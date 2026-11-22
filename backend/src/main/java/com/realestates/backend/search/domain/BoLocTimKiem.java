package com.realestates.backend.search.domain;

import com.realestates.backend.listing.domain.LoaiBds;
import com.realestates.backend.listing.domain.LoaiGiaoDich;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Bộ lọc đã chuẩn hóa và hợp lệ. loaiBds rỗng = không lọc; các trường null = không lọc.
 * kieuGia luôn khác null (mặc định TONG).
 */
public record BoLocTimKiem(
        LoaiGiaoDich loaiGiaoDich,
        List<LoaiBds> loaiBds,
        String khuVuc,
        KieuGia kieuGia,
        BigDecimal giaTu,
        BigDecimal giaDen,
        BigDecimal dienTichTu,
        BigDecimal dienTichDen
) {

    public BoLocTimKiem {
        Objects.requireNonNull(kieuGia, "kieuGia không được null");
        loaiBds = loaiBds == null
                ? List.of()
                : loaiBds.stream().distinct().sorted(Comparator.comparing(LoaiBds::getGiaTri)).toList();
    }

    public boolean coLocGia() {
        return giaTu != null || giaDen != null;
    }

    /** Dấu vân tay ổn định của bộ lọc (không gồm sapXep, limit, cursor): SHA-256 rút gọn 8 byte, dạng hex. */
    public String dauVanTay() {
        StringBuilder nguon = new StringBuilder();
        nguon.append(loaiGiaoDich == null ? "" : loaiGiaoDich.getGiaTri()).append('\u0001');
        nguon.append(String.join(",", loaiBds.stream().map(LoaiBds::getGiaTri).toList())).append('\u0001');
        nguon.append(khuVuc == null ? "" : khuVuc.trim().toLowerCase(Locale.ROOT)).append('\u0001');
        nguon.append(kieuGia.getGiaTri()).append('\u0001');
        nguon.append(soChuan(giaTu)).append('\u0001');
        nguon.append(soChuan(giaDen)).append('\u0001');
        nguon.append(soChuan(dienTichTu)).append('\u0001');
        nguon.append(soChuan(dienTichDen));
        return bamRutGon(nguon.toString());
    }

    private static String soChuan(BigDecimal so) {
        if (so == null) {
            return "";
        }
        BigDecimal rutGon = so.signum() == 0 ? BigDecimal.ZERO : so.stripTrailingZeros();
        return rutGon.toPlainString();
    }

    static String bamRutGon(String nguon) {
        try {
            byte[] bam = MessageDigest.getInstance("SHA-256").digest(nguon.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bam, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Thiếu SHA-256", ex);
        }
    }
}
