package com.realestates.backend.moderation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NoiDungPhienBan(
        UUID id,
        int soPhienBan,
        String tieuDe,
        String moTa,
        String loaiGiaoDich,
        String loaiBds,
        BigDecimal giaGoc,
        String donViGia,
        BigDecimal dienTichM2,
        String diaChi,
        String khuVuc,
        BigDecimal viDo,
        BigDecimal kinhDo,
        String ketQuaSoBo,
        String ketQuaKiemDuyet,
        UUID nguoiDuyetId,
        Instant thoiDiemQuyetDinh,
        String lyDoTuChoi,
        Instant taoLuc,
        List<AnhPhienBan> anh
) {
}
