package com.realestates.backend.moderation.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.exception.AccountLockedException;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.EmailNotVerifiedException;
import com.realestates.backend.auth.exception.InsufficientPermissionException;
import com.realestates.backend.auth.exception.InvalidCsrfException;
import com.realestates.backend.auth.exception.UnauthenticatedSessionException;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.ChuanHoaJson;
import com.realestates.backend.listing.exception.ListingConflictException;
import com.realestates.backend.listing.exception.ListingNotFoundException;
import com.realestates.backend.listing.exception.ListingPreconditionException;
import com.realestates.backend.moderation.domain.ConTroHangCho;
import com.realestates.backend.moderation.domain.KiemDuyetRepository;
import com.realestates.backend.moderation.domain.KiemDuyetRepository.DuLieuQuyetDinh;
import com.realestates.backend.moderation.domain.KiemDuyetRepository.KetQuaYeuCauQuyetDinh;
import com.realestates.backend.moderation.domain.KiemDuyetRepository.LoiNghiepVuKiemDuyetException;
import com.realestates.backend.moderation.dto.ChiTietPhienBanResponse;
import com.realestates.backend.moderation.dto.HangChoResponse;
import com.realestates.backend.moderation.dto.KetQuaQuyetDinh;
import com.realestates.backend.moderation.dto.MucHangCho;
import com.realestates.backend.moderation.exception.KiemDuyetMaLoi;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class KiemDuyetService {

    public static final String HEADER_IF_MATCH = "If-Match";
    public static final int GIOI_HAN_MAC_DINH = 20;
    public static final int GIOI_HAN_TOI_DA = 50;
    public static final int DO_DAI_LY_DO_TOI_DA = 1000;
    public static final String TRUONG_LY_DO = "lyDo";

    private static final String MA_LOI_DU_LIEU_KHONG_HOP_LE = TinDangService.MA_LOI_DU_LIEU_KHONG_HOP_LE;
    private static final String THONG_DIEP_KHONG_HOP_LE = "Dữ liệu gửi lên không hợp lệ.";
    private static final String THONG_DIEP_GIAN_DOAN = "Hệ thống kiểm duyệt đang tạm gián đoạn. Vui lòng thử lại sau.";
    private static final Pattern ETAG_MANH = Pattern.compile("^\"(0|[1-9][0-9]*)\"$");
    private static final Pattern SO_NGUYEN_DUONG = Pattern.compile("^[0-9]{1,9}$");

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final KiemDuyetRepository kiemDuyetRepository;
    private final Clock clock;

    @Autowired
    public KiemDuyetService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            KiemDuyetRepository kiemDuyetRepository
    ) {
        this(permissionAndCsrfService, kiemDuyetRepository, Clock.systemUTC());
    }

    public KiemDuyetService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            KiemDuyetRepository kiemDuyetRepository,
            Clock clock
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(permissionAndCsrfService, "permissionAndCsrfService không được null");
        this.kiemDuyetRepository = Objects.requireNonNull(kiemDuyetRepository, "kiemDuyetRepository không được null");
        this.clock = Objects.requireNonNull(clock, "clock không được null");
    }

    public HangChoResponse layHangCho(String rawSessionCookie, String rawCursor, String rawLimit) {
        permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie,
                ThaoTacNghiepVu.XEM_HANG_CHO_VA_DUYET_TIN,
                null,
                null
        );
        int gioiHan = docGioiHan(rawLimit);
        ConTroHangCho conTro = docConTro(rawCursor);

        List<MucHangCho> docDuoc = docHangChoAnToan(conTro, gioiHan + 1);
        if (docDuoc.size() <= gioiHan) {
            return new HangChoResponse(List.copyOf(docDuoc), null);
        }
        List<MucHangCho> trang = List.copyOf(docDuoc.subList(0, gioiHan));
        MucHangCho cuoi = trang.get(trang.size() - 1);
        return new HangChoResponse(trang, new ConTroHangCho(cuoi.guiLuc(), cuoi.phienBanId()).maHoa());
    }

    public ChiTietPhienBanResponse layChiTietPhienBan(String rawSessionCookie, UUID tinId, UUID phienBanId) {
        permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie,
                ThaoTacNghiepVu.XEM_HANG_CHO_VA_DUYET_TIN,
                null,
                null
        );
        try {
            return kiemDuyetRepository.docChiTietPhienBan(tinId, phienBanId)
                    .orElseThrow(ListingNotFoundException::new);
        } catch (ListingNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
    }

    public KetQuaQuyetDinh pheDuyet(
            String rawSessionCookie,
            UUID tinId,
            UUID phienBanId,
            String rawIdempotencyKey,
            String rawIfMatch
    ) {
        PrincipalPhienHienTai nguoiDuyet = permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie,
                ThaoTacNghiepVu.XEM_HANG_CHO_VA_DUYET_TIN,
                null,
                null
        );
        String maYDinh = chuanHoaVaKiemTraMaYDinh(rawIdempotencyKey);
        long phienBanCapNhatDaXem = docIfMatch(rawIfMatch);

        Map<String, Object> yeuCauDeBam = new LinkedHashMap<>();
        yeuCauDeBam.put("ifMatch", rawIfMatch.trim());
        String duLieuBam = ChuanHoaJson.bamYeuCau("POST", duongDan(tinId, phienBanId, "approve"), yeuCauDeBam);

        return thucThiQuyetDinh(
                rawSessionCookie,
                nguoiDuyet.getTaiKhoanId(),
                maYDinh,
                duLieuBam,
                tinId,
                phienBanId,
                KiemDuyetRepository.KET_QUA_DA_DUYET,
                null,
                phienBanCapNhatDaXem
        );
    }

    public KetQuaQuyetDinh tuChoi(
            String rawSessionCookie,
            UUID tinId,
            UUID phienBanId,
            String rawIdempotencyKey,
            String rawIfMatch,
            Map<String, Object> rawBody
    ) {
        PrincipalPhienHienTai nguoiDuyet = permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie,
                ThaoTacNghiepVu.XEM_HANG_CHO_VA_DUYET_TIN,
                null,
                null
        );
        String maYDinh = chuanHoaVaKiemTraMaYDinh(rawIdempotencyKey);
        long phienBanCapNhatDaXem = docIfMatch(rawIfMatch);
        String lyDo = docLyDo(rawBody);

        Map<String, Object> thanBam = new LinkedHashMap<>();
        thanBam.put(TRUONG_LY_DO, lyDo);
        Map<String, Object> yeuCauDeBam = new LinkedHashMap<>();
        yeuCauDeBam.put("ifMatch", rawIfMatch.trim());
        yeuCauDeBam.put("body", thanBam);
        String duLieuBam = ChuanHoaJson.bamYeuCau("POST", duongDan(tinId, phienBanId, "reject"), yeuCauDeBam);

        return thucThiQuyetDinh(
                rawSessionCookie,
                nguoiDuyet.getTaiKhoanId(),
                maYDinh,
                duLieuBam,
                tinId,
                phienBanId,
                KiemDuyetRepository.KET_QUA_BI_TU_CHOI,
                lyDo,
                phienBanCapNhatDaXem
        );
    }

    private KetQuaQuyetDinh thucThiQuyetDinh(
            String rawSessionCookie,
            UUID taiKhoanId,
            String maYDinh,
            String duLieuBam,
            UUID tinId,
            UUID phienBanId,
            String ketQua,
            String lyDo,
            long phienBanCapNhatDaXem
    ) {
        try {
            return permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                    rawSessionCookie,
                    ThaoTacNghiepVu.XEM_HANG_CHO_VA_DUYET_TIN,
                    null,
                    tinId,
                    null,
                    freshPrincipal -> kiemDuyetRepository.quyetDinhTrongTransaction(new DuLieuQuyetDinh(
                            tinId,
                            phienBanId,
                            freshPrincipal.getTaiKhoanId(),
                            ketQua,
                            lyDo,
                            phienBanCapNhatDaXem,
                            clock.instant(),
                            maYDinh,
                            duLieuBam
                    ))
            );
        } catch (LoiNghiepVuKiemDuyetException ex) {
            throw chuyenLoiNghiepVu(ex);
        } catch (DuplicateKeyException ex) {
            Optional<KetQuaYeuCauQuyetDinh> replay = traCuuYeuCauQuyetDinh(taiKhoanId, maYDinh);
            if (replay.isPresent()) {
                return phatLai(replay.get(), duLieuBam);
            }
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        } catch (AuthValidationException | UnauthenticatedSessionException | InsufficientPermissionException
                | AccountLockedException | EmailNotVerifiedException | InvalidCsrfException
                | AuthProviderUnavailableException | ListingConflictException | ListingNotFoundException
                | ListingPreconditionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
    }

    private Optional<KetQuaYeuCauQuyetDinh> traCuuYeuCauQuyetDinh(UUID taiKhoanId, String maYDinh) {
        try {
            return kiemDuyetRepository.timYeuCauQuyetDinh(taiKhoanId, maYDinh);
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
    }

    private List<MucHangCho> docHangChoAnToan(ConTroHangCho conTro, int gioiHan) {
        try {
            return kiemDuyetRepository.docHangCho(conTro, gioiHan);
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
    }

    private static KetQuaQuyetDinh phatLai(KetQuaYeuCauQuyetDinh daLuu, String duLieuBam) {
        if (!daLuu.duLieuBam().equals(duLieuBam)) {
            throw new ListingConflictException(
                    ListingConflictException.MA_LOI_MA_Y_DINH_KHAC_NOI_DUNG,
                    "Mã Idempotency-Key đã được sử dụng cho một nội dung yêu cầu khác."
            );
        }
        return daLuu.ketQua();
    }

    private static String duongDan(UUID tinId, UUID phienBanId, String hanhDong) {
        return "/api/moderation/listings/" + tinId + "/versions/" + phienBanId + "/" + hanhDong;
    }

    static String chuanHoaVaKiemTraMaYDinh(String rawIdempotencyKey) {
        if (rawIdempotencyKey == null || rawIdempotencyKey.isBlank()) {
            throw loiTruong(TinDangService.HEADER_IDEMPOTENCY_KEY, "Thiếu header Idempotency-Key.");
        }
        String trimmed = rawIdempotencyKey.trim();
        if (trimmed.length() > TinDangService.MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw loiTruong(
                    TinDangService.HEADER_IDEMPOTENCY_KEY,
                    "Idempotency-Key vượt quá " + TinDangService.MAX_IDEMPOTENCY_KEY_LENGTH + " ký tự."
            );
        }
        return trimmed;
    }

    static long docIfMatch(String rawIfMatch) {
        if (rawIfMatch == null || rawIfMatch.isBlank()) {
            throw new ListingPreconditionException(
                    ListingPreconditionException.MA_LOI_THIEU_IF_MATCH,
                    "Thiếu header If-Match. Hãy tải lại chi tiết phiên bản để lấy ETag hiện tại."
            );
        }
        String giaTri = rawIfMatch.trim();
        if (ETAG_MANH.matcher(giaTri).matches()) {
            try {
                return Long.parseLong(giaTri.substring(1, giaTri.length() - 1));
            } catch (NumberFormatException ex) {
                throw loiIfMatchKhongHopLe();
            }
        }
        throw loiIfMatchKhongHopLe();
    }

    static String docLyDo(Map<String, Object> rawBody) {
        Object tho = rawBody == null ? null : rawBody.get(TRUONG_LY_DO);
        if (!(tho instanceof String chuoi) || chuoi.isBlank()) {
            throw loiTruong(TRUONG_LY_DO, "Lý do từ chối là bắt buộc và không được để trống.");
        }
        String lyDo = chuoi.trim();
        if (lyDo.length() > DO_DAI_LY_DO_TOI_DA) {
            throw loiTruong(TRUONG_LY_DO, "Lý do từ chối tối đa " + DO_DAI_LY_DO_TOI_DA + " ký tự.");
        }
        return lyDo;
    }

    static int docGioiHan(String rawLimit) {
        if (rawLimit == null || rawLimit.isBlank()) {
            return GIOI_HAN_MAC_DINH;
        }
        String giaTri = rawLimit.trim();
        if (SO_NGUYEN_DUONG.matcher(giaTri).matches()) {
            int gioiHan = Integer.parseInt(giaTri);
            if (gioiHan >= 1 && gioiHan <= GIOI_HAN_TOI_DA) {
                return gioiHan;
            }
        }
        throw loiTruong("limit", "limit phải là số nguyên từ 1 đến " + GIOI_HAN_TOI_DA + ".");
    }

    static ConTroHangCho docConTro(String rawCursor) {
        if (rawCursor == null || rawCursor.isEmpty()) {
            return null;
        }
        return ConTroHangCho.giaiMa(rawCursor.trim())
                .orElseThrow(() -> loiTruong("cursor", "cursor không hợp lệ."));
    }

    private static AuthValidationException loiIfMatchKhongHopLe() {
        return loiTruong(
                HEADER_IF_MATCH,
                "If-Match phải là ETag mạnh dạng \"số\" lấy từ chi tiết phiên bản."
        );
    }

    private static AuthValidationException loiTruong(String truong, String thongDiep) {
        return new AuthValidationException(
                MA_LOI_DU_LIEU_KHONG_HOP_LE,
                THONG_DIEP_KHONG_HOP_LE,
                List.of(new FieldErrorItem(truong, MA_LOI_DU_LIEU_KHONG_HOP_LE, thongDiep))
        );
    }

    private static RuntimeException chuyenLoiNghiepVu(LoiNghiepVuKiemDuyetException ex) {
        return switch (ex.getLoaiLoi()) {
            case TIN_KHONG_TON_TAI, PHIEN_BAN_KHONG_THUOC_TIN -> new ListingNotFoundException();
            case TIN_DA_XOA -> new ListingConflictException(KiemDuyetMaLoi.TIN_DA_XOA, ex.getMessage());
            case TIN_DA_BI_GO -> new ListingConflictException(KiemDuyetMaLoi.TIN_DA_BI_GO, ex.getMessage());
            case PHIEN_BAN_CU -> new ListingPreconditionException(
                    ListingPreconditionException.MA_LOI_PHIEN_BAN_CU,
                    ex.getMessage()
            );
            case PHIEN_BAN_DA_CU -> new ListingConflictException(KiemDuyetMaLoi.PHIEN_BAN_DA_CU, ex.getMessage());
            case DA_CO_QUYET_DINH -> new ListingConflictException(KiemDuyetMaLoi.DA_CO_QUYET_DINH, ex.getMessage());
            case XUNG_DOT_MA_Y_DINH -> new ListingConflictException(
                    ListingConflictException.MA_LOI_MA_Y_DINH_KHAC_NOI_DUNG,
                    ex.getMessage()
            );
        };
    }
}
