package com.realestates.backend.detail.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Đọc chi tiết và thông tin liên hệ của tin công khai (luôn là nội dung phiên bản ĐÃ DUYỆT). */
public interface ChiTietTinRepository {

    record AnhChiTiet(int thuTu, String khoaAnhHienThi) {
    }

    /** daLuu = null khi nguoiXemId = null (không tính trạng thái lưu). */
    record DongChiTiet(
            UUID tinId,
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
            Instant ngayDang,
            boolean laTaiTro,
            Boolean daLuu,
            List<AnhChiTiet> anh
    ) {
    }

    record DongLienHe(String hoTen, String soDienThoai) {
    }

    /** Empty nếu tin không tồn tại hoặc không công khai. nguoiXemId chỉ truyền khi người xem là nguoi_dung. */
    Optional<DongChiTiet> docChiTiet(UUID tinId, UUID nguoiXemId);

    /** Empty nếu tin không công khai hoặc tài khoản chủ tin bị khóa. */
    Optional<DongLienHe> docLienHe(UUID tinId);
}
