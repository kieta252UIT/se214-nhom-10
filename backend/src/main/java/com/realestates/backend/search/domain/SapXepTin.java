package com.realestates.backend.search.domain;

import java.util.Optional;

/** Cách sắp xếp kết quả tìm kiếm; chỉ nhận giá trị trong enum để không bao giờ nối chuỗi người dùng vào SQL. */
public enum SapXepTin {
    MOI_NHAT("moi_nhat", false, false),
    CU_NHAT("cu_nhat", true, false),
    GIA_TANG("gia_tang", true, true),
    GIA_GIAM("gia_giam", false, true),
    DIEN_TICH_TANG("dien_tich_tang", true, false),
    DIEN_TICH_GIAM("dien_tich_giam", false, false);

    private final String giaTri;
    private final boolean tang;
    private final boolean theoGia;

    SapXepTin(String giaTri, boolean tang, boolean theoGia) {
        this.giaTri = giaTri;
        this.tang = tang;
        this.theoGia = theoGia;
    }

    public String getGiaTri() {
        return giaTri;
    }

    /** true nếu khóa chính tăng dần (khóa phụ t.id đi cùng chiều). */
    public boolean laTangDan() {
        return tang;
    }

    public boolean laTheoGia() {
        return theoGia;
    }

    public boolean laTheoThoiGian() {
        return this == MOI_NHAT || this == CU_NHAT;
    }

    public static Optional<SapXepTin> tuGiaTri(String giaTri) {
        for (SapXepTin sapXep : values()) {
            if (sapXep.giaTri.equals(giaTri)) {
                return Optional.of(sapXep);
            }
        }
        return Optional.empty();
    }
}
