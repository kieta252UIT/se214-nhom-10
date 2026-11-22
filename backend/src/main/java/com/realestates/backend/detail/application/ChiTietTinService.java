package com.realestates.backend.detail.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.detail.domain.ChiTietTinRepository;
import com.realestates.backend.detail.domain.ChiTietTinRepository.DongChiTiet;
import com.realestates.backend.detail.domain.ChiTietTinRepository.DongLienHe;
import com.realestates.backend.detail.dto.AnhChiTietItem;
import com.realestates.backend.detail.dto.ChiTietTinResponse;
import com.realestates.backend.detail.dto.LienHeChuTinResponse;
import com.realestates.backend.domain.model.PriceConverter;
import com.realestates.backend.listing.domain.DonViGiaTin;
import com.realestates.backend.listing.exception.ListingNotFoundException;
import com.realestates.backend.search.application.TimKiemTinService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Chi tiết và liên hệ của tin công khai (khách được phép). Mọi trường hợp không xem được (id sai định dạng,
 * không tồn tại, chưa duyệt, đã xóa/gỡ, chủ bị khóa khi xem liên hệ) đều là cùng một 404 KHONG_TIM_THAY, không lộ lý do.
 */
@Service
public class ChiTietTinService {

    private static final String THONG_DIEP_GIAN_DOAN = "Hệ thống đang tạm gián đoạn. Vui lòng thử lại sau.";
    private static final Pattern UUID_CHUAN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final ChiTietTinRepository chiTietTinRepository;

    public ChiTietTinService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            ChiTietTinRepository chiTietTinRepository
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(permissionAndCsrfService, "permissionAndCsrfService không được null");
        this.chiTietTinRepository = Objects.requireNonNull(chiTietTinRepository, "chiTietTinRepository không được null");
    }

    public ChiTietTinResponse chiTiet(String rawSessionCookie, String listingId) {
        PrincipalPhienHienTai principal = permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie, ThaoTacNghiepVu.XEM_CHI_TIET_TIN_DA_DUYET, null, null);
        UUID tinId = docTinId(listingId);
        UUID nguoiXemId = principal != null && principal.getVaiTro() == VaiTroTaiKhoan.NGUOI_DUNG
                ? principal.getTaiKhoanId()
                : null;
        DongChiTiet dong = docKho(() -> chiTietTinRepository.docChiTiet(tinId, nguoiXemId))
                .orElseThrow(ListingNotFoundException::new);

        DonViGiaTin donViGia = DonViGiaTin.tuGiaTri(dong.donViGia())
                .orElseThrow(() -> new IllegalStateException("Đơn vị giá không hợp lệ: " + dong.donViGia()));
        PriceConverter.KetQuaQuyDoiGia quyDoi = PriceConverter.quyDoi(
                dong.giaGoc(), TimKiemTinService.chuyenDonVi(donViGia), dong.dienTichM2());
        List<AnhChiTietItem> anh = dong.anh().stream()
                .map(a -> new AnhChiTietItem(a.thuTu(), a.khoaAnhHienThi()))
                .toList();
        return new ChiTietTinResponse(
                dong.tinId(),
                dong.tieuDe(),
                dong.moTa(),
                dong.loaiGiaoDich(),
                dong.loaiBds(),
                dong.giaGoc(),
                dong.donViGia(),
                quyDoi.giaTongChuanVnd(),
                quyDoi.giaM2ChuanVnd(),
                dong.dienTichM2(),
                dong.diaChi(),
                dong.khuVuc(),
                dong.viDo(),
                dong.kinhDo(),
                dong.ngayDang(),
                anh,
                nguoiXemId == null ? null : dong.daLuu(),
                dong.laTaiTro()
        );
    }

    public LienHeChuTinResponse lienHe(String rawSessionCookie, String listingId) {
        permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie, ThaoTacNghiepVu.XEM_CHI_TIET_TIN_DA_DUYET, null, null);
        UUID tinId = docTinId(listingId);
        DongLienHe dong = docKho(() -> chiTietTinRepository.docLienHe(tinId))
                .orElseThrow(ListingNotFoundException::new);
        return new LienHeChuTinResponse(dong.hoTen(), dong.soDienThoai());
    }

    private static UUID docTinId(String listingId) {
        if (listingId == null || !UUID_CHUAN.matcher(listingId).matches()) {
            throw new ListingNotFoundException();
        }
        return UUID.fromString(listingId);
    }

    private static <T> Optional<T> docKho(Supplier<Optional<T>> viec) {
        try {
            return viec.get();
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
    }
}
