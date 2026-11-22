package com.realestates.backend.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.controller.CsrfAndOriginProtectionFilter;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AuthExceptionHandler;
import com.realestates.backend.auth.exception.AuthSessionSecurityExceptionHandler;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.InMemoryPhienXacThucRepository;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.detail.application.ChiTietTinService;
import com.realestates.backend.detail.controller.ChiTietTinController;
import com.realestates.backend.detail.domain.ChiTietTinRepository;
import com.realestates.backend.exception.GlobalApiExceptionHandler;
import com.realestates.backend.favorite.application.YeuThichService;
import com.realestates.backend.favorite.controller.YeuThichController;
import com.realestates.backend.favorite.domain.ConTroYeuThich;
import com.realestates.backend.favorite.domain.YeuThichRepository;
import com.realestates.backend.listing.exception.ListingExceptionHandler;
import com.realestates.backend.search.domain.TimKiemRepository.DongTin;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** API chi tiết, liên hệ và yêu thích qua MockMvc độc lập (không DB) với kho giả: quyền, CSRF, mã trạng thái, header. */
class ChiTietYeuThichApiTests {

    private static final String ORIGIN = "https://localhost";
    private static final String CSRF_SECRET = "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM";
    private static final Instant GOC = Instant.parse("2026-11-18T03:00:00Z");
    private static final UUID TIN_CONG_KHAI = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TIN_CHU_BI_KHOA = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private AuthSessionCookieConfig cookieConfig;
    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryPhienXacThucRepository phienXacThucRepository;
    private KhoChiTietGia khoChiTiet;
    private KhoYeuThichGia khoYeuThich;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taiKhoanRepository = new InMemoryTaiKhoanRepository();
        phienXacThucRepository = new InMemoryPhienXacThucRepository();
        sessionLifecycleAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(CSRF_SECRET, phienXacThucRepository);
        cookieConfig = taoCookieConfig();
        CurrentPermissionAndCsrfService permissionAndCsrfService = new CurrentPermissionAndCsrfService(
                taiKhoanRepository, phienXacThucRepository, sessionLifecycleAdapter, List.of(ORIGIN));
        khoChiTiet = new KhoChiTietGia();
        khoYeuThich = new KhoYeuThichGia();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ChiTietTinController(new ChiTietTinService(permissionAndCsrfService, khoChiTiet), cookieConfig),
                        new YeuThichController(new YeuThichService(permissionAndCsrfService, khoYeuThich), cookieConfig))
                .setControllerAdvice(
                        new AuthSessionSecurityExceptionHandler(),
                        new AuthExceptionHandler(),
                        new ListingExceptionHandler(),
                        new GlobalApiExceptionHandler()
                )
                .addFilters(new CsrfAndOriginProtectionFilter(permissionAndCsrfService, cookieConfig))
                .build();
    }

    // ------------------------------------------------------------------ chi tiết

    @Test
    @DisplayName("Khách xem chi tiết: 200, no-store, Vary Cookie, đủ trường, daLuu null, không có chuId/email")
    void chiTietKhach() throws Exception {
        mockMvc.perform(yeuCauGet("/api/listings/" + TIN_CONG_KHAI, null))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(jsonPath("$.tinId").value(TIN_CONG_KHAI.toString()))
                .andExpect(jsonPath("$.tieuDe").value("Căn hộ view sông"))
                .andExpect(jsonPath("$.moTa").value("Mô tả chi tiết"))
                .andExpect(jsonPath("$.giaTongChuanVnd").value(2500000000.0))
                .andExpect(jsonPath("$.giaM2ChuanVnd").value(36496350.36))
                .andExpect(jsonPath("$.anh.length()").value(2))
                .andExpect(jsonPath("$.anh[0].thuTu").value(1))
                .andExpect(jsonPath("$.anh[0].khoaAnhHienThi").value("anh/1.png"))
                .andExpect(jsonPath("$.daLuu").value(nullValue()))
                .andExpect(jsonPath("$.laTaiTro").value(false))
                .andExpect(jsonPath("$.chuId").doesNotExist())
                .andExpect(content().string(not(containsString("email"))));
        assertThat(khoChiTiet.nguoiXemCuoi).isNull();
    }

    @Test
    @DisplayName("Người dùng đăng nhập nhận daLuu; kiểm duyệt viên và cookie rác coi như không có daLuu")
    void chiTietTheoNguoiXem() throws Exception {
        Fixture nguoiDung = taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        mockMvc.perform(yeuCauGet("/api/listings/" + TIN_CONG_KHAI, nguoiDung.rawSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daLuu").value(true));
        assertThat(khoChiTiet.nguoiXemCuoi).isEqualTo(nguoiDung.taiKhoan().getId());

        Fixture kiemDuyet = taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        mockMvc.perform(yeuCauGet("/api/listings/" + TIN_CONG_KHAI, kiemDuyet.rawSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daLuu").value(nullValue()));
        assertThat(khoChiTiet.nguoiXemCuoi).isNull();

        mockMvc.perform(yeuCauGet("/api/listings/" + TIN_CONG_KHAI, "cookie-rac"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daLuu").value(nullValue()));
    }

    @Test
    @DisplayName("Tin không công khai, không tồn tại hoặc id sai định dạng cùng nhận 404 KHONG_TIM_THAY với thông điệp giống nhau")
    void chiTiet404() throws Exception {
        for (String id : List.of(UUID.randomUUID().toString(), "khong-phai-uuid", "1-1-1-1-1")) {
            mockMvc.perform(yeuCauGet("/api/listings/" + id, null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.maLoi").value("KHONG_TIM_THAY"))
                    .andExpect(jsonPath("$.thongDiep").value("Không tìm thấy tin."))
                    .andExpect(jsonPath("$.truongLoi.length()").value(0));
        }
    }

    // ------------------------------------------------------------------ liên hệ

    @Test
    @DisplayName("Khách xem liên hệ: 200 chỉ có hoTen và soDienThoai, header đúng")
    void lienHeKhach() throws Exception {
        mockMvc.perform(yeuCauGet("/api/listings/" + TIN_CONG_KHAI + "/contact", null))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(jsonPath("$.hoTen").value("Nguyễn Văn Chủ"))
                .andExpect(jsonPath("$.soDienThoai").value("0912345678"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.chuId").doesNotExist())
                .andExpect(content().string(not(containsString("email"))));
    }

    @Test
    @DisplayName("Liên hệ: chủ bị khóa hoặc tin không công khai nhận 404 KHONG_TIM_THAY")
    void lienHe404() throws Exception {
        for (String id : List.of(TIN_CHU_BI_KHOA.toString(), UUID.randomUUID().toString(), "xyz")) {
            mockMvc.perform(yeuCauGet("/api/listings/" + id + "/contact", null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.maLoi").value("KHONG_TIM_THAY"));
        }
        // Chi tiết của tin có chủ bị khóa vẫn xem được.
        mockMvc.perform(yeuCauGet("/api/listings/" + TIN_CHU_BI_KHOA, null)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ yêu thích

    @Test
    @DisplayName("Khách nhận 401, kiểm duyệt viên và quản trị viên nhận 403 ở PUT, DELETE và GET danh sách")
    void yeuThichPhanQuyen() throws Exception {
        String duongDan = "/api/listings/" + TIN_CONG_KHAI + "/favorite";
        mockMvc.perform(yeuCauGhi(put(duongDan), null)).andExpect(status().isUnauthorized());
        mockMvc.perform(yeuCauGhi(delete(duongDan), null)).andExpect(status().isUnauthorized());
        mockMvc.perform(yeuCauGet("/api/me/favorites", null)).andExpect(status().isUnauthorized());

        for (VaiTroTaiKhoan vaiTro : List.of(VaiTroTaiKhoan.KIEM_DUYET_VIEN, VaiTroTaiKhoan.QUAN_TRI_VIEN)) {
            Fixture f = taoTaiKhoan(vaiTro);
            mockMvc.perform(yeuCauGhi(put(duongDan), f.rawSessionCookie())).andExpect(status().isForbidden());
            mockMvc.perform(yeuCauGhi(delete(duongDan), f.rawSessionCookie())).andExpect(status().isForbidden());
            mockMvc.perform(yeuCauGet("/api/me/favorites", f.rawSessionCookie())).andExpect(status().isForbidden());
        }
        assertThat(khoYeuThich.daLuu).isEmpty();
        assertThat(khoYeuThich.soLanGoiLuu).isZero();
    }

    @Test
    @DisplayName("PUT và DELETE thiếu token CSRF bị 403 và không chạm kho")
    void yeuThichCanCsrf() throws Exception {
        Fixture nguoiDung = taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        String duongDan = "/api/listings/" + TIN_CONG_KHAI + "/favorite";
        for (MockHttpServletRequestBuilder builder : List.of(put(duongDan), delete(duongDan))) {
            builder.header(HttpHeaders.ORIGIN, ORIGIN)
                    .cookie(new Cookie(cookieConfig.getSessionCookieName(), nguoiDung.rawSessionCookie()));
            mockMvc.perform(builder)
                    .andExpect(status().isForbidden())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        }
        assertThat(khoYeuThich.soLanGoiLuu).isZero();
        assertThat(khoYeuThich.soLanGoiBo).isZero();
    }

    @Test
    @DisplayName("PUT và DELETE hợp lệ trả 204 (không body), taiKhoanId lấy từ phiên")
    void yeuThichLuuVaBo() throws Exception {
        Fixture nguoiDung = taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        String duongDan = "/api/listings/" + TIN_CONG_KHAI + "/favorite";

        mockMvc.perform(yeuCauGhi(put(duongDan), nguoiDung.rawSessionCookie()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(content().string(""));
        assertThat(khoYeuThich.daLuu).containsExactly(nguoiDung.taiKhoan().getId() + "/" + TIN_CONG_KHAI);

        // Idempotent
        mockMvc.perform(yeuCauGhi(put(duongDan), nguoiDung.rawSessionCookie())).andExpect(status().isNoContent());
        mockMvc.perform(yeuCauGhi(delete(duongDan), nguoiDung.rawSessionCookie())).andExpect(status().isNoContent());
        mockMvc.perform(yeuCauGhi(delete(duongDan), nguoiDung.rawSessionCookie())).andExpect(status().isNoContent());
        assertThat(khoYeuThich.daLuu).isEmpty();
    }

    @Test
    @DisplayName("PUT tin không công khai/không tồn tại/sai định dạng: 404 KHONG_TIM_THAY; DELETE luôn 204")
    void yeuThichKhongCongKhai() throws Exception {
        Fixture nguoiDung = taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        for (String id : List.of(UUID.randomUUID().toString(), "khong-phai-uuid")) {
            mockMvc.perform(yeuCauGhi(put("/api/listings/" + id + "/favorite"), nguoiDung.rawSessionCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.maLoi").value("KHONG_TIM_THAY"));
            mockMvc.perform(yeuCauGhi(delete("/api/listings/" + id + "/favorite"), nguoiDung.rawSessionCookie()))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @DisplayName("GET /api/me/favorites: items với conHienThi/tin, tin ẩn có tin=null, conTroTiepTheo khi còn trang")
    void danhSachYeuThich() throws Exception {
        Fixture nguoiDung = taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);

        mockMvc.perform(yeuCauGet("/api/me/favorites?limit=2", nguoiDung.rawSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].conHienThi").value(true))
                .andExpect(jsonPath("$.items[0].tin.tieuDe").value("Căn hộ view sông"))
                .andExpect(jsonPath("$.items[0].tin.giaM2ChuanVnd").value(36496350.36))
                .andExpect(jsonPath("$.items[0].tin.laTaiTro").value(true))
                .andExpect(jsonPath("$.items[1].conHienThi").value(false))
                .andExpect(jsonPath("$.items[1].tin").value(nullValue()))
                .andExpect(jsonPath("$.items[1].tinId").value(TIN_CHU_BI_KHOA.toString()))
                .andExpect(jsonPath("$.conTroTiepTheo").isNotEmpty());
        assertThat(khoYeuThich.taiKhoanDocCuoi).isEqualTo(nguoiDung.taiKhoan().getId());
        assertThat(khoYeuThich.gioiHanCuoi).isEqualTo(3);

        mockMvc.perform(yeuCauGet("/api/me/favorites", nguoiDung.rawSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.conTroTiepTheo").value(nullValue()));
        assertThat(khoYeuThich.gioiHanCuoi).isEqualTo(21);
    }

    @Test
    @DisplayName("GET /api/me/favorites: con trỏ hợp lệ được chuyển xuống kho; cursor/limit sai trả 400 theo trường")
    void danhSachThamSo() throws Exception {
        Fixture nguoiDung = taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        UUID tinSau = UUID.randomUUID();
        String cursor = new ConTroYeuThich("2026-11-18 03:00:00.123456+00", tinSau).maHoa();

        mockMvc.perform(yeuCauGet("/api/me/favorites?cursor=" + cursor, nguoiDung.rawSessionCookie()))
                .andExpect(status().isOk());
        assertThat(khoYeuThich.conTroCuoi).isEqualTo(new ConTroYeuThich("2026-11-18 03:00:00.123456+00", tinSau));

        mockMvc.perform(yeuCauGet("/api/me/favorites?cursor=rac", nguoiDung.rawSessionCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"))
                .andExpect(jsonPath("$.truongLoi[0].truong").value("cursor"));
        for (String limit : List.of("0", "51", "abc")) {
            mockMvc.perform(yeuCauGet("/api/me/favorites?limit=" + limit, nguoiDung.rawSessionCookie()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.truongLoi[0].truong").value("limit"));
        }
    }

    // ------------------------------------------------------------------ tiện ích

    private MockHttpServletRequestBuilder yeuCauGet(String duongDan, String rawSessionCookie) {
        MockHttpServletRequestBuilder builder = get(duongDan).accept(MediaType.APPLICATION_JSON);
        if (rawSessionCookie != null) {
            builder.cookie(new Cookie(cookieConfig.getSessionCookieName(), rawSessionCookie));
        }
        return builder;
    }

    /** Yêu cầu ghi hợp lệ về CSRF/Origin (token theo phiên, hoặc token ẩn danh cho khách). */
    private MockHttpServletRequestBuilder yeuCauGhi(MockHttpServletRequestBuilder builder, String rawSessionCookie) {
        builder.header(HttpHeaders.ORIGIN, ORIGIN);
        String csrf = rawSessionCookie != null
                ? sessionLifecycleAdapter.issueSessionCsrfToken(rawSessionCookie)
                : sessionLifecycleAdapter.issueAnonymousCsrfToken();
        builder.header(AuthSessionCookieConfig.CSRF_HEADER_NAME, csrf);
        builder.cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, csrf));
        if (rawSessionCookie != null) {
            builder.cookie(new Cookie(cookieConfig.getSessionCookieName(), rawSessionCookie));
        }
        return builder;
    }

    private record Fixture(TaiKhoan taiKhoan, String rawSessionCookie) {
    }

    private Fixture taoTaiKhoan(VaiTroTaiKhoan vaiTro) {
        Instant now = Instant.now();
        TaiKhoan taiKhoan = TaiKhoan.khoiPhucTuLuuTru(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "chi-tiet-" + UUID.randomUUID() + "@bds-test.local",
                "Tài khoản thử",
                "0901234567",
                vaiTro,
                TrangThaiTaiKhoan.HOAT_DONG,
                true,
                0L,
                "san_sang",
                now,
                now,
                now
        );
        taiKhoanRepository.save(taiKhoan);
        String rawSessionCookie = sessionLifecycleAdapter.generateRawSessionCookie();
        PhienXacThuc phien = PhienXacThuc.taoPhienMoi(
                UUID.randomUUID(),
                taiKhoan.getId(),
                sessionLifecycleAdapter.hashSessionCookie(rawSessionCookie),
                0L,
                now,
                UUID.randomUUID(),
                "cipher-access".getBytes(StandardCharsets.UTF_8),
                "cipher-refresh".getBytes(StandardCharsets.UTF_8),
                now.plusSeconds(3600),
                "v1"
        );
        phienXacThucRepository.save(phien);
        return new Fixture(taiKhoan, rawSessionCookie);
    }

    private static AuthSessionCookieConfig taoCookieConfig() {
        AuthSessionCookieConfig config = new AuthSessionCookieConfig();
        ReflectionTestUtils.setField(config, "cookieSecure", true);
        ReflectionTestUtils.setField(config, "sessionCookieName", AuthSessionCookieConfig.SESSION_COOKIE_NAME);
        ReflectionTestUtils.setField(config, "sessionMaxAgeSeconds", AuthSessionCookieConfig.SESSION_MAX_AGE_SECONDS);
        ReflectionTestUtils.setField(config, "csrfCookieName", AuthSessionCookieConfig.CSRF_COOKIE_NAME);
        ReflectionTestUtils.setField(config, "sessionPersistent", true);
        return config;
    }

    private static DongTin dongTin(UUID id) {
        return new DongTin(id, "Căn hộ view sông", "ban", "can_ho", "Quận 1", "18 Lý Tự Trọng",
                new BigDecimal("2500000000"), "vnd_tong", new BigDecimal("68.5"), "anh/1.png", GOC, null);
    }

    /** Kho chi tiết giả: TIN_CONG_KHAI có đủ dữ liệu; TIN_CHU_BI_KHOA xem được chi tiết nhưng không có liên hệ. */
    private static final class KhoChiTietGia implements ChiTietTinRepository {

        UUID nguoiXemCuoi;

        @Override
        public Optional<DongChiTiet> docChiTiet(UUID tinId, UUID nguoiXemId) {
            nguoiXemCuoi = nguoiXemId;
            if (!tinId.equals(TIN_CONG_KHAI) && !tinId.equals(TIN_CHU_BI_KHOA)) {
                return Optional.empty();
            }
            return Optional.of(new DongChiTiet(
                    tinId, "Căn hộ view sông", "Mô tả chi tiết", "ban", "can_ho",
                    new BigDecimal("2500000000"), "vnd_tong", new BigDecimal("68.5"),
                    "18 Lý Tự Trọng", "Quận 1", new BigDecimal("10.7772"), new BigDecimal("106.7015"), GOC,
                    false, nguoiXemId == null ? null : Boolean.TRUE,
                    List.of(new AnhChiTiet(1, "anh/1.png"), new AnhChiTiet(2, "anh/2.png"))
            ));
        }

        @Override
        public Optional<DongLienHe> docLienHe(UUID tinId) {
            return tinId.equals(TIN_CONG_KHAI)
                    ? Optional.of(new DongLienHe("Nguyễn Văn Chủ", "0912345678"))
                    : Optional.empty();
        }
    }

    /** Kho yêu thích giả: chỉ TIN_CONG_KHAI được lưu; danh sách cố định 3 mục (công khai, ẩn, công khai). */
    private static final class KhoYeuThichGia implements YeuThichRepository {

        final Set<String> daLuu = new HashSet<>();
        int soLanGoiLuu;
        int soLanGoiBo;
        UUID taiKhoanDocCuoi;
        ConTroYeuThich conTroCuoi;
        int gioiHanCuoi;

        @Override
        public boolean luu(UUID taiKhoanId, UUID tinId) {
            soLanGoiLuu++;
            String khoa = taiKhoanId + "/" + tinId;
            if (daLuu.contains(khoa)) {
                return true;
            }
            if (!tinId.equals(TIN_CONG_KHAI)) {
                return false;
            }
            daLuu.add(khoa);
            return true;
        }

        @Override
        public void bo(UUID taiKhoanId, UUID tinId) {
            soLanGoiBo++;
            daLuu.remove(taiKhoanId + "/" + tinId);
        }

        @Override
        public List<DongYeuThich> docDanhSach(UUID taiKhoanId, ConTroYeuThich sau, int gioiHan) {
            taiKhoanDocCuoi = taiKhoanId;
            conTroCuoi = sau;
            gioiHanCuoi = gioiHan;
            List<DongYeuThich> tatCa = new ArrayList<>();
            tatCa.add(new DongYeuThich(TIN_CONG_KHAI, GOC.plusSeconds(30), "2026-11-18 03:00:30+00",
                    true, dongTin(TIN_CONG_KHAI), true));
            tatCa.add(new DongYeuThich(TIN_CHU_BI_KHOA, GOC.plusSeconds(20), "2026-11-18 03:00:20+00",
                    false, null, false));
            tatCa.add(new DongYeuThich(UUID.fromString("33333333-3333-4333-8333-333333333333"), GOC.plusSeconds(10),
                    "2026-11-18 03:00:10+00", true, dongTin(UUID.fromString("33333333-3333-4333-8333-333333333333")), false));
            return tatCa.subList(0, Math.min(gioiHan, tatCa.size()));
        }
    }
}
