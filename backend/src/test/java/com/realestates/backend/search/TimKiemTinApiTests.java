package com.realestates.backend.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.realestates.backend.listing.domain.LoaiBds;
import com.realestates.backend.listing.domain.LoaiGiaoDich;
import com.realestates.backend.listing.exception.ListingExceptionHandler;
import com.realestates.backend.search.application.TimKiemTinService;
import com.realestates.backend.search.controller.TimKiemTinController;
import com.realestates.backend.search.domain.BoLocTimKiem;
import com.realestates.backend.search.domain.ConTroTimKiem;
import com.realestates.backend.search.domain.KieuGia;
import com.realestates.backend.search.domain.SapXepTin;
import com.realestates.backend.search.domain.TimKiemRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

/** API tìm kiếm qua MockMvc độc lập (không DB) với kho giả: khách được phép, gán tham số, lỗi 400 theo trường, header. */
class TimKiemTinApiTests {

    private static final String ORIGIN = "https://localhost";
    private static final String CSRF_SECRET = "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM";
    private static final Instant GOC = Instant.parse("2026-11-18T03:00:00Z");

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
        TimKiemTinService service = new TimKiemTinService(permissionAndCsrfService, khoGia);
        mockMvc = MockMvcBuilders.standaloneSetup(new TimKiemTinController(service, cookieConfig))
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
    @DisplayName("Khách không cookie nhận 200, no-store, Vary Cookie; có khối taiTro và tuNhien")
    void khachDuocPhep() throws Exception {
        mockMvc.perform(yeuCauGet("/api/listings", null))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(jsonPath("$.taiTro.length()").value(1))
                .andExpect(jsonPath("$.taiTro[0].laTaiTro").value(true))
                .andExpect(jsonPath("$.tuNhien.length()").value(3))
                .andExpect(jsonPath("$.tuNhien[0].laTaiTro").value(false))
                .andExpect(jsonPath("$.tuNhien[0].giaTongChuanVnd").value(2500000000.0))
                .andExpect(jsonPath("$.tuNhien[0].giaM2ChuanVnd").value(36496350.36))
                .andExpect(jsonPath("$.tuNhien[0].anhDaiDien").value("anh/hien-thi-1.png"))
                .andExpect(jsonPath("$.conTroTiepTheo").doesNotExist());
        assertThat(khoGia.boLocCuoi.kieuGia()).isEqualTo(KieuGia.TONG);
        assertThat(khoGia.sapXepCuoi).isEqualTo(SapXepTin.MOI_NHAT);
        assertThat(khoGia.gioiHanCuoi).isEqualTo(TimKiemTinService.GIOI_HAN_MAC_DINH + 1);
    }

    @Test
    @DisplayName("Cookie phiên rác hoặc người dùng đăng nhập đều vẫn là 200")
    void cookieRacVaNguoiDung() throws Exception {
        mockMvc.perform(yeuCauGet("/api/listings", "cookie-rac-khong-ton-tai"))
                .andExpect(status().isOk());
        Fixture nguoiDung = taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        mockMvc.perform(yeuCauGet("/api/listings", nguoiDung.rawSessionCookie()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Tham số query được gán đúng vào bộ lọc, sắp xếp và giới hạn (loaiBds lặp lại hoặc phân tách dấu phẩy)")
    void ganThamSo() throws Exception {
        mockMvc.perform(yeuCauGet(
                        "/api/listings?loaiGiaoDich=cho_thue&loaiBds=can_ho&loaiBds=dat,phong_tro&khuVuc= Quan 1 "
                                + "&giaTu=1000000&giaDen=2500000.50&kieuGia=m2&dienTichTu=20&dienTichDen=80.5"
                                + "&sapXep=gia_giam&limit=7",
                        null))
                .andExpect(status().isOk());
        BoLocTimKiem boLoc = khoGia.boLocCuoi;
        assertThat(boLoc.loaiGiaoDich()).isEqualTo(LoaiGiaoDich.CHO_THUE);
        assertThat(boLoc.loaiBds()).containsExactly(LoaiBds.CAN_HO, LoaiBds.DAT, LoaiBds.PHONG_TRO);
        assertThat(boLoc.khuVuc()).isEqualTo("Quan 1");
        assertThat(boLoc.kieuGia()).isEqualTo(KieuGia.M2);
        assertThat(boLoc.giaTu()).isEqualByComparingTo("1000000");
        assertThat(boLoc.giaDen()).isEqualByComparingTo("2500000.50");
        assertThat(boLoc.dienTichTu()).isEqualByComparingTo("20");
        assertThat(boLoc.dienTichDen()).isEqualByComparingTo("80.5");
        assertThat(khoGia.sapXepCuoi).isEqualTo(SapXepTin.GIA_GIAM);
        assertThat(khoGia.gioiHanCuoi).isEqualTo(8);
        assertThat(khoGia.soLanDocTaiTro).isEqualTo(1);
    }

    @Test
    @DisplayName("Phân trang: có conTroTiepTheo khi còn dữ liệu; trang có cursor không gọi khối tài trợ và cursor mang đúng khóa")
    void phanTrangVaCursor() throws Exception {
        String trang1 = mockMvc.perform(yeuCauGet("/api/listings?limit=2", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tuNhien.length()").value(2))
                .andExpect(jsonPath("$.conTroTiepTheo").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String conTro = JsonPath.read(trang1, "$.conTroTiepTheo");
        ConTroTimKiem giaiMa = ConTroTimKiem.giaiMa(conTro).orElseThrow();
        assertThat(giaiMa.sapXep()).isEqualTo(SapXepTin.MOI_NHAT);
        assertThat(giaiMa.tinId()).isEqualTo(new UUID(0L, 2L));
        assertThat(khoGia.soLanDocTaiTro).isEqualTo(1);

        mockMvc.perform(yeuCauGet("/api/listings?limit=2&cursor=" + conTro, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taiTro.length()").value(0));
        assertThat(khoGia.soLanDocTaiTro).isEqualTo(1);
        assertThat(khoGia.conTroCuoi).isNotNull();
        assertThat(khoGia.conTroCuoi.tinId()).isEqualTo(new UUID(0L, 2L));
    }

    @Test
    @DisplayName("Cursor: sai định dạng, khác sapXep hoặc khác bộ lọc nhận 400 trường cursor")
    void cursorKhongHopLe() throws Exception {
        String trang1 = mockMvc.perform(yeuCauGet("/api/listings?limit=2&khuVuc=A", null))
                .andReturn().getResponse().getContentAsString();
        String conTro = JsonPath.read(trang1, "$.conTroTiepTheo");

        for (String xau : List.of("khong-phai-con-tro", conTro + "x", conTro.substring(0, conTro.length() - 3))) {
            mockMvc.perform(yeuCauGet("/api/listings?limit=2&khuVuc=A&cursor=" + xau, null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"))
                    .andExpect(jsonPath("$.truongLoi[0].truong").value("cursor"));
        }
        mockMvc.perform(yeuCauGet("/api/listings?limit=2&khuVuc=B&cursor=" + conTro, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truongLoi[0].truong").value("cursor"));
        mockMvc.perform(yeuCauGet("/api/listings?limit=2&khuVuc=A&sapXep=cu_nhat&cursor=" + conTro, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truongLoi[0].truong").value("cursor"));
    }

    @Test
    @DisplayName("400 với lỗi theo trường cho enum lạ, số sai, đảo khoảng, limit ngoài 1..50")
    void loiDuLieu() throws Exception {
        kiemTra400("/api/listings?loaiGiaoDich=mua", "loaiGiaoDich");
        kiemTra400("/api/listings?loaiBds=can_ho,biet_thu", "loaiBds");
        kiemTra400("/api/listings?kieuGia=nghin", "kieuGia");
        kiemTra400("/api/listings?sapXep=ngau_nhien", "sapXep");
        kiemTra400("/api/listings?dienTichTu=-5", "dienTichTu");
        kiemTra400("/api/listings?dienTichDen=abc", "dienTichDen");
        kiemTra400("/api/listings?dienTichTu=1e3", "dienTichTu");
        kiemTra400("/api/listings?dienTichTu=50&dienTichDen=40", "dienTichTu");
        kiemTra400("/api/listings?loaiGiaoDich=ban&giaTu=-1", "giaTu");
        kiemTra400("/api/listings?loaiGiaoDich=ban&giaTu=200&giaDen=100", "giaTu");
        kiemTra400("/api/listings?loaiGiaoDich=ban&giaDen=NaN", "giaDen");
        for (String limit : List.of("0", "51", "abc", "-3", "1.5")) {
            kiemTra400("/api/listings?limit=" + limit, "limit");
        }
        assertThat(khoGia.soLanDocTuNhien).isZero();
    }

    @Test
    @DisplayName("Lọc hoặc sắp xếp theo giá mà thiếu loaiGiaoDich nhận 400 trường loaiGiaoDich; có loaiGiaoDich thì 200")
    void giaCanLoaiGiaoDich() throws Exception {
        kiemTra400("/api/listings?giaTu=100", "loaiGiaoDich");
        kiemTra400("/api/listings?giaDen=100&kieuGia=m2", "loaiGiaoDich");
        kiemTra400("/api/listings?sapXep=gia_tang", "loaiGiaoDich");
        kiemTra400("/api/listings?sapXep=gia_giam", "loaiGiaoDich");
        mockMvc.perform(yeuCauGet("/api/listings?loaiGiaoDich=ban&sapXep=gia_tang&giaTu=100", null))
                .andExpect(status().isOk());
        mockMvc.perform(yeuCauGet("/api/listings?sapXep=dien_tich_tang&dienTichTu=10", null))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Nhiều lỗi được trả cùng lúc trong truongLoi")
    void nhieuLoiCungLuc() throws Exception {
        mockMvc.perform(yeuCauGet("/api/listings?limit=0&sapXep=x&loaiGiaoDich=mua", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truongLoi.length()").value(3));
    }

    private void kiemTra400(String duongDan, String truongDau) throws Exception {
        mockMvc.perform(yeuCauGet(duongDan, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"))
                .andExpect(jsonPath("$.truongLoi[0].truong").value(truongDau));
    }

    private MockHttpServletRequestBuilder yeuCauGet(String duongDan, String rawSessionCookie) {
        MockHttpServletRequestBuilder builder = get(duongDan).accept(MediaType.APPLICATION_JSON);
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
                "tim-kiem-" + UUID.randomUUID() + "@bds-test.local",
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

    /** Kho giả: một tin tài trợ và ba tin tự nhiên cố định; ghi lại tham số lần gọi cuối. */
    private static final class KhoGia implements TimKiemRepository {

        int soLanDocTaiTro;
        int soLanDocTuNhien;
        BoLocTimKiem boLocCuoi;
        SapXepTin sapXepCuoi;
        ConTroTimKiem conTroCuoi;
        int gioiHanCuoi;

        @Override
        public List<DongTin> docTaiTro(BoLocTimKiem boLoc, int toiDa) {
            soLanDocTaiTro++;
            boLocCuoi = boLoc;
            return List.of(dong(9L, null));
        }

        @Override
        public List<DongTin> docTuNhien(
                BoLocTimKiem boLoc,
                SapXepTin sapXep,
                ConTroTimKiem sau,
                int toiDaTaiTro,
                int gioiHan
        ) {
            soLanDocTuNhien++;
            boLocCuoi = boLoc;
            sapXepCuoi = sapXep;
            conTroCuoi = sau;
            gioiHanCuoi = gioiHan;
            List<DongTin> tatCa = new ArrayList<>();
            for (long i = 1; i <= 3; i++) {
                tatCa.add(dong(i, "2026-11-18 03:00:0" + (4 - i) + "+00"));
            }
            return tatCa.subList(0, Math.min(gioiHan, tatCa.size()));
        }

        private static DongTin dong(long so, String khoa) {
            return new DongTin(
                    new UUID(0L, so),
                    "Tin " + so,
                    "ban",
                    "can_ho",
                    "TP. Hồ Chí Minh",
                    "12 Tôn Đức Thắng",
                    new BigDecimal("2500000000"),
                    "vnd_tong",
                    new BigDecimal("68.5"),
                    "anh/hien-thi-1.png",
                    GOC.minusSeconds(so),
                    khoa
            );
        }
    }
}
