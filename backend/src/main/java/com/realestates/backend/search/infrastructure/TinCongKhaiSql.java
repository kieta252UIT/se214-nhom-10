package com.realestates.backend.search.infrastructure;

import com.realestates.backend.search.domain.TimKiemRepository.DongTin;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/**
 * Các đoạn SQL dùng chung cho mọi truy vấn "tin công khai" (tìm kiếm, chi tiết, yêu thích).
 * Quy tắc do chủ sản phẩm quyết định: tin công khai khi có phiên bản đã duyệt và chưa bị xóa/gỡ; luôn đọc nội dung
 * của phiên bản ĐÃ DUYỆT (p.id = t.phien_ban_da_duyet_id). Mọi giá trị là hằng; alias do mã gọi cung cấp.
 */
public final class TinCongKhaiSql {

    private TinCongKhaiSql() {
    }

    /** Điều kiện công khai trên bảng tin có alias t (không có WHERE). */
    public static String dieuKienCongKhai(String t) {
        return t + ".phien_ban_da_duyet_id IS NOT NULL\n  AND " + t + ".trang_thai NOT IN ('da_xoa', 'bi_go_vi_pham')";
    }

    /** FROM tin t JOIN phiên bản đã duyệt p. */
    public static String tuKhoaTin(String t, String p) {
        return "FROM se214.tin_bat_dong_san " + t + "\n"
                + "JOIN se214.phien_ban_tin " + p + " ON " + p + ".id = " + t + ".phien_ban_da_duyet_id AND "
                + p + ".tin_id = " + t + ".id\n";
    }

    /** Biểu thức boolean: tin có gói đẩy đang hoạt động và chưa hết hạn. */
    public static String coGoiTaiTro(String t) {
        return "EXISTS (SELECT 1 FROM se214.goi_day_tin g WHERE g.tin_id = " + t + ".id"
                + " AND g.trang_thai = 'dang_hoat_dong' AND g.het_han_luc > now())";
    }

    /** Cột tóm tắt của một tin (nội dung phiên bản đã duyệt) cho DongTin. */
    public static String cacCotTomTat(String t, String p) {
        return t + ".id AS tin_id, " + p + ".tieu_de, " + p + ".loai_giao_dich, " + p + ".loai_bds, "
                + p + ".khu_vuc, " + p + ".dia_chi,\n"
                + "       " + p + ".gia_goc, " + p + ".don_vi_gia, " + p + ".dien_tich_m2, " + t + ".ngay_dang,\n"
                + "       (SELECT a.khoa_anh_hien_thi FROM se214.anh_tin a\n"
                + "         WHERE a.phien_ban_tin_id = " + p + ".id AND a.trang_thai = 'da_gan'\n"
                + "         ORDER BY a.thu_tu ASC LIMIT 1) AS anh_dai_dien";
    }

    /** Ánh xạ cột của cacCotTomTat + cột khoa_sap_xep (có thể là NULL) sang DongTin. */
    public static final RowMapper<DongTin> ANH_XA_DONG_TIN = TinCongKhaiSql::anhXaDongTin;

    private static DongTin anhXaDongTin(ResultSet rs, int rowNum) throws SQLException {
        return new DongTin(
                rs.getObject("tin_id", UUID.class),
                rs.getString("tieu_de"),
                rs.getString("loai_giao_dich"),
                rs.getString("loai_bds"),
                rs.getString("khu_vuc"),
                rs.getString("dia_chi"),
                rs.getBigDecimal("gia_goc"),
                rs.getString("don_vi_gia"),
                rs.getBigDecimal("dien_tich_m2"),
                rs.getString("anh_dai_dien"),
                rs.getTimestamp("ngay_dang").toInstant(),
                rs.getString("khoa_sap_xep")
        );
    }
}
