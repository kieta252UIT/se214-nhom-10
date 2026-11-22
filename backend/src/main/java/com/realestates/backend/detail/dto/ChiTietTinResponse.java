package com.realestates.backend.detail.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Chi tiết tin công khai. Không có chuId/email chủ tin. daLuu = null với khách và vai trò khác nguoi_dung. */
public record ChiTietTinResponse(
        UUID tinId,
        String tieuDe,
        String moTa,
        String loaiGiaoDich,
        String loaiBds,
        BigDecimal giaGoc,
        String donViGia,
        BigDecimal giaTongChuanVnd,
        BigDecimal giaM2ChuanVnd,
        BigDecimal dienTichM2,
        String diaChi,
        String khuVuc,
        BigDecimal viDo,
        BigDecimal kinhDo,
        Instant ngayDang,
        List<AnhChiTietItem> anh,
        Boolean daLuu,
        boolean laTaiTro
) {
}
