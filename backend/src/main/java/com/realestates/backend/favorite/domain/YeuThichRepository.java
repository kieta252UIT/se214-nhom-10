package com.realestates.backend.favorite.domain;

import com.realestates.backend.search.domain.TimKiemRepository.DongTin;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface YeuThichRepository {

    /**
     * Một mục yêu thích. tin = null và conHienThi = false khi tin không còn công khai (đã xóa, bị gỡ,
     * chưa/không có phiên bản đã duyệt). luuLucVanBan là giá trị luu_luc dạng text để dựng con trỏ.
     */
    record DongYeuThich(
            UUID tinId,
            Instant luuLuc,
            String luuLucVanBan,
            boolean conHienThi,
            DongTin tin,
            boolean laTaiTro
    ) {
    }

    /**
     * Lưu yêu thích, idempotent (giữ luu_luc ban đầu). Trả true nếu sau lệnh tin đã nằm trong danh sách yêu thích
     * (vừa chèn hoặc đã có từ trước); false nếu chưa lưu và tin không công khai/không tồn tại.
     */
    boolean luu(UUID taiKhoanId, UUID tinId);

    /** Bỏ yêu thích, idempotent: không có gì để xóa thì không làm gì. */
    void bo(UUID taiKhoanId, UUID tinId);

    /** Mới lưu trước (luu_luc DESC, tin_id DESC), keyset sau con trỏ, tối đa gioiHan dòng. */
    List<DongYeuThich> docDanhSach(UUID taiKhoanId, ConTroYeuThich sau, int gioiHan);
}
