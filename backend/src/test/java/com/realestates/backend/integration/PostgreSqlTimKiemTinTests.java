package com.realestates.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.auth.application.AuthBrowserSessionLifecyclePort;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.database.TestDatabaseSettings;
import com.realestates.backend.domain.model.PriceConverter;
import com.realestates.backend.listing.application.QuanLyTinService;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.domain.DonViGiaTin;
import com.realestates.backend.search.TimKiemPgTestSupport;
import com.realestates.backend.search.TimKiemPgTestSupport.NoiDung;
import com.realestates.backend.search.TimKiemPgTestSupport.TinTao;
import com.realestates.backend.search.application.TimKiemTinService;
import com.realestates.backend.search.domain.SapXepTin;
import com.realestates.backend.search.domain.TimKiemRepository;
import com.realestates.backend.search.dto.KetQuaTimKiemResponse;
import com.realestates.backend.search.dto.ThamSoTimKiem;
import com.realestates.backend.search.dto.TinTimKiemItem;
import com.realestates.backend.search.infrastructure.BieuThucGiaSql;
import com.realestates.backend.search.infrastructure.PostgreSqlTimKiemRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = "app.repository.type=postgres")
@ContextConfiguration(initializers = TestDatabaseSettings.SpringInitializer.class)
class PostgreSqlTimKiemTinTests {

    private static final Instant GOC = Instant.parse("2026-11-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TaiKhoanRepository taiKhoanRepository;

    @Autowired
    private PhienXacThucRepository phienXacThucRepository;

    @Autowired
    private AnhTinRepository anhTinRepository;

    @Autowired
    private TimKiemRepository timKiemRepository;

    @Autowired
    private TinDangService tinDangService;

    @Autowired
    private QuanLyTinService quanLyTinService;

    @Autowired
    private TimKiemTinService timKiemTinService;

    @Autowired
    private AuthBrowserSessionLifecyclePort sessionLifecyclePort;

    private TimKiemPgTestSupport ht;
    /** Khu vực duy nhất cho mỗi test (JUnit tạo instance mới cho mỗi test) để cô lập khỏi dữ liệu khác. */
    private final String kv = "TKTEST-" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assertThat(timKiemRepository).isInstanceOf(PostgreSqlTimKiemRepository.class);
        ht = new TimKiemPgTestSupport(
                jdbcTemplate,
                transactionManager,
                taiKhoanRepository,
                phienXacThucRepository,
                anhTinRepository,
                tinDangService,
                quanLyTinService,
                sessionLifecyclePort
        );
    }

    @AfterEach
    void donDep() {
        ht.donDep();
    }

    // ------------------------------------------------------------------ tiện ích

    private static final class Tham {
        private final Map<String, String> giaTri = new LinkedHashMap<>();
        private List<String> loaiBds;

        Tham(String khuVuc) {
            giaTri.put("khuVuc", khuVuc);
        }

        Tham p(String ten, String gia) {
            giaTri.put(ten, gia);
            return this;
        }

        Tham loaiBds(String... cacGiaTri) {
            this.loaiBds = List.of(cacGiaTri);
            return this;
        }

        Tham sao() {
            Tham ban = new Tham(null);
            ban.giaTri.putAll(giaTri);
            ban.loaiBds = loaiBds;
            return ban;
        }

        ThamSoTimKiem build() {
            return new ThamSoTimKiem(
                    giaTri.get("loaiGiaoDich"), loaiBds, giaTri.get("khuVuc"), giaTri.get("giaTu"), giaTri.get("giaDen"),
                    giaTri.get("kieuGia"), giaTri.get("dienTichTu"), giaTri.get("dienTichDen"), giaTri.get("sapXep"),
                    giaTri.get("cursor"), giaTri.get("limit")
            );
        }
    }

    private Tham tham() {
        return new Tham(kv);
    }

    private KetQuaTimKiemResponse tim(Tham t) {
        return timKiemTinService.timKiem(null, t.build());
    }

    private static List<UUID> ids(List<TinTimKiemItem> items) {
        return items.stream().map(TinTimKiemItem::tinId).toList();
    }

    private static Set<UUID> tap(TinTao... tins) {
        Set<UUID> ketQua = new HashSet<>();
        for (TinTao tin : tins) {
            ketQua.add(tin.tinId());
        }
        return ketQua;
    }

    private static List<UUID> danhSach(TinTao... tins) {
        List<UUID> ketQua = new ArrayList<>();
        for (TinTao tin : tins) {
            ketQua.add(tin.tinId());
        }
        return ketQua;
    }

    private void kiemTraTuNhien(Tham t, TinTao... mongDoi) {
        KetQuaTimKiemResponse kq = tim(t.sao().p("limit", "50"));
        assertThat(ids(kq.tuNhien()))
                .as("ketQua tuNhien cho %s", t.giaTri)
                .containsExactlyInAnyOrderElementsOf(tap(mongDoi));
    }

    private static void kiemTraLoi(ThrowingCallable viec, String truong) {
        assertThatThrownBy(viec)
                .isInstanceOf(AuthValidationException.class)
                .satisfies(ex -> assertThat(((AuthValidationException) ex).getTruongLoi())
                        .extracting(FieldErrorItem::truong)
                        .contains(truong));
    }

    /** Duyệt hết các trang bằng conTroTiepTheo, trả toàn bộ id tự nhiên theo thứ tự nhận được. */
    private List<UUID> duyetHetCacTrang(Tham goc, int limit) {
        List<UUID> tatCa = new ArrayList<>();
        String cursor = null;
        int baoVe = 0;
        do {
            Tham t = goc.sao().p("limit", String.valueOf(limit));
            if (cursor != null) {
                t.p("cursor", cursor);
            }
            KetQuaTimKiemResponse kq = tim(t);
            assertThat(kq.tuNhien().size()).isLessThanOrEqualTo(limit);
            tatCa.addAll(ids(kq.tuNhien()));
            cursor = kq.conTroTiepTheo();
        } while (cursor != null && ++baoVe < 200);
        assertThat(baoVe).as("khong lap vo han").isLessThan(200);
        return tatCa;
    }

    // ------------------------------------------------------------------ bộ lọc

    /** A,B bán; C..G cho thuê; mỗi tin một đơn vị giá khác nhau. */
    private record BoDuLieuLoc(TinTao a, TinTao b, TinTao c, TinTao d, TinTao e, TinTao f, TinTao g) {
    }

    private BoDuLieuLoc taoBoDuLieuLoc() {
        NoiDung mau = NoiDung.banMau(kv);
        return new BoDuLieuLoc(
                ht.taoTinDaDuyet(mau.voiTieuDe("A").voiBan("can_ho", "2000000000", "vnd_tong", "50"), GOC.plusSeconds(1)),
                ht.taoTinDaDuyet(mau.voiTieuDe("B").voiBan("nha_rieng", "50000000", "vnd_m2", "100"), GOC.plusSeconds(2)),
                ht.taoTinDaDuyet(mau.voiTieuDe("C").voiThue("phong_tro", "5000000", "vnd_thang", "25"), GOC.plusSeconds(3)),
                ht.taoTinDaDuyet(mau.voiTieuDe("D").voiThue("can_ho", "120000000", "vnd_nam", "40"), GOC.plusSeconds(4)),
                ht.taoTinDaDuyet(mau.voiTieuDe("E").voiThue("nha_rieng", "300000", "vnd_ngay", "60"), GOC.plusSeconds(5)),
                ht.taoTinDaDuyet(mau.voiTieuDe("F").voiThue("dat", "10000", "vnd_m2_ngay", "30"), GOC.plusSeconds(6)),
                ht.taoTinDaDuyet(mau.voiTieuDe("G").voiThue("phong_tro", "120000", "vnd_m2_thang", "70"), GOC.plusSeconds(7))
        );
    }

    @Test
    @DisplayName("Từng bộ lọc: loaiGiaoDich, loaiBds (lặp và dấu phẩy), khuVuc không phân biệt hoa thường, diện tích; kèm đủ trường kết quả")
    void locTheoTungBoLoc() {
        BoDuLieuLoc d = taoBoDuLieuLoc();
        TinTao khac = ht.taoTinDaDuyet(NoiDung.banMau(kv + "-KHAC").voiTieuDe("Khu khac"), GOC);

        kiemTraTuNhien(tham(), d.a, d.b, d.c, d.d, d.e, d.f, d.g);
        kiemTraTuNhien(new Tham(kv + "-KHAC"), khac);
        kiemTraTuNhien(new Tham("  " + kv.toLowerCase() + "  "), d.a, d.b, d.c, d.d, d.e, d.f, d.g);
        kiemTraTuNhien(new Tham(kv.substring(0, kv.length() - 1)));

        kiemTraTuNhien(tham().p("loaiGiaoDich", "ban"), d.a, d.b);
        kiemTraTuNhien(tham().p("loaiGiaoDich", "cho_thue"), d.c, d.d, d.e, d.f, d.g);

        kiemTraTuNhien(tham().loaiBds("can_ho"), d.a, d.d);
        kiemTraTuNhien(tham().loaiBds("can_ho,phong_tro"), d.a, d.d, d.c, d.g);
        kiemTraTuNhien(tham().loaiBds("dat", "nha_rieng"), d.b, d.e, d.f);
        kiemTraTuNhien(tham().loaiBds("can_ho").p("loaiGiaoDich", "cho_thue"), d.d);

        kiemTraTuNhien(tham().p("dienTichTu", "40"), d.a, d.b, d.d, d.e, d.g);
        kiemTraTuNhien(tham().p("dienTichDen", "50"), d.a, d.c, d.d, d.f);
        kiemTraTuNhien(tham().p("dienTichTu", "40").p("dienTichDen", "50"), d.a, d.d);
        kiemTraTuNhien(tham().p("dienTichTu", "40.5").p("dienTichDen", "49.99"));

        TinTimKiemItem muc = tim(tham().loaiBds("can_ho").p("loaiGiaoDich", "ban").p("limit", "50"))
                .tuNhien().get(0);
        assertThat(muc.tinId()).isEqualTo(d.a.tinId());
        assertThat(muc.tieuDe()).isEqualTo("A");
        assertThat(muc.loaiGiaoDich()).isEqualTo("ban");
        assertThat(muc.loaiBds()).isEqualTo("can_ho");
        assertThat(muc.khuVuc()).isEqualTo(kv);
        assertThat(muc.diaChi()).isEqualTo("18 Lý Tự Trọng, Quận 1");
        assertThat(muc.giaGoc()).isEqualByComparingTo("2000000000");
        assertThat(muc.donViGia()).isEqualTo("vnd_tong");
        assertThat(muc.giaTongChuanVnd()).isEqualByComparingTo("2000000000.00");
        assertThat(muc.giaM2ChuanVnd()).isEqualByComparingTo("40000000.00");
        assertThat(muc.dienTichM2()).isEqualByComparingTo("50");
        assertThat(muc.anhDaiDien()).isEqualTo(ht.khoaAnhHienThi(d.a.phienBanId())).isNotBlank();
        assertThat(muc.ngayDang()).isEqualTo(GOC.plusSeconds(1));
        assertThat(muc.laTaiTro()).isFalse();
    }

    @Test
    @DisplayName("Lọc giá cho bán (tổng và m2) và cho thuê đủ năm đơn vị, biên khoảng gồm cả hai đầu")
    void locTheoGia() {
        BoDuLieuLoc d = taoBoDuLieuLoc();
        // Bán: A tổng 2e9, m2 40e6; B tổng 5e9 (50e6 x 100), m2 50e6.
        Tham ban = tham().p("loaiGiaoDich", "ban");
        kiemTraTuNhien(ban.sao().p("giaTu", "3000000000"), d.b);
        kiemTraTuNhien(ban.sao().p("giaDen", "2000000000"), d.a);
        kiemTraTuNhien(ban.sao().p("giaTu", "2000000000").p("giaDen", "5000000000"), d.a, d.b);
        kiemTraTuNhien(ban.sao().p("kieuGia", "m2").p("giaTu", "45000000"), d.b);
        kiemTraTuNhien(ban.sao().p("kieuGia", "m2").p("giaDen", "40000000"), d.a);
        kiemTraTuNhien(ban.sao().p("kieuGia", "m2").p("giaTu", "40000000.01").p("giaDen", "49999999.99"));

        // Thuê: tổng/tháng C 5e6, D 10e6, E 9e6, F 9e6, G 8.4e6; m2/tháng C 200000, D 250000, E 150000, F 300000, G 120000.
        Tham thue = tham().p("loaiGiaoDich", "cho_thue");
        kiemTraTuNhien(thue.sao().p("giaTu", "9000000").p("giaDen", "10000000"), d.d, d.e, d.f);
        kiemTraTuNhien(thue.sao().p("giaTu", "8400000").p("giaDen", "8400000"), d.g);
        kiemTraTuNhien(thue.sao().p("giaDen", "5000000"), d.c);
        kiemTraTuNhien(thue.sao().p("kieuGia", "m2").p("giaTu", "200000"), d.c, d.d, d.f);
        kiemTraTuNhien(thue.sao().p("kieuGia", "m2").p("giaDen", "150000"), d.e, d.g);
        kiemTraTuNhien(thue.sao().p("kieuGia", "m2").p("giaTu", "150000").p("giaDen", "250000"), d.c, d.d, d.e);

        Map<UUID, TinTimKiemItem> theoId = new HashMap<>();
        for (TinTimKiemItem item : tim(thue.sao().p("limit", "50")).tuNhien()) {
            theoId.put(item.tinId(), item);
        }
        assertThat(theoId.get(d.d.tinId()).giaTongChuanVnd()).isEqualByComparingTo("10000000.00");
        assertThat(theoId.get(d.d.tinId()).giaM2ChuanVnd()).isEqualByComparingTo("250000.00");
        assertThat(theoId.get(d.f.tinId()).giaTongChuanVnd()).isEqualByComparingTo("9000000.00");
        assertThat(theoId.get(d.f.tinId()).giaM2ChuanVnd()).isEqualByComparingTo("300000.00");
    }

    @Test
    @DisplayName("Dữ liệu sai nhận 400 theo trường: enum lạ, số âm hoặc không phải số, tu > den, limit ngoài 1..50, cursor hỏng, giá thiếu loaiGiaoDich")
    void loi400() {
        kiemTraLoi(() -> tim(tham().p("loaiGiaoDich", "mua")), "loaiGiaoDich");
        kiemTraLoi(() -> tim(tham().loaiBds("can_ho", "biet_thu")), "loaiBds");
        kiemTraLoi(() -> tim(tham().p("kieuGia", "nghin")), "kieuGia");
        kiemTraLoi(() -> tim(tham().p("sapXep", "ngau_nhien")), "sapXep");
        kiemTraLoi(() -> tim(tham().p("dienTichTu", "-1")), "dienTichTu");
        kiemTraLoi(() -> tim(tham().p("dienTichDen", "abc")), "dienTichDen");
        kiemTraLoi(() -> tim(tham().p("dienTichTu", "10").p("dienTichDen", "9")), "dienTichTu");
        kiemTraLoi(() -> tim(tham().p("loaiGiaoDich", "ban").p("giaTu", "-5")), "giaTu");
        kiemTraLoi(() -> tim(tham().p("loaiGiaoDich", "ban").p("giaDen", "1e9")), "giaDen");
        kiemTraLoi(() -> tim(tham().p("loaiGiaoDich", "ban").p("giaTu", "10").p("giaDen", "9")), "giaTu");
        kiemTraLoi(() -> tim(tham().p("limit", "0")), "limit");
        kiemTraLoi(() -> tim(tham().p("limit", "51")), "limit");
        kiemTraLoi(() -> tim(tham().p("limit", "x")), "limit");
        kiemTraLoi(() -> tim(tham().p("cursor", "khong-phai-con-tro")), "cursor");
        kiemTraLoi(() -> tim(tham().p("giaTu", "100")), "loaiGiaoDich");
        kiemTraLoi(() -> tim(tham().p("giaDen", "100").p("kieuGia", "m2")), "loaiGiaoDich");
        kiemTraLoi(() -> tim(tham().p("sapXep", "gia_tang")), "loaiGiaoDich");
        kiemTraLoi(() -> tim(tham().p("sapXep", "gia_giam")), "loaiGiaoDich");
        assertThat(tim(tham().p("limit", "50")).tuNhien()).isEmpty();
        assertThat(tim(tham().p("limit", "1")).tuNhien()).isEmpty();
    }

    // ------------------------------------------------------------------ sắp xếp và phân trang

    private record BoNamTin(TinTao l1, TinTao l2, TinTao l3, TinTao l4, TinTao l5) {
    }

    private BoNamTin taoNamTinKhacNhau() {
        NoiDung mau = NoiDung.banMau(kv);
        return new BoNamTin(
                ht.taoTinDaDuyet(mau.voiTieuDe("L1").voiGia("3000000000").voiDienTich("70"), GOC.plusSeconds(60 - 4)),
                ht.taoTinDaDuyet(mau.voiTieuDe("L2").voiGia("1000000000").voiDienTich("90"), GOC.plusSeconds(60 - 1)),
                ht.taoTinDaDuyet(mau.voiTieuDe("L3").voiGia("2000000000").voiDienTich("50"), GOC.plusSeconds(60 - 3)),
                ht.taoTinDaDuyet(mau.voiTieuDe("L4").voiGia("5000000000").voiDienTich("30"), GOC.plusSeconds(60 - 2)),
                ht.taoTinDaDuyet(mau.voiTieuDe("L5").voiGia("4000000000").voiDienTich("10"), GOC.plusSeconds(60 - 5))
        );
    }

    @Test
    @DisplayName("Sáu cách sắp xếp (giá theo tổng và theo m2) cho đúng thứ tự")
    void saoSapXep() {
        BoNamTin n = taoNamTinKhacNhau();
        Tham co = tham().p("loaiGiaoDich", "ban").p("limit", "50");

        assertThat(ids(tim(co.sao()).tuNhien())).isEqualTo(danhSach(n.l2, n.l4, n.l3, n.l1, n.l5));
        assertThat(ids(tim(co.sao().p("sapXep", "moi_nhat")).tuNhien())).isEqualTo(danhSach(n.l2, n.l4, n.l3, n.l1, n.l5));
        assertThat(ids(tim(co.sao().p("sapXep", "cu_nhat")).tuNhien())).isEqualTo(danhSach(n.l5, n.l1, n.l3, n.l4, n.l2));
        // Tổng giá: L2 1e9, L3 2e9, L1 3e9, L5 4e9, L4 5e9
        assertThat(ids(tim(co.sao().p("sapXep", "gia_tang")).tuNhien())).isEqualTo(danhSach(n.l2, n.l3, n.l1, n.l5, n.l4));
        assertThat(ids(tim(co.sao().p("sapXep", "gia_giam")).tuNhien())).isEqualTo(danhSach(n.l4, n.l5, n.l1, n.l3, n.l2));
        // Giá/m2: L2 11.1M, L3 40M, L1 42.9M, L4 166.7M, L5 400M
        assertThat(ids(tim(co.sao().p("sapXep", "gia_tang").p("kieuGia", "m2")).tuNhien()))
                .isEqualTo(danhSach(n.l2, n.l3, n.l1, n.l4, n.l5));
        assertThat(ids(tim(co.sao().p("sapXep", "gia_giam").p("kieuGia", "m2")).tuNhien()))
                .isEqualTo(danhSach(n.l5, n.l4, n.l1, n.l3, n.l2));
        // Diện tích: L5 10, L4 30, L3 50, L1 70, L2 90
        assertThat(ids(tim(co.sao().p("sapXep", "dien_tich_tang")).tuNhien())).isEqualTo(danhSach(n.l5, n.l4, n.l3, n.l1, n.l2));
        assertThat(ids(tim(co.sao().p("sapXep", "dien_tich_giam")).tuNhien())).isEqualTo(danhSach(n.l2, n.l1, n.l3, n.l4, n.l5));
    }

    @Test
    @DisplayName("Phân trang với ngay_dang, giá, diện tích trùng nhau: không trùng lặp, không sót và bằng một trang lớn cho cả sáu cách sắp xếp")
    void phanTrangOnDinhKhiTrungKhoa() {
        NoiDung mau = NoiDung.banMau(kv);
        int tong = 18;
        for (int i = 0; i < tong; i++) {
            NoiDung nd;
            Instant ngay;
            if (i < 12) {
                nd = mau.voiTieuDe("P" + i).voiGia(String.valueOf((1 + i % 3) * 1_000_000_000L)).voiDienTich(String.valueOf(40 + (i % 4) * 10));
                ngay = GOC.plusSeconds((i % 3) * 86_400L);
            } else {
                // Sáu tin giống hệt nhau ở mọi khóa: chỉ t.id phân biệt.
                nd = mau.voiTieuDe("P" + i).voiGia("1000000000").voiDienTich("40");
                ngay = GOC;
            }
            ht.taoTinDaDuyet(nd, ngay);
        }

        Map<String, Comparator<TinTimKiemItem>> thuTu = new LinkedHashMap<>();
        thuTu.put("moi_nhat|tong", Comparator.comparing(TinTimKiemItem::ngayDang).reversed());
        thuTu.put("cu_nhat|tong", Comparator.comparing(TinTimKiemItem::ngayDang));
        thuTu.put("gia_tang|tong", Comparator.comparing(TinTimKiemItem::giaTongChuanVnd));
        thuTu.put("gia_giam|tong", Comparator.comparing(TinTimKiemItem::giaTongChuanVnd).reversed());
        thuTu.put("gia_tang|m2", Comparator.comparing(TinTimKiemItem::giaM2ChuanVnd));
        thuTu.put("gia_giam|m2", Comparator.comparing(TinTimKiemItem::giaM2ChuanVnd).reversed());
        thuTu.put("dien_tich_tang|tong", Comparator.comparing(TinTimKiemItem::dienTichM2));
        thuTu.put("dien_tich_giam|tong", Comparator.comparing(TinTimKiemItem::dienTichM2).reversed());

        for (Map.Entry<String, Comparator<TinTimKiemItem>> muc : thuTu.entrySet()) {
            String[] phan = muc.getKey().split("\\|");
            Tham co = tham().p("loaiGiaoDich", "ban").p("sapXep", phan[0]).p("kieuGia", phan[1]);

            KetQuaTimKiemResponse lon = tim(co.sao().p("limit", "50"));
            assertThat(lon.conTroTiepTheo()).as(muc.getKey()).isNull();
            List<UUID> trangLon = ids(lon.tuNhien());
            assertThat(trangLon).as(muc.getKey()).hasSize(tong).doesNotHaveDuplicates();
            for (int i = 1; i < lon.tuNhien().size(); i++) {
                assertThat(muc.getValue().compare(lon.tuNhien().get(i - 1), lon.tuNhien().get(i)))
                        .as("%s: vi tri %d", muc.getKey(), i).isLessThanOrEqualTo(0);
            }
            for (int limit : new int[] {1, 5, 7, 18}) {
                List<UUID> tungTrang = duyetHetCacTrang(co, limit);
                assertThat(tungTrang).as("%s limit=%d", muc.getKey(), limit).doesNotHaveDuplicates().isEqualTo(trangLon);
            }
        }
    }

    @Test
    @DisplayName("Cursor dùng lại với bộ lọc, kieuGia hoặc sapXep khác bị 400 trường cursor; cursor bị sửa hoặc cắt cụt bị 400; limit và cách viết khuVuc không ảnh hưởng")
    void cursorChiDungDuocVoiCungBoLoc() {
        NoiDung mau = NoiDung.banMau(kv);
        for (int i = 1; i <= 4; i++) {
            ht.taoTinDaDuyet(mau.voiTieuDe("C" + i).voiGia(i + "000000000"), GOC.plusSeconds(i));
        }
        Tham goc = tham().p("loaiGiaoDich", "ban").p("sapXep", "gia_tang").p("limit", "2");
        KetQuaTimKiemResponse trang1 = tim(goc);
        String cursor = trang1.conTroTiepTheo();
        assertThat(cursor).isNotBlank();
        assertThat(tim(goc.sao().p("cursor", cursor)).tuNhien()).hasSize(2);

        // Không đổi bộ lọc dù limit khác hoặc khuVuc khác hoa thường/khoảng trắng
        assertThat(tim(goc.sao().p("cursor", cursor).p("limit", "3")).tuNhien()).hasSize(2);
        assertThat(tim(goc.sao().p("cursor", cursor).p("khuVuc", " " + kv.toLowerCase() + " ")).tuNhien()).hasSize(2);

        kiemTraLoi(() -> tim(goc.sao().p("cursor", cursor).p("kieuGia", "m2")), "cursor");
        kiemTraLoi(() -> tim(goc.sao().p("cursor", cursor).p("khuVuc", kv + "-x")), "cursor");
        kiemTraLoi(() -> tim(goc.sao().p("cursor", cursor).p("sapXep", "gia_giam")), "cursor");
        kiemTraLoi(() -> tim(goc.sao().p("cursor", cursor).p("giaTu", "1")), "cursor");
        kiemTraLoi(() -> tim(goc.sao().p("cursor", cursor).p("dienTichDen", "999")), "cursor");
        kiemTraLoi(() -> tim(goc.sao().p("cursor", cursor).loaiBds("can_ho")), "cursor");
        kiemTraLoi(() -> tim(goc.sao().p("cursor", cursor).p("loaiGiaoDich", "cho_thue")), "cursor");

        String giaiMa = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        String[] phan = giaiMa.split("\\|", -1);
        phan[2] = phan[2].substring(0, phan[2].length() - 1) + (phan[2].endsWith("0") ? "1" : "0");
        String biSua = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.join("|", phan).getBytes(StandardCharsets.UTF_8));
        kiemTraLoi(() -> tim(goc.sao().p("cursor", biSua)), "cursor");
        kiemTraLoi(() -> tim(goc.sao().p("cursor", cursor.substring(0, cursor.length() - 4))), "cursor");
        kiemTraLoi(() -> tim(goc.sao().p("cursor", cursor.substring(2))), "cursor");
        kiemTraLoi(() -> tim(goc.sao().p("cursor", "!!!@@@")), "cursor");
    }

    // ------------------------------------------------------------------ hiển thị công khai

    @Test
    @DisplayName("Chờ duyệt lần đầu, bị từ chối, đã xóa (luồng thật), bị gỡ vi phạm đều ẩn; tin đã duyệt hiện")
    void tinKhongDuocHienThi() {
        NoiDung mau = NoiDung.banMau(kv);
        TinTao hienThi = ht.taoTinDaDuyet(mau.voiTieuDe("Hien thi"), GOC);
        TinTao choDuyet = ht.taoTinChoDuyet(mau.voiTieuDe("Cho duyet"));
        TinTao biTuChoi = ht.taoTinChoDuyet(mau.voiTieuDe("Bi tu choi"));
        ht.tuChoiPhienBanHienTai(biTuChoi.tinId());
        TinTao daXoa = ht.taoTinDaDuyet(mau.voiTieuDe("Da xoa"), GOC);
        ht.xoaTin(daXoa);
        TinTao biGo = ht.taoTinDaDuyet(mau.voiTieuDe("Bi go"), GOC);
        ht.goViPham(biGo.tinId());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT trang_thai FROM se214.tin_bat_dong_san WHERE id = ?", String.class, daXoa.tinId()))
                .isEqualTo("da_xoa");
        kiemTraTuNhien(tham(), hienThi);
        kiemTraTuNhien(tham().p("loaiGiaoDich", "ban").p("sapXep", "gia_tang"), hienThi);
    }

    @Test
    @DisplayName("Tin đang chờ duyệt bản sửa vẫn hiện với nội dung đã duyệt CŨ; bộ lọc áp dụng lên giá trị cũ")
    void tinCoBanSuaChoDuyetVanHienNoiDungCu() {
        TinTao tin = ht.taoTinDaDuyet(
                NoiDung.banMau(kv).voiTieuDe("Tieu de cu").voiLoaiBds("can_ho").voiGia("2000000000").voiDienTich("50"),
                GOC
        );
        ht.suaTin(tin, NoiDung.banMau(kv).voiTieuDe("Tieu de moi").voiLoaiBds("dat").voiGia("9000000000").voiDienTich("500"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT trang_thai FROM se214.tin_bat_dong_san WHERE id = ?", String.class, tin.tinId()))
                .isEqualTo("cho_kiem_duyet");
        assertThat(ht.phienBanHienTai(tin.tinId())).isNotEqualTo(ht.phienBanDaDuyet(tin.tinId()));

        List<TinTimKiemItem> ketQua = tim(tham().p("limit", "50")).tuNhien();
        assertThat(ketQua).hasSize(1);
        TinTimKiemItem muc = ketQua.get(0);
        assertThat(muc.tinId()).isEqualTo(tin.tinId());
        assertThat(muc.tieuDe()).isEqualTo("Tieu de cu");
        assertThat(muc.loaiBds()).isEqualTo("can_ho");
        assertThat(muc.giaGoc()).isEqualByComparingTo("2000000000");
        assertThat(muc.dienTichM2()).isEqualByComparingTo("50");
        assertThat(muc.anhDaiDien()).isEqualTo(ht.khoaAnhHienThi(ht.phienBanDaDuyet(tin.tinId())));

        // Lọc theo giá trị MỚI (chưa duyệt) không khớp; theo giá trị CŨ thì khớp
        kiemTraTuNhien(tham().loaiBds("dat"));
        kiemTraTuNhien(tham().loaiBds("can_ho"), tin);
        kiemTraTuNhien(tham().p("loaiGiaoDich", "ban").p("giaTu", "8000000000"));
        kiemTraTuNhien(tham().p("loaiGiaoDich", "ban").p("giaDen", "3000000000"), tin);
        kiemTraTuNhien(tham().p("dienTichTu", "400"));
        kiemTraTuNhien(tham().p("dienTichDen", "60"), tin);
        assertThat(ids(tim(tham().p("loaiGiaoDich", "ban").p("sapXep", "gia_giam")).tuNhien())).containsExactly(tin.tinId());
        assertThat(tim(tham().p("loaiGiaoDich", "ban").p("sapXep", "gia_giam")).tuNhien().get(0).giaTongChuanVnd())
                .isEqualByComparingTo("2000000000.00");
    }

    // ------------------------------------------------------------------ khối tài trợ

    private record BoTaiTro(List<TinTao> taiTro, List<TinTao> thuong, List<TinTao> khongDuDieuKien, TinTao biGo) {
    }

    private BoTaiTro taoBoTaiTro() {
        NoiDung mau = NoiDung.banMau(kv);
        List<TinTao> taiTro = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            TinTao tin = ht.taoTinDaDuyet(mau.voiTieuDe("S" + i).voiGia(i + "000000000"), GOC.plusSeconds(i * 10L));
            ht.taoGoiDangHoatDong(tin.tinId());
            taiTro.add(tin);
        }
        List<TinTao> thuong = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            thuong.add(ht.taoTinDaDuyet(mau.voiTieuDe("N" + i), GOC.plusSeconds(1000L + i)));
        }
        List<TinTao> khongDuDieuKien = new ArrayList<>();
        Instant qua = Instant.now().minusSeconds(3600);
        TinTao hetHanTheoGio = ht.taoTinDaDuyet(mau.voiTieuDe("X1 het gio"), GOC.plusSeconds(2001));
        ht.taoGoi(hetHanTheoGio.tinId(), "dang_hoat_dong", qua, "0 seconds");
        TinTao tamDung = ht.taoTinDaDuyet(mau.voiTieuDe("X2 tam dung"), GOC.plusSeconds(2002));
        ht.taoGoi(tamDung.tinId(), "tam_dung_cho_duyet", null, "2 days");
        TinTao hetHan = ht.taoTinDaDuyet(mau.voiTieuDe("X3 het han"), GOC.plusSeconds(2003));
        ht.taoGoi(hetHan.tinId(), "het_han", qua, "0 seconds");
        TinTao ketThuc = ht.taoTinDaDuyet(mau.voiTieuDe("X4 ket thuc"), GOC.plusSeconds(2004));
        ht.taoGoi(ketThuc.tinId(), "ket_thuc_do_go_tin", null, "0 seconds");
        khongDuDieuKien.addAll(List.of(hetHanTheoGio, tamDung, hetHan, ketThuc));

        TinTao biGo = ht.taoTinDaDuyet(mau.voiTieuDe("G1 bi go"), GOC.plusSeconds(5));
        ht.taoGoiDangHoatDong(biGo.tinId());
        ht.goViPham(biGo.tinId());
        return new BoTaiTro(taiTro, thuong, khongDuDieuKien, biGo);
    }

    @Test
    @DisplayName("Tài trợ: 7 tin đủ điều kiện -> đúng 5 tin cũ nhất; không lặp trong tự nhiên ở mọi trang; gói hết hạn/tạm dừng/kết thúc và tin bị gỡ không được tài trợ; có cursor thì không có khối tài trợ")
    void khoiTaiTro() {
        BoTaiTro d = taoBoTaiTro();
        List<TinTao> nam = d.taiTro.subList(0, 5);
        Set<UUID> tuNhienMongDoi = new HashSet<>(tap(d.taiTro.get(5), d.taiTro.get(6)));
        tuNhienMongDoi.addAll(tap(d.thuong.toArray(new TinTao[0])));
        tuNhienMongDoi.addAll(tap(d.khongDuDieuKien.toArray(new TinTao[0])));
        assertThat(tuNhienMongDoi).hasSize(9);

        KetQuaTimKiemResponse trang = tim(tham().p("limit", "50"));
        assertThat(ids(trang.taiTro())).isEqualTo(danhSach(nam.toArray(new TinTao[0])));
        assertThat(trang.taiTro()).allMatch(TinTimKiemItem::laTaiTro);
        assertThat(trang.tuNhien()).noneMatch(TinTimKiemItem::laTaiTro);
        assertThat(new HashSet<>(ids(trang.tuNhien()))).isEqualTo(tuNhienMongDoi);
        assertThat(ids(trang.tuNhien())).doesNotContain(d.biGo.tinId());
        assertThat(ids(trang.taiTro())).doesNotContain(d.biGo.tinId());

        // Phân trang limit=3: trang 1 có tài trợ, trang 2+ không; tự nhiên ở mọi trang loại đúng 5 tin tài trợ
        List<UUID> tatCa = new ArrayList<>();
        String cursor = null;
        int soTrang = 0;
        do {
            Tham t = tham().p("limit", "3");
            if (cursor != null) {
                t.p("cursor", cursor);
            }
            KetQuaTimKiemResponse kq = tim(t);
            if (soTrang == 0) {
                assertThat(ids(kq.taiTro())).isEqualTo(danhSach(nam.toArray(new TinTao[0])));
            } else {
                assertThat(kq.taiTro()).as("trang %d khong co tai tro", soTrang + 1).isEmpty();
            }
            tatCa.addAll(ids(kq.tuNhien()));
            cursor = kq.conTroTiepTheo();
            soTrang++;
        } while (cursor != null && soTrang < 20);
        assertThat(soTrang).isEqualTo(3);
        assertThat(tatCa).doesNotHaveDuplicates();
        assertThat(new HashSet<>(tatCa)).isEqualTo(tuNhienMongDoi);
        assertThat(tatCa).doesNotContainAnyElementsOf(danhSach(nam.toArray(new TinTao[0])));
    }

    @Test
    @DisplayName("Tài trợ tôn trọng bộ lọc: chọn 5 tin cũ nhất TRONG số tin khớp lọc, phần còn lại ở danh sách tự nhiên")
    void taiTroTonTrongBoLoc() {
        BoTaiTro d = taoBoTaiTro();
        TinTao s1 = d.taiTro.get(0);
        TinTao s2 = d.taiTro.get(1);
        TinTao s3 = d.taiTro.get(2);
        TinTao s4 = d.taiTro.get(3);
        TinTao s5 = d.taiTro.get(4);
        TinTao s6 = d.taiTro.get(5);
        TinTao s7 = d.taiTro.get(6);
        Tham ban = tham().p("loaiGiaoDich", "ban").p("limit", "50");

        // Giá S_i = i tỷ; N và X giá 2 tỷ.
        KetQuaTimKiemResponse ketQua = tim(ban.sao().p("giaTu", "3000000000"));
        assertThat(ids(ketQua.taiTro())).isEqualTo(danhSach(s3, s4, s5, s6, s7));
        assertThat(ketQua.tuNhien()).isEmpty();

        ketQua = tim(ban.sao().p("giaDen", "5000000000"));
        assertThat(ids(ketQua.taiTro())).isEqualTo(danhSach(s1, s2, s3, s4, s5));
        Set<UUID> mongDoi = new HashSet<>(tap(d.thuong.toArray(new TinTao[0])));
        mongDoi.addAll(tap(d.khongDuDieuKien.toArray(new TinTao[0])));
        assertThat(new HashSet<>(ids(ketQua.tuNhien()))).isEqualTo(mongDoi);

        ketQua = tim(ban.sao().p("giaTu", "2000000000").p("giaDen", "6000000000"));
        assertThat(ids(ketQua.taiTro())).isEqualTo(danhSach(s2, s3, s4, s5, s6));
        assertThat(ids(ketQua.tuNhien())).doesNotContainAnyElementsOf(danhSach(s2, s3, s4, s5, s6));
        assertThat(new HashSet<>(ids(ketQua.tuNhien()))).isEqualTo(mongDoi);

        // Sắp xếp người dùng chọn không đổi thứ tự tài trợ (cũ nhất trước)
        ketQua = tim(ban.sao().p("sapXep", "gia_giam"));
        assertThat(ids(ketQua.taiTro())).isEqualTo(danhSach(s1, s2, s3, s4, s5));
        assertThat(ids(ketQua.tuNhien()).subList(0, 2)).containsExactly(s7.tinId(), s6.tinId());
    }

    @Test
    @DisplayName("Tài trợ lọc theo loaiBds; ít hơn 5 tin đủ điều kiện thì trả đúng số đó")
    void taiTroTheoLoaiBds() {
        NoiDung mau = NoiDung.banMau(kv);
        List<TinTao> dat = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            TinTao tin = ht.taoTinDaDuyet(mau.voiTieuDe("D" + i).voiLoaiBds("dat"), GOC.plusSeconds(i));
            ht.taoGoiDangHoatDong(tin.tinId());
            dat.add(tin);
        }
        List<TinTao> canHo = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            TinTao tin = ht.taoTinDaDuyet(mau.voiTieuDe("H" + i).voiLoaiBds("can_ho"), GOC.plusSeconds(10 + i));
            ht.taoGoiDangHoatDong(tin.tinId());
            canHo.add(tin);
        }
        KetQuaTimKiemResponse theoDat = tim(tham().loaiBds("dat").p("limit", "50"));
        assertThat(ids(theoDat.taiTro())).isEqualTo(danhSach(dat.toArray(new TinTao[0])));
        assertThat(theoDat.tuNhien()).isEmpty();

        KetQuaTimKiemResponse theoCanHo = tim(tham().loaiBds("can_ho").p("limit", "50"));
        assertThat(ids(theoCanHo.taiTro())).isEqualTo(danhSach(canHo.toArray(new TinTao[0])));
        assertThat(theoCanHo.tuNhien()).isEmpty();
    }

    // ------------------------------------------------------------------ bảy đơn vị giá: SQL = PriceConverter

    @ParameterizedTest(name = "SQL = PriceConverter cho {0}")
    @EnumSource(DonViGiaTin.class)
    @DisplayName("Biểu thức SQL cho tổng giá và giá/m2 bằng PriceConverter ở cả bảy đơn vị, với số liệu khó làm tròn")
    void sqlBangPriceConverter(DonViGiaTin donVi) {
        List<String> cacGia = List.of(
                "100", "1", "99999999.99", "123456789.125", "0.005", "7777777.775", "1000000000.5", "333.33", "0.01"
        );
        List<String> cacDienTich = List.of("33.333", "3", "0.07", "100.01", "85", "7.777", "12.5", "0.5", "33.3333333");
        PriceConverter.DonViGia donViJava = TimKiemTinService.chuyenDonVi(donVi);
        String sql = "SELECT " + BieuThucGiaSql.giaTong("p") + " AS tong, " + BieuThucGiaSql.giaM2("p") + " AS m2 "
                + "FROM (SELECT CAST(? AS numeric) AS gia_goc, CAST(? AS text) AS don_vi_gia, "
                + "CAST(? AS numeric) AS dien_tich_m2) p";
        int soCa = 0;
        for (String gia : cacGia) {
            for (String dienTich : cacDienTich) {
                BigDecimal[] sqlKetQua = jdbcTemplate.queryForObject(
                        sql,
                        (rs, rowNum) -> new BigDecimal[] {rs.getBigDecimal("tong"), rs.getBigDecimal("m2")},
                        gia, donVi.getGiaTri(), dienTich
                );
                PriceConverter.KetQuaQuyDoiGia java = PriceConverter.quyDoi(
                        new BigDecimal(gia), donViJava, new BigDecimal(dienTich)
                );
                String ca = donVi.getGiaTri() + " gia=" + gia + " dienTich=" + dienTich;
                assertThat(sqlKetQua).as(ca).isNotNull();
                assertThat(sqlKetQua[0].toPlainString()).as("tong " + ca).isEqualTo(java.giaTongChuanVnd().toPlainString());
                assertThat(sqlKetQua[1].toPlainString()).as("m2 " + ca).isEqualTo(java.giaM2ChuanVnd().toPlainString());
                soCa++;
            }
        }
        assertThat(soCa).isEqualTo(cacGia.size() * cacDienTich.size());
    }

    @Test
    @DisplayName("Ca khó cụ thể: vnd_nam 100 trên 33.333 m2, vnd_m2_ngay làm tròn giá/m2/tháng trước khi nhân diện tích")
    void caKhoCuThe() {
        String sql = "SELECT " + BieuThucGiaSql.giaTong("p") + " AS tong, " + BieuThucGiaSql.giaM2("p") + " AS m2 "
                + "FROM (SELECT CAST(? AS numeric) AS gia_goc, CAST(? AS text) AS don_vi_gia, "
                + "CAST(? AS numeric) AS dien_tich_m2) p";
        Function<String[], Map<String, Object>> chay = tham -> jdbcTemplate.queryForMap(sql, tham[0], tham[1], tham[2]);
        // vnd_nam 100: tổng 100/12 = 8.33; m2 100/(12*33.333)=0.25 (0.250002...)
        Map<String, Object> nam = chay.apply(new String[] {"100", "vnd_nam", "33.333"});
        assertThat((BigDecimal) nam.get("tong")).isEqualByComparingTo("8.33");
        assertThat((BigDecimal) nam.get("m2")).isEqualByComparingTo("0.25");
        // vnd_m2_ngay 0.0333 -> giá/m2/tháng round(0.999,2)=1.00; tổng round(1.00*85,2)=85.00 (không phải 0.999*85=84.915)
        Map<String, Object> m2Ngay = chay.apply(new String[] {"0.0333", "vnd_m2_ngay", "85"});
        assertThat((BigDecimal) m2Ngay.get("m2")).isEqualByComparingTo("1.00");
        assertThat((BigDecimal) m2Ngay.get("tong")).isEqualByComparingTo("85.00");
        // Làm tròn nửa lên: 1 / 8 = 0.125 -> 0.13
        Map<String, Object> nua = chay.apply(new String[] {"1", "vnd_tong", "8"});
        assertThat((BigDecimal) nua.get("m2")).isEqualByComparingTo("0.13");
    }

    @Test
    @DisplayName("Hằng sắp xếp được dùng đầy đủ: mọi SapXepTin đều chạy được trên PostgreSQL")
    void moiCachSapXepDeuChay() {
        for (SapXepTin sapXep : SapXepTin.values()) {
            Tham t = tham().p("loaiGiaoDich", "ban").p("sapXep", sapXep.getGiaTri()).p("limit", "5");
            assertThat(tim(t).tuNhien()).isEmpty();
            assertThat(tim(t.sao().p("kieuGia", "m2")).tuNhien()).isEmpty();
        }
    }
}
