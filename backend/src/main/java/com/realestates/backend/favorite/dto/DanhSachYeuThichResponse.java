package com.realestates.backend.favorite.dto;

import java.util.List;

public record DanhSachYeuThichResponse(List<YeuThichItem> items, String conTroTiepTheo) {
}
