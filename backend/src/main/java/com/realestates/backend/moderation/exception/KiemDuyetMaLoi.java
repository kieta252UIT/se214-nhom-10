package com.realestates.backend.moderation.exception;

/** Mã lỗi riêng của nghiệp vụ kiểm duyệt; các mã dùng chung (404, 412, 428, 409 ý định) lấy từ gói listing. */
public final class KiemDuyetMaLoi {

    public static final String TIN_DA_XOA = "TIN_DA_XOA";
    public static final String TIN_DA_BI_GO = "TIN_DA_BI_GO";
    public static final String PHIEN_BAN_DA_CU = "PHIEN_BAN_DA_CU";
    public static final String DA_CO_QUYET_DINH = "DA_CO_QUYET_DINH";

    private KiemDuyetMaLoi() {
    }
}
