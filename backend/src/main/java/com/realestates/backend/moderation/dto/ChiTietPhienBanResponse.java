package com.realestates.backend.moderation.dto;

import java.util.UUID;

public record ChiTietPhienBanResponse(
        UUID tinId,
        UUID chuId,
        String trangThaiTin,
        long phienBanCapNhat,
        UUID phienBanHienTaiId,
        UUID phienBanDaDuyetId,
        boolean laBanSua,
        NoiDungPhienBan phienBan,
        NoiDungPhienBan phienBanDaDuyet
) {
}
