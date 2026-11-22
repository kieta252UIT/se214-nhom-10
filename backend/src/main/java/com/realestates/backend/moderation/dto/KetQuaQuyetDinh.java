package com.realestates.backend.moderation.dto;

import java.time.Instant;
import java.util.UUID;

public record KetQuaQuyetDinh(
        UUID tinId,
        UUID phienBanId,
        String ketQuaKiemDuyet,
        String trangThaiTin,
        UUID nguoiDuyetId,
        Instant thoiDiemQuyetDinh,
        String lyDoTuChoi,
        long phienBanCapNhat
) {
}
