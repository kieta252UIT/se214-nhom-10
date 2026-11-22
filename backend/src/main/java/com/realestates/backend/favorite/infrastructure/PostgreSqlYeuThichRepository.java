package com.realestates.backend.favorite.infrastructure;

import com.realestates.backend.favorite.domain.ConTroYeuThich;
import com.realestates.backend.favorite.domain.YeuThichRepository;
import com.realestates.backend.search.domain.TimKiemRepository.DongTin;
import com.realestates.backend.search.infrastructure.TinCongKhaiSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Yêu thích trên PostgreSQL. Lưu = INSERT ... SELECT (chỉ khi tin công khai) ON CONFLICT DO NOTHING nên atomic và
 * giữ luu_luc ban đầu. Danh sách LEFT JOIN tin công khai (điều kiện công khai nằm trong ON) để tin đã ẩn vẫn hiện
 * với conHienThi = false. Thứ tự (luu_luc DESC, tin_id DESC) + keyset hàng so sánh, cùng tiền tố với ix_yeu_thich_danh_sach.
 */
@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlYeuThichRepository implements YeuThichRepository {

    private static final RowMapper<DongYeuThich> ROW_MAPPER = PostgreSqlYeuThichRepository::anhXa;

    private final NamedParameterJdbcTemplate jdbc;

    public PostgreSqlYeuThichRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null"));
    }

    @Override
    public boolean luu(UUID taiKhoanId, UUID tinId) {
        MapSqlParameterSource tham = new MapSqlParameterSource()
                .addValue("taiKhoanId", taiKhoanId.toString())
                .addValue("tinId", tinId.toString());
        int daChen = jdbc.update(
                "INSERT INTO se214.yeu_thich_tin (tai_khoan_id, tin_id)\n"
                        + "SELECT CAST(:taiKhoanId AS uuid), t.id FROM se214.tin_bat_dong_san t\n"
                        + "WHERE t.id = CAST(:tinId AS uuid) AND " + TinCongKhaiSql.dieuKienCongKhai("t") + "\n"
                        + "ON CONFLICT (tai_khoan_id, tin_id) DO NOTHING",
                tham
        );
        if (daChen > 0) {
            return true;
        }
        // 0 dòng: hoặc đã lưu từ trước (idempotent, kể cả khi tin đã ẩn) hoặc tin không công khai.
        Boolean daCo = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM se214.yeu_thich_tin"
                        + " WHERE tai_khoan_id = CAST(:taiKhoanId AS uuid) AND tin_id = CAST(:tinId AS uuid))",
                tham,
                Boolean.class
        );
        return Boolean.TRUE.equals(daCo);
    }

    @Override
    public void bo(UUID taiKhoanId, UUID tinId) {
        jdbc.update(
                "DELETE FROM se214.yeu_thich_tin"
                        + " WHERE tai_khoan_id = CAST(:taiKhoanId AS uuid) AND tin_id = CAST(:tinId AS uuid)",
                new MapSqlParameterSource()
                        .addValue("taiKhoanId", taiKhoanId.toString())
                        .addValue("tinId", tinId.toString())
        );
    }

    @Override
    public List<DongYeuThich> docDanhSach(UUID taiKhoanId, ConTroYeuThich sau, int gioiHan) {
        MapSqlParameterSource tham = new MapSqlParameterSource()
                .addValue("taiKhoanId", taiKhoanId.toString())
                .addValue("gioiHan", gioiHan);
        StringBuilder sql = new StringBuilder("SELECT y.tin_id AS yeu_thich_tin_id, y.luu_luc,\n")
                .append("       CAST(y.luu_luc AS text) AS luu_luc_text,\n")
                .append("       (t.id IS NOT NULL) AS con_hien_thi,\n")
                .append("       ").append(TinCongKhaiSql.coGoiTaiTro("t")).append(" AS la_tai_tro,\n")
                .append("       CAST(NULL AS text) AS khoa_sap_xep,\n")
                .append("       ").append(TinCongKhaiSql.cacCotTomTat("t", "p")).append('\n')
                .append("FROM se214.yeu_thich_tin y\n")
                .append("LEFT JOIN (se214.tin_bat_dong_san t\n")
                .append("           JOIN se214.phien_ban_tin p ON p.id = t.phien_ban_da_duyet_id AND p.tin_id = t.id\n")
                .append("             AND ").append(TinCongKhaiSql.dieuKienCongKhai("t")).append(")\n")
                .append("       ON t.id = y.tin_id\n")
                .append("WHERE y.tai_khoan_id = CAST(:taiKhoanId AS uuid)");
        if (sau != null) {
            sql.append("\n  AND (y.luu_luc, y.tin_id) < (CAST(:luuLucSau AS timestamptz), CAST(:tinIdSau AS uuid))");
            tham.addValue("luuLucSau", sau.luuLuc());
            tham.addValue("tinIdSau", sau.tinId().toString());
        }
        sql.append("\nORDER BY y.luu_luc DESC, y.tin_id DESC\nLIMIT :gioiHan");
        return jdbc.query(sql.toString(), tham, ROW_MAPPER);
    }

    private static DongYeuThich anhXa(ResultSet rs, int rowNum) throws SQLException {
        boolean conHienThi = rs.getBoolean("con_hien_thi");
        DongTin tin = conHienThi ? TinCongKhaiSql.ANH_XA_DONG_TIN.mapRow(rs, rowNum) : null;
        return new DongYeuThich(
                rs.getObject("yeu_thich_tin_id", UUID.class),
                rs.getTimestamp("luu_luc").toInstant(),
                rs.getString("luu_luc_text"),
                conHienThi,
                tin,
                rs.getBoolean("la_tai_tro")
        );
    }
}
