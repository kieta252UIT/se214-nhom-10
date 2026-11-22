package com.realestates.backend.search.dto;

import java.util.List;

/** Tham số query thô (chưa kiểm tra) của GET /api/listings. */
public record ThamSoTimKiem(
        String loaiGiaoDich,
        List<String> loaiBds,
        String khuVuc,
        String giaTu,
        String giaDen,
        String kieuGia,
        String dienTichTu,
        String dienTichDen,
        String sapXep,
        String cursor,
        String limit
) {
}
