package com.realestates.backend.search.infrastructure;

import com.realestates.backend.search.domain.KieuGia;

/**
 * Biểu thức SQL (PostgreSQL) tái tạo CHÍNH XÁC PriceConverter cho 7 đơn vị giá, gồm cả các bước ROUND(...,2)
 * theo đúng thứ tự. Phép nhân/cộng của numeric là chính xác; round(numeric, 2) làm tròn nửa ra xa 0 (= HALF_UP với số dương).
 * Phép chia a/b làm tròn 2 chữ số KHÔNG dùng toán tử "/" (numeric chia chỉ có ~16 chữ số rồi mới round, có thể làm tròn kép)
 * mà dùng floor((200a + b) / 2b) / 100 qua div() cắt cụt chính xác, tương đương BigDecimal.divide(b, 2, HALF_UP) với a, b > 0.
 * Mọi giá trị là hằng trong mã; alias bảng do mã gọi cung cấp (không bao giờ là dữ liệu người dùng).
 */
public final class BieuThucGiaSql {

    private BieuThucGiaSql() {
    }

    /** Tổng giá chuẩn VND (giaTongChuanVnd). */
    public static String giaTong(String p) {
        String g = p + ".gia_goc";
        String a = p + ".dien_tich_m2";
        return "round(CASE " + p + ".don_vi_gia"
                + " WHEN 'vnd_tong' THEN " + g
                + " WHEN 'vnd_m2' THEN round(" + g + " * " + a + ", 2)"
                + " WHEN 'vnd_thang' THEN " + g
                + " WHEN 'vnd_ngay' THEN round(" + g + " * 30, 2)"
                + " WHEN 'vnd_nam' THEN " + chia(g, "12")
                + " WHEN 'vnd_m2_ngay' THEN round(round(" + g + " * 30, 2) * " + a + ", 2)"
                + " WHEN 'vnd_m2_thang' THEN round(" + g + " * " + a + ", 2)"
                + " END, 2)";
    }

    /** Giá chuẩn trên m2 (giaM2ChuanVnd). */
    public static String giaM2(String p) {
        String g = p + ".gia_goc";
        String a = p + ".dien_tich_m2";
        return "round(CASE " + p + ".don_vi_gia"
                + " WHEN 'vnd_tong' THEN " + chia(g, a)
                + " WHEN 'vnd_m2' THEN " + g
                + " WHEN 'vnd_thang' THEN " + chia(g, a)
                + " WHEN 'vnd_ngay' THEN " + chia("round(" + g + " * 30, 2)", a)
                + " WHEN 'vnd_nam' THEN " + chia(g, "(12 * " + a + ")")
                + " WHEN 'vnd_m2_ngay' THEN round(" + g + " * 30, 2)"
                + " WHEN 'vnd_m2_thang' THEN " + g
                + " END, 2)";
    }

    public static String theoKieu(KieuGia kieuGia, String p) {
        return kieuGia == KieuGia.M2 ? giaM2(p) : giaTong(p);
    }

    private static String chia(String tu, String mau) {
        return "(div(200 * (" + tu + ") + (" + mau + "), 2 * (" + mau + ")) / 100)";
    }
}
