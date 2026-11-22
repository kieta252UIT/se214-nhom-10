package com.realestates.backend.detail.dto;

/** Chỉ họ tên và số điện thoại (có thể null); không bao giờ có email. */
public record LienHeChuTinResponse(String hoTen, String soDienThoai) {
}
