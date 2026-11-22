package com.realestates.backend.favorite.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.favorite.domain.ConTroYeuThich;
import com.realestates.backend.favorite.domain.YeuThichRepository;
import com.realestates.backend.favorite.domain.YeuThichRepository.DongYeuThich;
import com.realestates.backend.favorite.dto.DanhSachYeuThichResponse;
import com.realestates.backend.favorite.dto.YeuThichItem;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.exception.ListingNotFoundException;
import com.realestates.backend.search.application.TimKiemTinService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Yêu thích: chỉ vai trò nguoi_dung (khách 401, vai trò khác 403). taiKhoanId luôn lấy từ principal của phiên.
 * Quy ước: PUT id sai định dạng/không công khai và chưa lưu = 404; DELETE luôn 204 (kể cả id sai định dạng/không tồn tại).
 */
@Service
public class YeuThichService {

    private static final String THONG_DIEP_KHONG_HOP_LE = "Dữ liệu gửi lên không hợp lệ.";
    private static final String THONG_DIEP_GIAN_DOAN = "Hệ thống đang tạm gián đoạn. Vui lòng thử lại sau.";
    private static final Pattern UUID_CHUAN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );
    private static final Pattern SO_NGUYEN_DUONG = Pattern.compile("^[0-9]{1,9}$");

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final YeuThichRepository yeuThichRepository;

    public YeuThichService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            YeuThichRepository yeuThichRepository
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(permissionAndCsrfService, "permissionAndCsrfService không được null");
        this.yeuThichRepository = Objects.requireNonNull(yeuThichRepository, "yeuThichRepository không được null");
    }

    public void luu(String rawSessionCookie, String listingId) {
        UUID taiKhoanId = nguoiDung(rawSessionCookie);
        UUID tinId = docTinId(listingId);
        if (tinId == null || !docKho(() -> yeuThichRepository.luu(taiKhoanId, tinId))) {
            throw new ListingNotFoundException();
        }
    }

    public void bo(String rawSessionCookie, String listingId) {
        UUID taiKhoanId = nguoiDung(rawSessionCookie);
        UUID tinId = docTinId(listingId);
        if (tinId == null) {
            return;
        }
        docKho(() -> {
            yeuThichRepository.bo(taiKhoanId, tinId);
            return Boolean.TRUE;
        });
    }

    public DanhSachYeuThichResponse danhSach(String rawSessionCookie, String cursor, String limit) {
        UUID taiKhoanId = nguoiDung(rawSessionCookie);
        List<FieldErrorItem> loi = new ArrayList<>();
        int gioiHan = docGioiHan(limit, loi);
        ConTroYeuThich conTro = docConTro(cursor, loi);
        if (!loi.isEmpty()) {
            throw new AuthValidationException(
                    TinDangService.MA_LOI_DU_LIEU_KHONG_HOP_LE, THONG_DIEP_KHONG_HOP_LE, loi);
        }

        List<DongYeuThich> docDuoc = docKho(() -> yeuThichRepository.docDanhSach(taiKhoanId, conTro, gioiHan + 1));
        boolean conTiep = docDuoc.size() > gioiHan;
        List<DongYeuThich> trang = conTiep ? docDuoc.subList(0, gioiHan) : docDuoc;
        String conTroTiepTheo = null;
        if (conTiep) {
            DongYeuThich cuoi = trang.get(trang.size() - 1);
            conTroTiepTheo = new ConTroYeuThich(cuoi.luuLucVanBan(), cuoi.tinId()).maHoa();
        }
        List<YeuThichItem> items = trang.stream()
                .map(dong -> new YeuThichItem(
                        dong.tinId(),
                        dong.luuLuc(),
                        dong.conHienThi(),
                        dong.conHienThi() ? TimKiemTinService.chuyenThanhItem(dong.tin(), dong.laTaiTro()) : null
                ))
                .toList();
        return new DanhSachYeuThichResponse(items, conTroTiepTheo);
    }

    private UUID nguoiDung(String rawSessionCookie) {
        PrincipalPhienHienTai principal = permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie, ThaoTacNghiepVu.THEM_GO_XEM_DANH_SACH_YEU_THICH, null, null);
        return principal.getTaiKhoanId();
    }

    /** null nếu sai định dạng. */
    private static UUID docTinId(String listingId) {
        if (listingId == null || !UUID_CHUAN.matcher(listingId).matches()) {
            return null;
        }
        return UUID.fromString(listingId);
    }

    private static int docGioiHan(String tho, List<FieldErrorItem> loi) {
        String giaTri = tho == null ? null : tho.strip();
        if (giaTri == null || giaTri.isEmpty()) {
            return TimKiemTinService.GIOI_HAN_MAC_DINH;
        }
        if (SO_NGUYEN_DUONG.matcher(giaTri).matches()) {
            int gioiHan = Integer.parseInt(giaTri);
            if (gioiHan >= 1 && gioiHan <= TimKiemTinService.GIOI_HAN_TOI_DA) {
                return gioiHan;
            }
        }
        loi.add(loiTruong(
                TimKiemTinService.TRUONG_LIMIT,
                "limit phải là số nguyên từ 1 đến " + TimKiemTinService.GIOI_HAN_TOI_DA + "."
        ));
        return TimKiemTinService.GIOI_HAN_MAC_DINH;
    }

    private static ConTroYeuThich docConTro(String tho, List<FieldErrorItem> loi) {
        String giaTri = tho == null ? null : tho.strip();
        if (giaTri == null || giaTri.isEmpty()) {
            return null;
        }
        return ConTroYeuThich.giaiMa(giaTri).orElseGet(() -> {
            loi.add(loiTruong(TimKiemTinService.TRUONG_CURSOR, "cursor không hợp lệ."));
            return null;
        });
    }

    private static FieldErrorItem loiTruong(String truong, String thongDiep) {
        return new FieldErrorItem(truong, TinDangService.MA_LOI_DU_LIEU_KHONG_HOP_LE, thongDiep);
    }

    private static <T> T docKho(Supplier<T> viec) {
        try {
            return viec.get();
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
    }
}
