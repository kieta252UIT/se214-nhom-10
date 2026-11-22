package com.realestates.backend.favorite.dto;

import com.realestates.backend.search.dto.TinTimKiemItem;
import java.time.Instant;
import java.util.UUID;

/** Một mục yêu thích. tin = null khi conHienThi = false (tin đã xóa/gỡ/không còn công khai). */
public record YeuThichItem(UUID tinId, Instant luuLuc, boolean conHienThi, TinTimKiemItem tin) {
}
