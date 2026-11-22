package com.realestates.backend.search.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TimKiemRepository {

    /** Một dòng kết quả (nội dung của phiên bản ĐÃ DUYỆT). khoaSapXep chỉ có ở danh sách tự nhiên. */
    record DongTin(
            UUID tinId,
            String tieuDe,
            String loaiGiaoDich,
            String loaiBds,
            String khuVuc,
            String diaChi,
            BigDecimal giaGoc,
            String donViGia,
            BigDecimal dienTichM2,
            String anhDaiDien,
            Instant ngayDang,
            String khoaSapXep
    ) {
    }

    /** Tối đa toiDa tin tài trợ khớp bộ lọc, cũ nhất trước (ngay_dang ASC, id ASC). */
    List<DongTin> docTaiTro(BoLocTimKiem boLoc, int toiDa);

    /**
     * Danh sách tự nhiên theo sapXep, keyset sau con trỏ (nếu có), đã loại đúng các tin nằm trong khối tài trợ
     * (tối đa toiDaTaiTro tin đầu theo cùng bộ lọc). Trả tối đa gioiHan dòng.
     */
    List<DongTin> docTuNhien(BoLocTimKiem boLoc, SapXepTin sapXep, ConTroTimKiem sau, int toiDaTaiTro, int gioiHan);
}
