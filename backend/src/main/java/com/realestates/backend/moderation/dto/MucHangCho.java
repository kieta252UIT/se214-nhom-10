package com.realestates.backend.moderation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MucHangCho(
        UUID tinId,
        UUID phienBanId,
        int soPhienBan,
        boolean laBanSua,
        String tieuDe,
        String loaiGiaoDich,
        String loaiBds,
        String khuVuc,
        BigDecimal giaGoc,
        String donViGia,
        BigDecimal dienTichM2,
        String ketQuaSoBo,
        Instant guiLuc,
        UUID chuId
) {
}
