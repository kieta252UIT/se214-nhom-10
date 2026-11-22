package com.realestates.backend.search.infrastructure;

import com.realestates.backend.listing.domain.LoaiBds;
import com.realestates.backend.search.domain.BoLocTimKiem;
import com.realestates.backend.search.domain.ConTroTimKiem;
import com.realestates.backend.search.domain.SapXepTin;
import com.realestates.backend.search.domain.TimKiemRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tìm kiếm công khai trên PostgreSQL. Luôn hiển thị và lọc theo phiên bản ĐÃ DUYỆT (p.id = t.phien_ban_da_duyet_id).
 * Mọi giá trị người dùng đi qua tham số có tên; sắp xếp/kiểu giá chỉ chọn từ enum nên các đoạn SQL nối chuỗi đều là hằng.
 */
@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlTimKiemRepository implements TimKiemRepository {

    private static final String CAC_COT = TinCongKhaiSql.cacCotTomTat("t", "p");

    private static final RowMapper<DongTin> ROW_MAPPER = TinCongKhaiSql.ANH_XA_DONG_TIN;

    private final NamedParameterJdbcTemplate jdbc;

    public PostgreSqlTimKiemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null"));
    }

    @Override
    public List<DongTin> docTaiTro(BoLocTimKiem boLoc, int toiDa) {
        MapSqlParameterSource tham = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(CAC_COT)
                .append(", CAST(NULL AS text) AS khoa_sap_xep\n");
        sql.append(tuKhoaTin("t", "p"));
        themDieuKienTaiTro(sql, tham, boLoc, "t", "p");
        sql.append("\nORDER BY t.ngay_dang ASC, t.id ASC\nLIMIT :gioiHan");
        tham.addValue("gioiHan", toiDa);
        return jdbc.query(sql.toString(), tham, ROW_MAPPER);
    }

    @Override
    public List<DongTin> docTuNhien(
            BoLocTimKiem boLoc,
            SapXepTin sapXep,
            ConTroTimKiem sau,
            int toiDaTaiTro,
            int gioiHan
    ) {
        MapSqlParameterSource tham = new MapSqlParameterSource();
        String khoa = bieuThucKhoa(sapXep, boLoc, "p", "t");
        String huong = sapXep.laTangDan() ? "ASC" : "DESC";

        StringBuilder sql = new StringBuilder("SELECT ")
                .append(CAC_COT)
                .append(", CAST(").append(khoa).append(" AS text) AS khoa_sap_xep\n");
        sql.append(tuKhoaTin("t", "p"));
        themDieuKienLoc(sql, tham, boLoc, "t", "p");
        if (sau != null) {
            String kieuKhoa = sapXep.laTheoThoiGian() ? "timestamptz" : "numeric";
            String soSanh = sapXep.laTangDan() ? ">" : "<";
            sql.append("\n  AND (").append(khoa).append(", t.id) ").append(soSanh)
                    .append(" (CAST(:khoaSau AS ").append(kieuKhoa).append("), CAST(:idSau AS uuid))");
            tham.addValue("khoaSau", sau.khoa());
            tham.addValue("idSau", sau.tinId().toString());
        }
        // Loại đúng các tin đã được chọn vào khối tài trợ (cùng bộ lọc, cùng thứ tự, LIMIT như nhau) trên MỌI trang.
        sql.append("\n  AND t.id NOT IN (SELECT s.id FROM (\n")
                .append(truyVanIdTaiTro(boLoc, tham, "ts", "ps"))
                .append("\n) s)");
        sql.append("\nORDER BY ").append(khoa).append(' ').append(huong)
                .append(", t.id ").append(huong)
                .append("\nLIMIT :gioiHan");
        tham.addValue("soTaiTro", toiDaTaiTro);
        tham.addValue("gioiHan", gioiHan);
        return jdbc.query(sql.toString(), tham, ROW_MAPPER);
    }

    private static String truyVanIdTaiTro(BoLocTimKiem boLoc, MapSqlParameterSource tham, String t, String p) {
        StringBuilder sql = new StringBuilder("SELECT ").append(t).append(".id AS id\n");
        sql.append(tuKhoaTin(t, p));
        themDieuKienTaiTro(sql, tham, boLoc, t, p);
        sql.append("\nORDER BY ").append(t).append(".ngay_dang ASC, ").append(t).append(".id ASC\nLIMIT :soTaiTro");
        return sql.toString();
    }

    private static String tuKhoaTin(String t, String p) {
        return TinCongKhaiSql.tuKhoaTin(t, p);
    }

    /** Hiển thị công khai + bộ lọc; bắt đầu bằng WHERE, các điều kiện sau nối bằng AND. */
    private static void themDieuKienLoc(
            StringBuilder sql,
            MapSqlParameterSource tham,
            BoLocTimKiem boLoc,
            String t,
            String p
    ) {
        sql.append("WHERE ").append(TinCongKhaiSql.dieuKienCongKhai(t));
        if (boLoc.loaiGiaoDich() != null) {
            sql.append("\n  AND ").append(p).append(".loai_giao_dich = :loaiGiaoDich");
            tham.addValue("loaiGiaoDich", boLoc.loaiGiaoDich().getGiaTri());
        }
        if (!boLoc.loaiBds().isEmpty()) {
            sql.append("\n  AND ").append(p).append(".loai_bds IN (:loaiBds)");
            tham.addValue("loaiBds", boLoc.loaiBds().stream().map(LoaiBds::getGiaTri).toList());
        }
        if (boLoc.khuVuc() != null) {
            sql.append("\n  AND lower(btrim(").append(p).append(".khu_vuc)) = lower(btrim(CAST(:khuVuc AS text)))");
            tham.addValue("khuVuc", boLoc.khuVuc());
        }
        if (boLoc.giaTu() != null || boLoc.giaDen() != null) {
            String gia = BieuThucGiaSql.theoKieu(boLoc.kieuGia(), p);
            if (boLoc.giaTu() != null) {
                sql.append("\n  AND ").append(gia).append(" >= :giaTu");
                tham.addValue("giaTu", boLoc.giaTu());
            }
            if (boLoc.giaDen() != null) {
                sql.append("\n  AND ").append(gia).append(" <= :giaDen");
                tham.addValue("giaDen", boLoc.giaDen());
            }
        }
        if (boLoc.dienTichTu() != null) {
            sql.append("\n  AND ").append(p).append(".dien_tich_m2 >= :dienTichTu");
            tham.addValue("dienTichTu", boLoc.dienTichTu());
        }
        if (boLoc.dienTichDen() != null) {
            sql.append("\n  AND ").append(p).append(".dien_tich_m2 <= :dienTichDen");
            tham.addValue("dienTichDen", boLoc.dienTichDen());
        }
    }

    /** Điều kiện lọc + gói đẩy tin đang hoạt động và chưa hết hạn. */
    private static void themDieuKienTaiTro(
            StringBuilder sql,
            MapSqlParameterSource tham,
            BoLocTimKiem boLoc,
            String t,
            String p
    ) {
        themDieuKienLoc(sql, tham, boLoc, t, p);
        sql.append("\n  AND ").append(TinCongKhaiSql.coGoiTaiTro(t));
    }

    private static String bieuThucKhoa(SapXepTin sapXep, BoLocTimKiem boLoc, String p, String t) {
        if (sapXep.laTheoThoiGian()) {
            return t + ".ngay_dang";
        }
        if (sapXep.laTheoGia()) {
            return BieuThucGiaSql.theoKieu(boLoc.kieuGia(), p);
        }
        return p + ".dien_tich_m2";
    }
}
