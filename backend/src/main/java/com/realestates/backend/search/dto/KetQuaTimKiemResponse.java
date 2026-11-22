package com.realestates.backend.search.dto;

import java.util.List;

public record KetQuaTimKiemResponse(
        List<TinTimKiemItem> taiTro,
        List<TinTimKiemItem> tuNhien,
        String conTroTiepTheo
) {
}
