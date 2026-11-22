package com.realestates.backend.search.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.domain.model.PriceConverter;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.DonViGiaTin;
import com.realestates.backend.listing.domain.LoaiBds;
import com.realestates.backend.listing.domain.LoaiGiaoDich;
import com.realestates.backend.search.domain.BoLocTimKiem;
import com.realestates.backend.search.domain.ConTroTimKiem;
import com.realestates.backend.search.domain.KieuGia;
import com.realestates.backend.search.domain.SapXepTin;
import com.realestates.backend.search.domain.TimKiemRepository;
import com.realestates.backend.search.domain.TimKiemRepository.DongTin;
import com.realestates.backend.search.dto.KetQuaTimKiemResponse;
import com.realestates.backend.search.dto.ThamSoTimKiem;
import com.realestates.backend.search.dto.TinTimKiemItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class TimKiemTinService {

    public static final int GIOI_HAN_MAC_DINH = 20;
    public static final int GIOI_HAN_TOI_DA = 50;
    public static final int SO_TAI_TRO_TOI_DA = 5;
    public static final int DO_DAI_KHU_VUC_TOI_DA = 200;

    public static final String TRUONG_LOAI_GIAO_DICH = "loaiGiaoDich";
    public static final String TRUONG_LOAI_BDS = "loaiBds";
    public static final String TRUONG_KHU_VUC = "khuVuc";
    public static final String TRUONG_GIA_TU = "giaTu";
    public static final String TRUONG_GIA_DEN = "giaDen";
    public static final String TRUONG_KIEU_GIA = "kieuGia";
    public static final String TRUONG_DIEN_TICH_TU = "dienTichTu";
    public static final String TRUONG_DIEN_TICH_DEN = "dienTichDen";
    public static final String TRUONG_SAP_XEP = "sapXep";
    public static final String TRUONG_CURSOR = "cursor";
    public static final String TRUONG_LIMIT = "limit";

    private static final String MA_LOI_DU_LIEU_KHONG_HOP_LE = TinDangService.MA_LOI_DU_LIEU_KHONG_HOP_LE;
    private static final String THONG_DIEP_KHONG_HOP_LE = "Dữ liệu gửi lên không hợp lệ.";
    private static final String THONG_DIEP_GIAN_DOAN = "Hệ thống tìm kiếm đang tạm gián đoạn. Vui lòng thử lại sau.";
    private static final Pattern SO_KHONG_AM = Pattern.compile("^[0-9]{1,18}(\\.[0-9]{1,6})?$");
    private static final Pattern SO_NGUYEN_DUONG = Pattern.compile("^[0-9]{1,9}$");

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final TimKiemRepository timKiemRepository;

    public TimKiemTinService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            TimKiemRepository timKiemRepository
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(permissionAndCsrfService, "permissionAndCsrfService không được null");
        this.timKiemRepository = Objects.requireNonNull(timKiemRepository, "timKiemRepository không được null");
    }

    public KetQuaTimKiemResponse timKiem(String rawSessionCookie, ThamSoTimKiem tham) {
        // Công khai: khách được phép; cookie hỏng/thu hồi được coi là khách.
        permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie,
                ThaoTacNghiepVu.TIM_LOC_SAP_XEP_TIN_CONG_KHAI,
                null,
                null
        );
        ThamSoTimKiem t = Objects.requireNonNull(tham, "tham không được null");
        List<FieldErrorItem> loi = new ArrayList<>();

        LoaiGiaoDich loaiGiaoDich = docLoaiGiaoDich(t.loaiGiaoDich(), loi);
        List<LoaiBds> loaiBds = docLoaiBds(t.loaiBds(), loi);
        String khuVuc = docKhuVuc(t.khuVuc(), loi);
        KieuGia kieuGia = docKieuGia(t.kieuGia(), loi);
        BigDecimal giaTu = docSo(t.giaTu(), TRUONG_GIA_TU, loi);
        BigDecimal giaDen = docSo(t.giaDen(), TRUONG_GIA_DEN, loi);
        BigDecimal dienTichTu = docSo(t.dienTichTu(), TRUONG_DIEN_TICH_TU, loi);
        BigDecimal dienTichDen = docSo(t.dienTichDen(), TRUONG_DIEN_TICH_DEN, loi);
        SapXepTin sapXep = docSapXep(t.sapXep(), loi);
        int gioiHan = docGioiHan(t.limit(), loi);

        kiemTraKhoang(giaTu, giaDen, TRUONG_GIA_TU, "giaTu không được lớn hơn giaDen.", loi);
        kiemTraKhoang(dienTichTu, dienTichDen, TRUONG_DIEN_TICH_TU, "dienTichTu không được lớn hơn dienTichDen.", loi);

        boolean coLocGia = giaTu != null || giaDen != null;
        if (loaiGiaoDich == null && (coLocGia || sapXep.laTheoGia()) && khongCoLoiTruong(loi, TRUONG_LOAI_GIAO_DICH)) {
            loi.add(loiTruong(
                    TRUONG_LOAI_GIAO_DICH,
                    "Lọc hoặc sắp xếp theo giá bắt buộc có loaiGiaoDich vì giá bán và giá thuê không so sánh được."
            ));
        }
        if (!loi.isEmpty()) {
            throw new AuthValidationException(MA_LOI_DU_LIEU_KHONG_HOP_LE, THONG_DIEP_KHONG_HOP_LE, loi);
        }

        BoLocTimKiem boLoc = new BoLocTimKiem(
                loaiGiaoDich, loaiBds, khuVuc, kieuGia, giaTu, giaDen, dienTichTu, dienTichDen
        );
        ConTroTimKiem conTro = docConTro(t.cursor(), sapXep, boLoc);

        try {
            List<TinTimKiemItem> taiTro = List.of();
            if (conTro == null) {
                taiTro = timKiemRepository.docTaiTro(boLoc, SO_TAI_TRO_TOI_DA).stream()
                        .map(dong -> chuyenThanhItem(dong, true))
                        .toList();
            }
            List<DongTin> docDuoc = timKiemRepository.docTuNhien(
                    boLoc, sapXep, conTro, SO_TAI_TRO_TOI_DA, gioiHan + 1
            );
            boolean conTiep = docDuoc.size() > gioiHan;
            List<DongTin> trang = conTiep ? docDuoc.subList(0, gioiHan) : docDuoc;
            String conTroTiepTheo = null;
            if (conTiep) {
                DongTin cuoi = trang.get(trang.size() - 1);
                conTroTiepTheo = new ConTroTimKiem(sapXep, cuoi.khoaSapXep(), cuoi.tinId(), boLoc.dauVanTay()).maHoa();
            }
            List<TinTimKiemItem> tuNhien = trang.stream()
                    .map(dong -> chuyenThanhItem(dong, false))
                    .toList();
            return new KetQuaTimKiemResponse(taiTro, tuNhien, conTroTiepTheo);
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
    }

    public static TinTimKiemItem chuyenThanhItem(DongTin dong, boolean laTaiTro) {
        DonViGiaTin donViGia = DonViGiaTin.tuGiaTri(dong.donViGia())
                .orElseThrow(() -> new IllegalStateException("Đơn vị giá không hợp lệ: " + dong.donViGia()));
        PriceConverter.KetQuaQuyDoiGia quyDoi = PriceConverter.quyDoi(
                dong.giaGoc(),
                chuyenDonVi(donViGia),
                dong.dienTichM2()
        );
        return new TinTimKiemItem(
                dong.tinId(),
                dong.tieuDe(),
                dong.loaiGiaoDich(),
                dong.loaiBds(),
                dong.khuVuc(),
                dong.diaChi(),
                dong.giaGoc(),
                dong.donViGia(),
                quyDoi.giaTongChuanVnd(),
                quyDoi.giaM2ChuanVnd(),
                dong.dienTichM2(),
                dong.anhDaiDien(),
                dong.ngayDang(),
                laTaiTro
        );
    }

    public static PriceConverter.DonViGia chuyenDonVi(DonViGiaTin donVi) {
        return switch (donVi) {
            case VND_TONG -> PriceConverter.DonViGia.TONG_VND;
            case VND_M2 -> PriceConverter.DonViGia.VND_M2;
            case VND_NGAY -> PriceConverter.DonViGia.VND_NGAY;
            case VND_THANG -> PriceConverter.DonViGia.VND_THANG;
            case VND_NAM -> PriceConverter.DonViGia.VND_NAM;
            case VND_M2_NGAY -> PriceConverter.DonViGia.VND_M2_NGAY;
            case VND_M2_THANG -> PriceConverter.DonViGia.VND_M2_THANG;
        };
    }

    // ------------------------------------------------------------------ đọc và kiểm tra tham số

    private static String vanBanHoacNull(String giaTri) {
        if (giaTri == null) {
            return null;
        }
        String cat = giaTri.strip();
        return cat.isEmpty() ? null : cat;
    }

    private static LoaiGiaoDich docLoaiGiaoDich(String tho, List<FieldErrorItem> loi) {
        String giaTri = vanBanHoacNull(tho);
        if (giaTri == null) {
            return null;
        }
        Optional<LoaiGiaoDich> loai = LoaiGiaoDich.tuGiaTri(giaTri);
        if (loai.isEmpty()) {
            loi.add(loiTruong(TRUONG_LOAI_GIAO_DICH, "loaiGiaoDich chỉ nhận: ban, cho_thue."));
            return null;
        }
        return loai.get();
    }

    private static List<LoaiBds> docLoaiBds(List<String> tho, List<FieldErrorItem> loi) {
        List<LoaiBds> ketQua = new ArrayList<>();
        if (tho == null) {
            return ketQua;
        }
        boolean coLoi = false;
        for (String phanTu : tho) {
            if (phanTu == null) {
                continue;
            }
            for (String manh : phanTu.split(",", -1)) {
                String giaTri = vanBanHoacNull(manh);
                if (giaTri == null) {
                    continue;
                }
                Optional<LoaiBds> loai = LoaiBds.tuGiaTri(giaTri);
                if (loai.isEmpty()) {
                    coLoi = true;
                } else {
                    ketQua.add(loai.get());
                }
            }
        }
        if (coLoi) {
            loi.add(loiTruong(TRUONG_LOAI_BDS, "loaiBds chỉ nhận: can_ho, nha_rieng, dat, phong_tro, khac."));
        }
        return ketQua;
    }

    private static String docKhuVuc(String tho, List<FieldErrorItem> loi) {
        String giaTri = vanBanHoacNull(tho);
        if (giaTri != null && giaTri.length() > DO_DAI_KHU_VUC_TOI_DA) {
            loi.add(loiTruong(TRUONG_KHU_VUC, "khuVuc không được dài quá " + DO_DAI_KHU_VUC_TOI_DA + " ký tự."));
            return null;
        }
        return giaTri;
    }

    private static KieuGia docKieuGia(String tho, List<FieldErrorItem> loi) {
        String giaTri = vanBanHoacNull(tho);
        if (giaTri == null) {
            return KieuGia.TONG;
        }
        Optional<KieuGia> kieu = KieuGia.tuGiaTri(giaTri);
        if (kieu.isEmpty()) {
            loi.add(loiTruong(TRUONG_KIEU_GIA, "kieuGia chỉ nhận: tong, m2."));
            return KieuGia.TONG;
        }
        return kieu.get();
    }

    private static SapXepTin docSapXep(String tho, List<FieldErrorItem> loi) {
        String giaTri = vanBanHoacNull(tho);
        if (giaTri == null) {
            return SapXepTin.MOI_NHAT;
        }
        Optional<SapXepTin> sapXep = SapXepTin.tuGiaTri(giaTri);
        if (sapXep.isEmpty()) {
            loi.add(loiTruong(
                    TRUONG_SAP_XEP,
                    "sapXep chỉ nhận: moi_nhat, cu_nhat, gia_tang, gia_giam, dien_tich_tang, dien_tich_giam."
            ));
            return SapXepTin.MOI_NHAT;
        }
        return sapXep.get();
    }

    private static BigDecimal docSo(String tho, String truong, List<FieldErrorItem> loi) {
        String giaTri = vanBanHoacNull(tho);
        if (giaTri == null) {
            return null;
        }
        if (!SO_KHONG_AM.matcher(giaTri).matches()) {
            loi.add(loiTruong(
                    truong,
                    truong + " phải là số thập phân không âm, dùng dấu chấm, không số mũ (tối đa 18 chữ số nguyên, 6 chữ số lẻ)."
            ));
            return null;
        }
        return new BigDecimal(giaTri);
    }

    private static int docGioiHan(String tho, List<FieldErrorItem> loi) {
        String giaTri = vanBanHoacNull(tho);
        if (giaTri == null) {
            return GIOI_HAN_MAC_DINH;
        }
        if (!SO_NGUYEN_DUONG.matcher(giaTri).matches()) {
            loi.add(loiTruong(TRUONG_LIMIT, "limit phải là số nguyên từ 1 đến " + GIOI_HAN_TOI_DA + "."));
            return GIOI_HAN_MAC_DINH;
        }
        int gioiHan = Integer.parseInt(giaTri);
        if (gioiHan < 1 || gioiHan > GIOI_HAN_TOI_DA) {
            loi.add(loiTruong(TRUONG_LIMIT, "limit phải là số nguyên từ 1 đến " + GIOI_HAN_TOI_DA + "."));
            return GIOI_HAN_MAC_DINH;
        }
        return gioiHan;
    }

    private static void kiemTraKhoang(
            BigDecimal tu,
            BigDecimal den,
            String truong,
            String thongDiep,
            List<FieldErrorItem> loi
    ) {
        if (tu != null && den != null && tu.compareTo(den) > 0) {
            loi.add(loiTruong(truong, thongDiep));
        }
    }

    private static boolean khongCoLoiTruong(List<FieldErrorItem> loi, String truong) {
        return loi.stream().noneMatch(muc -> truong.equals(muc.truong()));
    }

    private static ConTroTimKiem docConTro(String tho, SapXepTin sapXep, BoLocTimKiem boLoc) {
        String giaTri = vanBanHoacNull(tho);
        if (giaTri == null) {
            return null;
        }
        Optional<ConTroTimKiem> conTro = ConTroTimKiem.giaiMa(giaTri);
        if (conTro.isEmpty()) {
            throw loiConTro("cursor không hợp lệ.");
        }
        if (conTro.get().sapXep() != sapXep || !conTro.get().dauVanTayLoc().equals(boLoc.dauVanTay())) {
            throw loiConTro("cursor không dùng được với bộ lọc hoặc cách sắp xếp hiện tại.");
        }
        return conTro.get();
    }

    private static AuthValidationException loiConTro(String thongDiep) {
        return new AuthValidationException(
                MA_LOI_DU_LIEU_KHONG_HOP_LE,
                THONG_DIEP_KHONG_HOP_LE,
                List.of(loiTruong(TRUONG_CURSOR, thongDiep))
        );
    }

    private static FieldErrorItem loiTruong(String truong, String thongDiep) {
        return new FieldErrorItem(truong, MA_LOI_DU_LIEU_KHONG_HOP_LE, thongDiep);
    }
}
