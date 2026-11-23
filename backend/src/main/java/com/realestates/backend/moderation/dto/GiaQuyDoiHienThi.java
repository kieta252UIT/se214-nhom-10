package com.realestates.backend.moderation.dto;

import com.realestates.backend.domain.model.PriceConverter;
import java.math.BigDecimal;

/** Schema GiaQuyDoiHienThi; đường đọc quản lý/KDV mặc định chế độ tong. */
public record GiaQuyDoiHienThi(String giaTri, String donVi, String chuThich) {

    public static final String CHU_THICH = "Giá quy đổi tham khảo; không dùng để lọc.";

    /** Trả null nếu dữ liệu không quy đổi được (không xảy ra với dữ liệu thỏa CHECK của V1). */
    public static GiaQuyDoiHienThi tinhCheDoTong(
            String loaiGiaoDich,
            BigDecimal giaGoc,
            String donViGia,
            BigDecimal dienTichM2
    ) {
        PriceConverter.DonViGia donVi = switch (donViGia == null ? "" : donViGia) {
            case "vnd_tong" -> PriceConverter.DonViGia.TONG_VND;
            case "vnd_m2" -> PriceConverter.DonViGia.VND_M2;
            case "vnd_ngay" -> PriceConverter.DonViGia.VND_NGAY;
            case "vnd_thang" -> PriceConverter.DonViGia.VND_THANG;
            case "vnd_nam" -> PriceConverter.DonViGia.VND_NAM;
            case "vnd_m2_ngay" -> PriceConverter.DonViGia.VND_M2_NGAY;
            case "vnd_m2_thang" -> PriceConverter.DonViGia.VND_M2_THANG;
            default -> null;
        };
        if (donVi == null) {
            return null;
        }
        try {
            PriceConverter.KetQuaQuyDoiGia ketQua = PriceConverter.quyDoi(giaGoc, donVi, dienTichM2);
            String donViHienThi = "ban".equals(loaiGiaoDich) ? "vnd_tong" : "vnd_thang";
            return new GiaQuyDoiHienThi(ketQua.giaTongChuanVnd().toPlainString(), donViHienThi, CHU_THICH);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
