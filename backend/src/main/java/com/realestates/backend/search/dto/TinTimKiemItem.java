package com.realestates.backend.search.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TinTimKiemItem(
        UUID tinId,
        String tieuDe,
        String loaiGiaoDich,
        String loaiBds,
        String khuVuc,
        String diaChi,
        BigDecimal giaGoc,
        String donViGia,
        BigDecimal giaTongChuanVnd,
        BigDecimal giaM2ChuanVnd,
        BigDecimal dienTichM2,
        String anhDaiDien,
        Instant ngayDang,
        boolean laTaiTro
) {
}
