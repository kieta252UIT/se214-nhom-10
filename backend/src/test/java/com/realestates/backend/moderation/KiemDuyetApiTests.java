package com.realestates.backend.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import com.realestates.backend.exception.GlobalApiExceptionHandler;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.ChuanHoaJson;
import com.realestates.backend.listing.exception.ListingExceptionHandler;
import com.realestates.backend.moderation.application.KiemDuyetService;
import com.realestates.backend.moderation.controller.KiemDuyetController;
import com.realestates.backend.moderation.domain.ConTroHangCho;
import com.realestates.backend.moderation.domain.KiemDuyetRepository;
import com.realestates.backend.moderation.dto.AnhPhienBan;
import com.realestates.backend.moderation.dto.ChiTietPhienBanResponse;
import com.realestates.backend.moderation.dto.KetQuaQuyetDinh;
import com.realestates.backend.moderation.dto.MucHangCho;
import com.realestates.backend.moderation.dto.NoiDungPhienBan;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

/**
 * API kiểm duyệt qua MockMvc độc lập (không DB): kiểm tra phân quyền, ETag, ánh xạ 428/412/409/400/404 và lỗi theo trường.
 * Kho dữ liệu được thay bằng bản giả giữ cố định phienBanCapNhat = 7.
 */
class KiemDuyetApiTests {

    private static final String ORIGIN = "https://localhost";
    private static final String CSRF_SECRET = "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM";
    private static final long CAP_NHAT_HIEN_TAI = 7L;
    private static final Instant GOC = Instant.parse("2026-11-18T03:00:00Z");

    private final UUID tinId = UUID.randomUUID();
    private final UUID phienBanId = UUID.randomUUID();
    private final UUID chuId = UUID.randomUUID();

    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private AuthSessionCookieConfig cookieConfig;
    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryPhienXacThucRepository phienXacThucRepository;
    private KhoGia khoGia;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taiKhoanRepository = new InMemoryTaiKhoanRepository();
        phienXacThucRepository = new InMemoryPhienXacThucRepository();
        sessionLifecycleAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(CSRF_SECRET, phienXacThucRepository);
        cookieConfig = taoCookieConfig();
        CurrentPermissionAndCsrfService permissionAndCsrfService = new CurrentPermissionAndCsrfService(
                taiKhoanRepository,
                phienXacThucRepository,
                sessionLifecycleAdapter,
                List.of(ORIGIN)
        );
        khoGia = new KhoGia();
        KiemDuyetService kiemDuyetService = new KiemDuyetService(
                permissionAndCsrfService,
                khoGia,
                Clock.fixed(GOC, ZoneOffset.UTC)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new KiemDuyetController(kiemDuyetService, cookieConfig))
                .setControllerAdvice(
                        new AuthSessionSecurityExceptionHandler(),
                        new AuthExceptionHandler(),
                        new ListingExceptionHandler(),
                        new GlobalApiExceptionHandler()
                )
                .addFilters(new CsrfAndOriginProtectionFilter(permissionAndCsrfService, cookieConfig))
                .build();
    }

    @Test
    @DisplayName("Người dùng thường nhận 403 KHONG_DU_QUYEN ở cả bốn route")
    void nguoiDungThuongBi403() throws Exception {
        Fixture nguoiDung = taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);

        mockMvc.perform(yeuCauGet("/api/moderation/queue", nguoiDung.rawSessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));
        mockMvc.perform(yeuCauGet(duongDanChiTiet(), nguoiDung.rawSessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));
        mockMvc.perform(yeuCauPost(duongDanQuyetDinh("approve"), nguoiDung.rawSessionCookie(), "k-1", "\"7\"", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));
        mockMvc.perform(yeuCauPost(duongDanQuyetDinh("reject"), nguoiDung.rawSessionCookie(), "k-2", "\"7\"", Map.of("lyDo", "x")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));
        assertThat(khoGia.soLanQuyetDinh).isZero();
    }

    @Test
    @DisplayName("Chưa đăng nhập nhận 401 CHUA_DANG_NHAP ở cả bốn route")
    void khachBi401() throws Exception {
        mockMvc.perform(yeuCauGet("/api/moderation/queue", null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));
        mockMvc.perform(yeuCauGet(duongDanChiTiet(), null))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(yeuCauPost(duongDanQuyetDinh("approve"), null, "k-1", "\"7\"", null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));
        mockMvc.perform(yeuCauPost(duongDanQuyetDinh("reject"), null, "k-2", "\"7\"", Map.of("lyDo", "x")))
                .andExpect(status().isUnauthorized());
        assertThat(khoGia.soLanQuyetDinh).isZero();
    }

    @Test
    @DisplayName("Chi tiết phiên bản: 200 với ETag mạnh theo phienBanCapNhat, no-store, Vary Cookie; sai phiên bản 404")
    void chiTietCoEtag() throws Exception {
        Fixture kdv = taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);

        mockMvc.perform(yeuCauGet(duongDanChiTiet(), kdv.rawSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"7\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(jsonPath("$.tinId").value(tinId.toString()))
                .andExpect(jsonPath("$.phienBanCapNhat").value(7))
                .andExpect(jsonPath("$.laBanSua").value(true))
                .andExpect(jsonPath("$.phienBan.id").value(phienBanId.toString()))
                .andExpect(jsonPath("$.phienBan.anh[0].khoaAnhHienThi").value("anh/hien-thi-1.png"))
                .andExpect(jsonPath("$.phienBanDaDuyet.soPhienBan").value(1));

        mockMvc.perform(yeuCauGet("/api/moderation/listings/" + tinId + "/versions/" + UUID.randomUUID(), kdv.rawSessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.maLoi").value("KHONG_TIM_THAY"));
    }

    @Test
    @DisplayName("Hàng chờ: 200 no-store, phân trang bằng nextCursor mờ; limit hoặc cursor sai nhận 400 với lỗi theo trường")
    void hangChoVaPhanTrang() throws Exception {
        Fixture kdv = taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);

        String trang1 = mockMvc.perform(yeuCauGet("/api/moderation/queue?limit=2", kdv.rawSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].tieuDe").value("Tin 0"))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String conTro = JsonPath.read(trang1, "$.nextCursor");
        assertThat(ConTroHangCho.giaiMa(conTro)).isPresent();

        mockMvc.perform(yeuCauGet("/api/moderation/queue?limit=2&cursor=" + conTro, kdv.rawSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].tieuDe").value("Tin 2"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        mockMvc.perform(yeuCauGet("/api/moderation/queue", kdv.rawSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        for (String limit : List.of("0", "51", "abc", "-3")) {
            mockMvc.perform(yeuCauGet("/api/moderation/queue?limit=" + limit, kdv.rawSessionCookie()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"))
                    .andExpect(jsonPath("$.truongLoi[0].truong").value("limit"));
        }
        mockMvc.perform(yeuCauGet("/api/moderation/queue?cursor=khong-phai-con-tro", kdv.rawSessionCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"))
                .andExpect(jsonPath("$.truongLoi[0].truong").value("cursor"));
    }

    @Test
    @DisplayName("Phê duyệt: 200 với ETag mới, no-store và thân đầy đủ; thiếu If-Match 428, If-Match lệch 412, thiếu Idempotency-Key 400")
    void pheDuyetVaDieuKienTienQuyet() throws Exception {
        Fixture kdv = taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);

        mockMvc.perform(yeuCauPost(duongDanQuyetDinh("approve"), kdv.rawSessionCookie(), "duyet-1", "\"7\"", null))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"8\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.tinId").value(tinId.toString()))
                .andExpect(jsonPath("$.phienBanId").value(phienBanId.toString()))
                .andExpect(jsonPath("$.ketQuaKiemDuyet").value("da_duyet"))
                .andExpect(jsonPath("$.trangThaiTin").value("da_duyet"))
                .andExpect(jsonPath("$.nguoiDuyetId").value(kdv.taiKhoan().getId().toString()))
                .andExpect(jsonPath("$.phienBanCapNhat").value(8))
                .andExpect(jsonPath("$.lyDoTuChoi").doesNotExist());
        assertThat(khoGia.duLieuCuoi.ketQua()).isEqualTo("da_duyet");
        assertThat(khoGia.duLieuCuoi.phienBanCapNhatDaXem()).isEqualTo(7L);
        assertThat(khoGia.duLieuCuoi.maYDinh()).isEqualTo("duyet-1");
        assertThat(khoGia.duLieuCuoi.nguoiDuyetId()).isEqualTo(kdv.taiKhoan().getId());

        mockMvc.perform(yeuCauPost(duongDanQuyetDinh("approve"), kdv.rawSessionCookie(), "duyet-2", null, null))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.maLoi").value("THIEU_IF_MATCH"));
        mockMvc.perform(yeuCauPost(duongDanQuyetDinh("approve"), kdv.rawSessionCookie(), "duyet-3", "\"3\"", null))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.maLoi").value("PHIEN_BAN_CU"));
        mockMvc.perform(yeuCauPost(duongDanQuyetDinh("approve"), kdv.rawSessionCookie(), "duyet-4", "W/\"7\"", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"))
                .andExpect(jsonPath("$.truongLoi[0].truong").value("If-Match"));
        mockMvc.perform(yeuCauPost(duongDanQuyetDinh("approve"), kdv.rawSessionCookie(), null, "\"7\"", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truongLoi[0].truong").value("Idempotency-Key"));
        assertThat(khoGia.soLanQuyetDinh).isEqualTo(1);
    }

    @Test
    @DisplayName("Từ chối: lyDo bắt buộc, trim, tối đa 1000 ký tự -> 400 với lỗi trường lyDo; hợp lệ thì 200 và lý do đã trim")
    void tuChoiKiemTraLyDo() throws Exception {
        Fixture kdv = taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        java.util.Map<String, Object> lyDoNull = new java.util.HashMap<>();
        lyDoNull.put("lyDo", null);

        List<Map<String, Object>> cacBodyLoi = new ArrayList<>();
        cacBodyLoi.add(lyDoNull);
        cacBodyLoi.add(Map.of("lyDo", ""));
        cacBodyLoi.add(Map.of("lyDo", "    "));
        cacBodyLoi.add(Map.of("lyDo", "a".repeat(1001)));
        cacBodyLoi.add(Map.of("lyDo", 42));
        cacBodyLoi.add(Map.of("khac", "x"));
        cacBodyLoi.add(Map.of());
        int stt = 0;
        for (Map<String, Object> body : cacBodyLoi) {
            mockMvc.perform(yeuCauPost(duongDanQuyetDinh("reject"), kdv.rawSessionCookie(), "tc-loi-" + stt++, "\"7\"", body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"))
                    .andExpect(jsonPath("$.truongLoi.length()").value(1))
                    .andExpect(jsonPath("$.truongLoi[0].truong").value("lyDo"));
        }
        mockMvc.perform(yeuCauPost(duongDanQuyetDinh("reject"), kdv.rawSessionCookie(), "tc-khong-body", "\"7\"", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truongLoi[0].truong").value("lyDo"));
        assertThat(khoGia.soLanQuyetDinh).isZero();

        mockMvc.perform(yeuCauPost(duongDanQuyetDinh("reject"), kdv.rawSessionCookie(), "tc-ok", "\"7\"", Map.of("lyDo", "  Ảnh sai sự thật  ")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"8\""))
                .andExpect(jsonPath("$.ketQuaKiemDuyet").value("bi_tu_choi"))
                .andExpect(jsonPath("$.lyDoTuChoi").value("Ảnh sai sự thật"));
        assertThat(khoGia.duLieuCuoi.lyDo()).isEqualTo("Ảnh sai sự thật");

        mockMvc.perform(yeuCauPost(duongDanQuyetDinh("reject"), kdv.rawSessionCookie(), "tc-1000", "\"7\"", Map.of("lyDo", "c".repeat(1000))))
                .andExpect(status().isOk());
        mockMvc.perform(yeuCauPost(duongDanQuyetDinh("reject"), kdv.rawSessionCookie(), "tc-428", null, Map.of("lyDo", "x")))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    @DisplayName("Lỗi nghiệp vụ của kho được ánh xạ: DA_CO_QUYET_DINH 409, PHIEN_BAN_DA_CU 409, TIN_DA_XOA 409, TIN_DA_BI_GO 409, không thuộc tin 404")
    void anhXaLoiNghiepVu() throws Exception {
        Fixture kdv = taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        Map<KiemDuyetRepository.LoaiLoiKiemDuyet, String> mong = Map.of(
                KiemDuyetRepository.LoaiLoiKiemDuyet.DA_CO_QUYET_DINH, "DA_CO_QUYET_DINH",
                KiemDuyetRepository.LoaiLoiKiemDuyet.PHIEN_BAN_DA_CU, "PHIEN_BAN_DA_CU",
                KiemDuyetRepository.LoaiLoiKiemDuyet.TIN_DA_XOA, "TIN_DA_XOA",
                KiemDuyetRepository.LoaiLoiKiemDuyet.TIN_DA_BI_GO, "TIN_DA_BI_GO",
                KiemDuyetRepository.LoaiLoiKiemDuyet.XUNG_DOT_MA_Y_DINH, "MA_Y_DINH_KHAC_NOI_DUNG"
        );
        int stt = 0;
        for (Map.Entry<KiemDuyetRepository.LoaiLoiKiemDuyet, String> muc : mong.entrySet()) {
            khoGia.loiPhaiNem = muc.getKey();
            mockMvc.perform(yeuCauPost(duongDanQuyetDinh("approve"), kdv.rawSessionCookie(), "loi-" + stt++, "\"7\"", null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.maLoi").value(muc.getValue()));
        }
        khoGia.loiPhaiNem = KiemDuyetRepository.LoaiLoiKiemDuyet.PHIEN_BAN_KHONG_THUOC_TIN;
        mockMvc.perform(yeuCauPost(duongDanQuyetDinh("approve"), kdv.rawSessionCookie(), "loi-404", "\"7\"", null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.maLoi").value("KHONG_TIM_THAY"));
        khoGia.loiPhaiNem = KiemDuyetRepository.LoaiLoiKiemDuyet.TIN_KHONG_TON_TAI;
        mockMvc.perform(yeuCauPost(duongDanQuyetDinh("reject"), kdv.rawSessionCookie(), "loi-404b", "\"7\"", Map.of("lyDo", "x")))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- tiện ích

    private String duongDanChiTiet() {
        return "/api/moderation/listings/" + tinId + "/versions/" + phienBanId;
    }

    private String duongDanQuyetDinh(String hanhDong) {
        return duongDanChiTiet() + "/" + hanhDong;
    }

    private MockHttpServletRequestBuilder yeuCauGet(String duongDan, String rawSessionCookie) {
        MockHttpServletRequestBuilder builder = get(duongDan).accept(MediaType.APPLICATION_JSON);
        if (rawSessionCookie != null) {
            builder.cookie(new Cookie(cookieConfig.getSessionCookieName(), rawSessionCookie));
        }
        return builder;
    }

    private MockHttpServletRequestBuilder yeuCauPost(
            String duongDan,
            String rawSessionCookie,
            String idempotencyKey,
            String ifMatch,
            Map<String, Object> body
    ) {
        MockHttpServletRequestBuilder builder = post(duongDan).accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(ChuanHoaJson.toJsonString(body));
        }
        builder.header(HttpHeaders.ORIGIN, ORIGIN);
        if (idempotencyKey != null) {
            builder.header(TinDangService.HEADER_IDEMPOTENCY_KEY, idempotencyKey);
        }
        if (ifMatch != null) {
            builder.header(HttpHeaders.IF_MATCH, ifMatch);
        }
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

    private Fixture taoTaiKhoan(VaiTroTaiKhoan vaiTro) {
        Instant now = Instant.now();
        TaiKhoan taiKhoan = TaiKhoan.khoiPhucTuLuuTru(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "kiem-duyet-" + UUID.randomUUID() + "@bds-test.local",
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

    private record Fixture(TaiKhoan taiKhoan, String rawSessionCookie) {
    }

    /** Kho giả: ba mục hàng chờ cố định, một phiên bản có phienBanCapNhat = 7, quyết định mô phỏng điều kiện If-Match. */
    private final class KhoGia implements KiemDuyetRepository {

        int soLanQuyetDinh;
        DuLieuQuyetDinh duLieuCuoi;
        LoaiLoiKiemDuyet loiPhaiNem;

        @Override
        public List<MucHangCho> docHangCho(ConTroHangCho sau, int gioiHan) {
            List<MucHangCho> tatCa = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                tatCa.add(new MucHangCho(
                        UUID.randomUUID(),
                        new UUID(0L, i + 1L),
                        1,
                        false,
                        "Tin " + i,
                        "ban",
                        "can_ho",
                        "TP. Hồ Chí Minh",
                        new BigDecimal("2500000000"),
                        "vnd_tong",
                        new BigDecimal("68.5"),
                        "dat",
                        GOC.plusSeconds(i),
                        chuId
                ));
            }
            List<MucHangCho> ketQua = new ArrayList<>();
            for (MucHangCho muc : tatCa) {
                boolean saCon = sau == null
                        || muc.guiLuc().isAfter(sau.guiLuc())
                        || (muc.guiLuc().equals(sau.guiLuc()) && muc.phienBanId().toString().compareTo(sau.phienBanId().toString()) > 0);
                if (saCon && ketQua.size() < gioiHan) {
                    ketQua.add(muc);
                }
            }
            return ketQua;
        }

        @Override
        public Optional<ChiTietPhienBanResponse> docChiTietPhienBan(UUID tin, UUID phienBan) {
            if (!tinId.equals(tin) || !phienBanId.equals(phienBan)) {
                return Optional.empty();
            }
            UUID phienBanCu = UUID.randomUUID();
            return Optional.of(new ChiTietPhienBanResponse(
                    tinId,
                    chuId,
                    "cho_kiem_duyet",
                    CAP_NHAT_HIEN_TAI,
                    phienBanId,
                    phienBanCu,
                    true,
                    noiDung(phienBanId, 2, "cho_kiem_duyet"),
                    noiDung(phienBanCu, 1, "da_duyet")
            ));
        }

        private NoiDungPhienBan noiDung(UUID id, int soPhienBan, String ketQua) {
            return new NoiDungPhienBan(
                    id, soPhienBan, "Tiêu đề", "Mô tả", "ban", "can_ho", new BigDecimal("2500000000"), "vnd_tong",
                    new BigDecimal("68.5"), "12 Tôn Đức Thắng", "TP. Hồ Chí Minh", new BigDecimal("10.7769"),
                    new BigDecimal("106.7009"), "dat", ketQua, null, null, null, GOC,
                    List.of(new AnhPhienBan(UUID.randomUUID(), 1, "anh/hien-thi-1.png"))
            );
        }

        @Override
        public Optional<KetQuaYeuCauQuyetDinh> timYeuCauQuyetDinh(UUID taiKhoanId, String maYDinh) {
            return Optional.empty();
        }

        @Override
        public KetQuaQuyetDinh quyetDinhTrongTransaction(DuLieuQuyetDinh duLieu) {
            if (loiPhaiNem != null) {
                throw new LoiNghiepVuKiemDuyetException(loiPhaiNem, "Lỗi giả lập " + loiPhaiNem);
            }
            if (duLieu.phienBanCapNhatDaXem() != CAP_NHAT_HIEN_TAI) {
                throw new LoiNghiepVuKiemDuyetException(LoaiLoiKiemDuyet.PHIEN_BAN_CU, "Tin đã thay đổi.");
            }
            soLanQuyetDinh++;
            duLieuCuoi = duLieu;
            boolean duyet = KET_QUA_DA_DUYET.equals(duLieu.ketQua());
            return new KetQuaQuyetDinh(
                    duLieu.tinId(),
                    duLieu.phienBanId(),
                    duLieu.ketQua(),
                    duyet ? "da_duyet" : "bi_tu_choi",
                    duLieu.nguoiDuyetId(),
                    duLieu.thoiDiem(),
                    duyet ? null : duLieu.lyDo(),
                    CAP_NHAT_HIEN_TAI + 1
            );
        }
    }
}
