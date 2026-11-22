package com.realestates.backend.moderation.domain;

import com.realestates.backend.moderation.dto.ChiTietPhienBanResponse;
import com.realestates.backend.moderation.dto.KetQuaQuyetDinh;
import com.realestates.backend.moderation.dto.MucHangCho;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KiemDuyetRepository {

    String LOAI_THAO_TAC_QUYET_DINH = "quyet_dinh";
    String KET_QUA_DA_DUYET = "da_duyet";
    String KET_QUA_BI_TU_CHOI = "bi_tu_choi";

    record KetQuaYeuCauQuyetDinh(String duLieuBam, KetQuaQuyetDinh ketQua) {
    }

    record DuLieuQuyetDinh(
            UUID tinId,
            UUID phienBanId,
            UUID nguoiDuyetId,
            /** {@link #KET_QUA_DA_DUYET} hoặc {@link #KET_QUA_BI_TU_CHOI}. */
            String ketQua,
            /** Chỉ dùng khi từ chối; đã trim. */
            String lyDo,
            long phienBanCapNhatDaXem,
            Instant thoiDiem,
            String maYDinh,
            String duLieuBam
    ) {
    }

    enum LoaiLoiKiemDuyet {
        TIN_KHONG_TON_TAI,
        PHIEN_BAN_KHONG_THUOC_TIN,
        TIN_DA_XOA,
        TIN_DA_BI_GO,
        PHIEN_BAN_CU,
        PHIEN_BAN_DA_CU,
        DA_CO_QUYET_DINH,
        XUNG_DOT_MA_Y_DINH
    }

    final class LoiNghiepVuKiemDuyetException extends RuntimeException {

        private final LoaiLoiKiemDuyet loaiLoi;

        public LoiNghiepVuKiemDuyetException(LoaiLoiKiemDuyet loaiLoi, String message) {
            super(message);
            this.loaiLoi = loaiLoi;
        }

        public LoaiLoiKiemDuyet getLoaiLoi() {
            return loaiLoi;
        }
    }

    /** Đọc tối đa {@code gioiHan} mục, bắt đầu sau {@code sau} (nếu có), cũ nhất trước. */
    List<MucHangCho> docHangCho(ConTroHangCho sau, int gioiHan);

    Optional<ChiTietPhienBanResponse> docChiTietPhienBan(UUID tinId, UUID phienBanId);

    Optional<KetQuaYeuCauQuyetDinh> timYeuCauQuyetDinh(UUID taiKhoanId, String maYDinh);

    /**
     * Phải được gọi trong giao dịch ghi đã mở (khóa dòng tin, kiểm tra điều kiện, ghi phiên bản, tin, gói, nhật ký,
     * yêu cầu ghi) để mọi thay đổi cùng commit hoặc cùng rollback.
     */
    KetQuaQuyetDinh quyetDinhTrongTransaction(DuLieuQuyetDinh duLieu);
}
