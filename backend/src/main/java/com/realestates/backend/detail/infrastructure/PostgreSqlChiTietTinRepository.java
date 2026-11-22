package com.realestates.backend.detail.infrastructure;

import com.realestates.backend.detail.domain.ChiTietTinRepository;
import com.realestates.backend.search.infrastructure.TinCongKhaiSql;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Chi tiết và liên hệ trên PostgreSQL. Dùng chung điều kiện công khai / phiên bản đã duyệt với tìm kiếm
 * (TinCongKhaiSql). Chi tiết là MỘT câu lệnh (LEFT JOIN ảnh) nên nội dung và ảnh luôn cùng một phiên bản.
 */
@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlChiTietTinRepository implements ChiTietTinRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PostgreSqlChiTietTinRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null"));
    }

    @Override
    public Optional<DongChiTiet> docChiTiet(UUID tinId, UUID nguoiXemId) {
        MapSqlParameterSource tham = new MapSqlParameterSource().addValue("tinId", tinId.toString());
        String daLuu;
        if (nguoiXemId == null) {
            daLuu = "CAST(NULL AS boolean)";
        } else {
            daLuu = "EXISTS (SELECT 1 FROM se214.yeu_thich_tin y WHERE y.tai_khoan_id = CAST(:nguoiXemId AS uuid)"
                    + " AND y.tin_id = t.id)";
            tham.addValue("nguoiXemId", nguoiXemId.toString());
        }
        String sql = "SELECT t.id AS tin_id, p.tieu_de, p.mo_ta, p.loai_giao_dich, p.loai_bds, p.gia_goc, p.don_vi_gia,\n"
                + "       p.dien_tich_m2, p.dia_chi, p.khu_vuc, p.vi_do, p.kinh_do, t.ngay_dang,\n"
                + "       " + TinCongKhaiSql.coGoiTaiTro("t") + " AS la_tai_tro,\n"
                + "       " + daLuu + " AS da_luu,\n"
                + "       a.thu_tu AS anh_thu_tu, a.khoa_anh_hien_thi AS anh_khoa\n"
                + TinCongKhaiSql.tuKhoaTin("t", "p")
                + "LEFT JOIN se214.anh_tin a ON a.phien_ban_tin_id = p.id AND a.trang_thai = 'da_gan'\n"
                + "WHERE t.id = CAST(:tinId AS uuid)\n  AND " + TinCongKhaiSql.dieuKienCongKhai("t")
                + "\nORDER BY a.thu_tu ASC";
        ResultSetExtractor<Optional<DongChiTiet>> gom = PostgreSqlChiTietTinRepository::gomDong;
        return jdbc.query(sql, tham, gom);
    }

    @Override
    public Optional<DongLienHe> docLienHe(UUID tinId) {
        String sql = "SELECT c.ho_ten, c.so_dien_thoai\n"
                + "FROM se214.tin_bat_dong_san t\n"
                + "JOIN se214.tai_khoan c ON c.id = t.chu_id\n"
                + "WHERE t.id = CAST(:tinId AS uuid)\n  AND " + TinCongKhaiSql.dieuKienCongKhai("t")
                + "\n  AND c.trang_thai <> 'bi_khoa'";
        List<DongLienHe> ketQua = jdbc.query(
                sql,
                new MapSqlParameterSource("tinId", tinId.toString()),
                (rs, rowNum) -> new DongLienHe(rs.getString("ho_ten"), rs.getString("so_dien_thoai"))
        );
        return ketQua.stream().findFirst();
    }

    private static Optional<DongChiTiet> gomDong(ResultSet rs) throws SQLException {
        DongChiTiet dong = null;
        List<AnhChiTiet> anh = new ArrayList<>();
        while (rs.next()) {
            if (dong == null) {
                Boolean daLuu = rs.getObject("da_luu", Boolean.class);
                BigDecimal viDo = rs.getBigDecimal("vi_do");
                BigDecimal kinhDo = rs.getBigDecimal("kinh_do");
                dong = new DongChiTiet(
                        rs.getObject("tin_id", UUID.class),
                        rs.getString("tieu_de"),
                        rs.getString("mo_ta"),
                        rs.getString("loai_giao_dich"),
                        rs.getString("loai_bds"),
                        rs.getBigDecimal("gia_goc"),
                        rs.getString("don_vi_gia"),
                        rs.getBigDecimal("dien_tich_m2"),
                        rs.getString("dia_chi"),
                        rs.getString("khu_vuc"),
                        viDo,
                        kinhDo,
                        rs.getTimestamp("ngay_dang").toInstant(),
                        rs.getBoolean("la_tai_tro"),
                        daLuu,
                        anh
                );
            }
            String khoa = rs.getString("anh_khoa");
            if (khoa != null) {
                anh.add(new AnhChiTiet(rs.getInt("anh_thu_tu"), khoa));
            }
        }
        return Optional.ofNullable(dong);
    }
}
