package com.realestates.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.auth.application.AuthBrowserSessionLifecyclePort;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.InsufficientPermissionException;
import com.realestates.backend.auth.exception.UnauthenticatedSessionException;
import com.realestates.backend.database.TestDatabaseSettings;
import com.realestates.backend.detail.application.ChiTietTinService;
import com.realestates.backend.detail.domain.ChiTietTinRepository;
import com.realestates.backend.detail.dto.ChiTietTinResponse;
import com.realestates.backend.detail.dto.LienHeChuTinResponse;
import com.realestates.backend.detail.infrastructure.PostgreSqlChiTietTinRepository;
import com.realestates.backend.domain.model.PriceConverter;
import com.realestates.backend.favorite.application.YeuThichService;
import com.realestates.backend.favorite.domain.YeuThichRepository;
import com.realestates.backend.favorite.dto.DanhSachYeuThichResponse;
import com.realestates.backend.favorite.dto.YeuThichItem;
import com.realestates.backend.favorite.infrastructure.PostgreSqlYeuThichRepository;
import com.realestates.backend.listing.application.QuanLyTinService;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.exception.ListingNotFoundException;
import com.realestates.backend.search.TimKiemPgTestSupport;
import com.realestates.backend.search.TimKiemPgTestSupport.NoiDung;
import com.realestates.backend.search.TimKiemPgTestSupport.TaiKhoanSession;
import com.realestates.backend.search.TimKiemPgTestSupport.TinTao;
import com.realestates.backend.search.application.TimKiemTinService;
import com.realestates.backend.search.dto.KetQuaTimKiemResponse;
import com.realestates.backend.search.dto.ThamSoTimKiem;
import com.realestates.backend.search.dto.TinTimKiemItem;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;

/** Chi tiết tin, liên hệ chủ tin và yêu thích trên PostgreSQL thật; dựng dữ liệu bằng luồng thật qua TimKiemPgTestSupport. */
@SpringBootTest(properties = "app.repository.type=postgres")
@ContextConfiguration(initializers = TestDatabaseSettings.SpringInitializer.class)
class PostgreSqlChiTietYeuThichTests {

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
    private TinDangService tinDangService;
    @Autowired
    private QuanLyTinService quanLyTinService;
    @Autowired
    private AuthBrowserSessionLifecyclePort sessionLifecyclePort;
    @Autowired
    private ChiTietTinRepository chiTietTinRepository;
    @Autowired
    private YeuThichRepository yeuThichRepository;
    @Autowired
    private ChiTietTinService chiTietTinService;
    @Autowired
    private YeuThichService yeuThichService;
    @Autowired
    private TimKiemTinService timKiemTinService;

    private TimKiemPgTestSupport ht;
    /** Khu vực duy nhất cho mỗi test để cô lập khỏi dữ liệu khác. */
    private final String kv = "CTTEST-" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assertThat(chiTietTinRepository).isInstanceOf(PostgreSqlChiTietTinRepository.class);
        assertThat(yeuThichRepository).isInstanceOf(PostgreSqlYeuThichRepository.class);
        ht = new TimKiemPgTestSupport(
                jdbcTemplate, transactionManager, taiKhoanRepository, phienXacThucRepository, anhTinRepository,
                tinDangService, quanLyTinService, sessionLifecyclePort
        );
    }

    @AfterEach
    void donDep() {
        ht.donDep();
    }

    // ------------------------------------------------------------------ tiện ích

    private TinTao tinDaDuyet() {
        return ht.taoTinDaDuyet(NoiDung.banMau(kv), GOC);
    }

    private ChiTietTinResponse chiTiet(String cookie, TinTao tin) {
        return chiTietTinService.chiTiet(cookie, tin.tinId().toString());
    }

    private static void phaiLaKhongTimThay(ThrowingCallable viec) {
        assertThatThrownBy(viec).isInstanceOf(ListingNotFoundException.class);
    }

    private void luu(TaiKhoanSession nguoi, TinTao tin) {
        yeuThichService.luu(nguoi.rawCookie(), tin.tinId().toString());
    }

    private void bo(TaiKhoanSession nguoi, TinTao tin) {
        yeuThichService.bo(nguoi.rawCookie(), tin.tinId().toString());
    }

    private DanhSachYeuThichResponse danhSach(TaiKhoanSession nguoi, String cursor, String limit) {
        return yeuThichService.danhSach(nguoi.rawCookie(), cursor, limit);
    }

    private static List<UUID> ids(List<YeuThichItem> items) {
        return items.stream().map(YeuThichItem::tinId).toList();
    }

    private static void kiemTraLoiTruong(ThrowingCallable viec, String truong) {
        assertThatThrownBy(viec)
                .isInstanceOf(AuthValidationException.class)
                .satisfies(ex -> assertThat(((AuthValidationException) ex).getTruongLoi())
                        .extracting(FieldErrorItem::truong)
                        .contains(truong));
    }

    /** Dựng các tin không công khai: chờ duyệt, bị từ chối, đã xóa, bị gỡ. */
    private record TinAn(TinTao choDuyet, TinTao biTuChoi, TinTao daXoa, TinTao biGo) {
        List<TinTao> tatCa() {
            return List.of(choDuyet, biTuChoi, daXoa, biGo);
        }
    }

    private TinAn dungTinAn() {
        TinTao choDuyet = ht.taoTinChoDuyet(NoiDung.banMau(kv));
        TinTao biTuChoi = ht.taoTinChoDuyet(NoiDung.banMau(kv));
        ht.tuChoiPhienBanHienTai(biTuChoi.tinId());
        TinTao daXoa = tinDaDuyet();
        ht.xoaTin(daXoa);
        TinTao biGo = tinDaDuyet();
        ht.goViPham(biGo.tinId());
        return new TinAn(choDuyet, biTuChoi, daXoa, biGo);
    }

    // ------------------------------------------------------------------ chi tiết

    @Test
    @DisplayName("Chi tiết tin công khai trả đủ trường, giá quy đổi đúng, ảnh phiên bản đã duyệt")
    void chiTietDayDu() {
        NoiDung noiDung = NoiDung.banMau(kv).voiBan("can_ho", "30000000", "vnd_m2", "50");
        TinTao tin = ht.taoTinDaDuyet(noiDung, GOC);

        ChiTietTinResponse kq = chiTiet(null, tin);

        assertThat(kq.tinId()).isEqualTo(tin.tinId());
        assertThat(kq.tieuDe()).isEqualTo("Căn hộ 2 phòng ngủ view sông");
        assertThat(kq.moTa()).isEqualTo("Kết cấu 1 trệt 3 lầu, đường rộng 8m.");
        assertThat(kq.loaiGiaoDich()).isEqualTo("ban");
        assertThat(kq.loaiBds()).isEqualTo("can_ho");
        assertThat(kq.giaGoc()).isEqualByComparingTo("30000000");
        assertThat(kq.donViGia()).isEqualTo("vnd_m2");
        assertThat(kq.giaTongChuanVnd()).isEqualByComparingTo("1500000000.00");
        assertThat(kq.giaM2ChuanVnd()).isEqualByComparingTo("30000000.00");
        assertThat(kq.dienTichM2()).isEqualByComparingTo("50");
        assertThat(kq.diaChi()).isEqualTo("18 Lý Tự Trọng, Quận 1");
        assertThat(kq.khuVuc()).isEqualTo(kv);
        assertThat(kq.viDo()).isEqualByComparingTo("10.7772");
        assertThat(kq.kinhDo()).isEqualByComparingTo("106.7015");
        assertThat(kq.ngayDang()).isEqualTo(GOC);
        assertThat(kq.anh()).hasSize(1);
        assertThat(kq.anh().get(0).thuTu()).isEqualTo(1);
        assertThat(kq.anh().get(0).khoaAnhHienThi()).isEqualTo(ht.khoaAnhHienThi(tin.phienBanId()));
        assertThat(kq.daLuu()).isNull();
        assertThat(kq.laTaiTro()).isFalse();
    }

    @Test
    @DisplayName("Giá thuê theo năm được quy đổi giống PriceConverter")
    void chiTietGiaThue() {
        NoiDung noiDung = NoiDung.banMau(kv).voiThue("nha_rieng", "120000000", "vnd_nam", "37.5");
        TinTao tin = ht.taoTinDaDuyet(noiDung, GOC);

        ChiTietTinResponse kq = chiTiet(null, tin);

        PriceConverter.KetQuaQuyDoiGia mong = PriceConverter.quyDoi(
                new BigDecimal("120000000"), PriceConverter.DonViGia.VND_NAM, new BigDecimal("37.5"));
        assertThat(kq.giaTongChuanVnd()).isEqualByComparingTo(mong.giaTongChuanVnd());
        assertThat(kq.giaM2ChuanVnd()).isEqualByComparingTo(mong.giaM2ChuanVnd());
    }

    @Test
    @DisplayName("Ảnh trả về theo thứ tự thu_tu tăng dần của phiên bản đã duyệt")
    void chiTietAnhDungThuTu() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TinTao tin = ht.taoTinChoDuyet(chu, NoiDung.banMau(kv), 3);
        ht.duyetPhienBanHienTai(tin.tinId());

        List<Map<String, Object>> mong = jdbcTemplate.queryForList(
                "SELECT thu_tu, khoa_anh_hien_thi FROM se214.anh_tin WHERE phien_ban_tin_id = ? "
                        + "AND trang_thai = 'da_gan' ORDER BY thu_tu",
                tin.phienBanId()
        );
        ChiTietTinResponse kq = chiTiet(null, tin);

        assertThat(mong).hasSize(3);
        assertThat(kq.anh()).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(kq.anh().get(i).thuTu()).isEqualTo(((Number) mong.get(i).get("thu_tu")).intValue());
            assertThat(kq.anh().get(i).khoaAnhHienThi()).isEqualTo(mong.get(i).get("khoa_anh_hien_thi"));
        }
        assertThat(kq.anh()).extracting(a -> a.thuTu()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("Tin chờ duyệt, bị từ chối, đã xóa, bị gỡ, id ngẫu nhiên hoặc sai định dạng đều 404")
    void chiTietKhongCongKhai() {
        TinAn an = dungTinAn();
        for (TinTao tin : an.tatCa()) {
            phaiLaKhongTimThay(() -> chiTiet(null, tin));
        }
        phaiLaKhongTimThay(() -> chiTietTinService.chiTiet(null, UUID.randomUUID().toString()));
        phaiLaKhongTimThay(() -> chiTietTinService.chiTiet(null, "khong-phai-uuid"));
        phaiLaKhongTimThay(() -> chiTietTinService.chiTiet(null, "1-1-1-1-1"));
    }

    @Test
    @DisplayName("Tin có bản sửa chờ duyệt vẫn hiện nội dung cũ; duyệt bản sửa thì hiện nội dung mới")
    void chiTietBanSuaChoDuyet() {
        TinTao tin = tinDaDuyet();
        ht.suaTin(tin, NoiDung.banMau(kv).voiTieuDe("Tiêu đề mới chờ duyệt"));

        assertThat(chiTiet(null, tin).tieuDe()).isEqualTo("Căn hộ 2 phòng ngủ view sông");

        ht.duyetPhienBanHienTai(tin.tinId());
        assertThat(chiTiet(null, tin).tieuDe()).isEqualTo("Tiêu đề mới chờ duyệt");
    }

    @Test
    @DisplayName("daLuu: true/false cho người dùng đã đăng nhập, null cho khách, cookie rác và vai trò khác")
    void chiTietDaLuu() {
        TinTao tin = tinDaDuyet();
        TaiKhoanSession daLuu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession chuaLuu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kiemDuyet = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TaiKhoanSession quanTri = ht.taoTaiKhoan(VaiTroTaiKhoan.QUAN_TRI_VIEN);
        luu(daLuu, tin);

        assertThat(chiTiet(daLuu.rawCookie(), tin).daLuu()).isTrue();
        assertThat(chiTiet(chuaLuu.rawCookie(), tin).daLuu()).isFalse();
        assertThat(chiTiet(null, tin).daLuu()).isNull();
        assertThat(chiTiet("cookie-rac-khong-ton-tai", tin).daLuu()).isNull();
        assertThat(chiTiet(kiemDuyet.rawCookie(), tin).daLuu()).isNull();
        assertThat(chiTiet(quanTri.rawCookie(), tin).daLuu()).isNull();
    }

    @Test
    @DisplayName("laTaiTro true khi có gói đang hoạt động còn hạn, false khi gói hết hạn")
    void chiTietTaiTro() {
        TinTao taiTro = tinDaDuyet();
        TinTao hetHan = tinDaDuyet();
        ht.taoGoiDangHoatDong(taiTro.tinId());
        ht.taoGoi(hetHan.tinId(), "het_han", Instant.now().minusSeconds(60), "0 seconds");

        assertThat(chiTiet(null, taiTro).laTaiTro()).isTrue();
        assertThat(chiTiet(null, hetHan).laTaiTro()).isFalse();
    }

    @Test
    @DisplayName("Phản hồi chi tiết và liên hệ không có chuId hay email")
    void khongLoChuHayEmail() {
        Set<String> chiTietTruong = new HashSet<>();
        for (RecordComponent c : ChiTietTinResponse.class.getRecordComponents()) {
            chiTietTruong.add(c.getName().toLowerCase());
        }
        assertThat(chiTietTruong).noneMatch(t -> t.contains("chu") && t.contains("id") || t.contains("email"));
        assertThat(Arrays.stream(LienHeChuTinResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("hoTen", "soDienThoai");
    }

    // ------------------------------------------------------------------ liên hệ

    @Test
    @DisplayName("Liên hệ trả họ tên và số điện thoại của chủ tin, không có email")
    void lienHeCoDuLieu() {
        TinTao tin = tinDaDuyet();
        ht.datHoTenVaSoDienThoai(tin.chu().taiKhoan().getId(), "Nguyễn Văn Chủ", "0912345678");

        LienHeChuTinResponse kq = chiTietTinService.lienHe(null, tin.tinId().toString());

        assertThat(kq.hoTen()).isEqualTo("Nguyễn Văn Chủ");
        assertThat(kq.soDienThoai()).isEqualTo("0912345678");
        assertThat(kq.toString()).doesNotContain(ht.emailTaiKhoan(tin.chu().taiKhoan().getId()));
    }

    @Test
    @DisplayName("Liên hệ: họ tên/số điện thoại rỗng trong DB trả null")
    void lienHeTruongNull() {
        TinTao tin = tinDaDuyet();
        ht.datHoTenVaSoDienThoai(tin.chu().taiKhoan().getId(), null, null);

        LienHeChuTinResponse kq = chiTietTinService.lienHe(null, tin.tinId().toString());

        assertThat(kq.hoTen()).isNull();
        assertThat(kq.soDienThoai()).isNull();
    }

    @Test
    @DisplayName("Chủ tin bị khóa: liên hệ 404 nhưng chi tiết và tìm kiếm vẫn thấy tin")
    void lienHeChuBiKhoa() {
        TinTao tin = tinDaDuyet();
        ht.khoaTaiKhoan(tin.chu().taiKhoan().getId());

        phaiLaKhongTimThay(() -> docLienHe(tin));
        assertThat(chiTiet(null, tin).tinId()).isEqualTo(tin.tinId());
        KetQuaTimKiemResponse timKiem = timKiemTinService.timKiem(
                null, new ThamSoTimKiem(null, null, kv, null, null, null, null, null, null, null, "50"));
        assertThat(timKiem.tuNhien()).extracting(TinTimKiemItem::tinId).contains(tin.tinId());
    }

    private LienHeChuTinResponse docLienHe(TinTao tin) {
        return chiTietTinService.lienHe(null, tin.tinId().toString());
    }

    @Test
    @DisplayName("Liên hệ của tin không công khai, id ngẫu nhiên hoặc sai định dạng đều 404")
    void lienHeKhongCongKhai() {
        TinAn an = dungTinAn();
        for (TinTao tin : an.tatCa()) {
            phaiLaKhongTimThay(() -> docLienHe(tin));
        }
        phaiLaKhongTimThay(() -> chiTietTinService.lienHe(null, UUID.randomUUID().toString()));
        phaiLaKhongTimThay(() -> chiTietTinService.lienHe(null, "xyz"));
    }

    // ------------------------------------------------------------------ yêu thích: lưu / bỏ

    @Test
    @DisplayName("Lưu hai lần chỉ có một dòng và giữ nguyên luu_luc ban đầu")
    void luuHaiLan() throws InterruptedException {
        TinTao tin = tinDaDuyet();
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);

        luu(nguoi, tin);
        Instant lanDau = ht.luuLucYeuThich(nguoi.taiKhoan().getId(), tin.tinId());
        Thread.sleep(50);
        luu(nguoi, tin);

        assertThat(ht.demYeuThich(nguoi.taiKhoan().getId(), tin.tinId())).isEqualTo(1);
        assertThat(ht.luuLucYeuThich(nguoi.taiKhoan().getId(), tin.tinId())).isEqualTo(lanDau);
    }

    @Test
    @DisplayName("Lưu tin không công khai, id ngẫu nhiên hoặc sai định dạng: 404 và không có dòng nào")
    void luuTinKhongCongKhai() {
        TinAn an = dungTinAn();
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);

        for (TinTao tin : an.tatCa()) {
            phaiLaKhongTimThay(() -> luu(nguoi, tin));
            assertThat(ht.demYeuThich(nguoi.taiKhoan().getId(), tin.tinId())).isZero();
        }
        phaiLaKhongTimThay(() -> yeuThichService.luu(nguoi.rawCookie(), UUID.randomUUID().toString()));
        phaiLaKhongTimThay(() -> yeuThichService.luu(nguoi.rawCookie(), "khong-phai-uuid"));
    }

    @Test
    @DisplayName("Đã lưu rồi tin bị ẩn: PUT vẫn thành công (idempotent), không đổi luu_luc")
    void luuLaiTinDaAn() {
        TinTao tin = tinDaDuyet();
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        luu(nguoi, tin);
        Instant luuLuc = ht.luuLucYeuThich(nguoi.taiKhoan().getId(), tin.tinId());
        ht.goViPham(tin.tinId());

        assertThatCode(() -> luu(nguoi, tin)).doesNotThrowAnyException();
        assertThat(ht.demYeuThich(nguoi.taiKhoan().getId(), tin.tinId())).isEqualTo(1);
        assertThat(ht.luuLucYeuThich(nguoi.taiKhoan().getId(), tin.tinId())).isEqualTo(luuLuc);
    }

    @Test
    @DisplayName("Bỏ yêu thích idempotent: bỏ hai lần, chưa từng lưu, tin ẩn/đã xóa, id ngẫu nhiên hoặc sai định dạng đều êm")
    void boYeuThich() {
        TinTao tin = tinDaDuyet();
        TinTao chuaLuu = tinDaDuyet();
        TinTao daXoa = tinDaDuyet();
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        luu(nguoi, tin);
        luu(nguoi, daXoa);
        ht.xoaTin(daXoa);

        bo(nguoi, tin);
        assertThat(ht.demYeuThich(nguoi.taiKhoan().getId(), tin.tinId())).isZero();
        assertThatCode(() -> bo(nguoi, tin)).doesNotThrowAnyException();
        assertThatCode(() -> bo(nguoi, chuaLuu)).doesNotThrowAnyException();
        assertThatCode(() -> bo(nguoi, daXoa)).doesNotThrowAnyException();
        assertThat(ht.demYeuThich(nguoi.taiKhoan().getId(), daXoa.tinId())).isZero();
        assertThatCode(() -> yeuThichService.bo(nguoi.rawCookie(), UUID.randomUUID().toString())).doesNotThrowAnyException();
        assertThatCode(() -> yeuThichService.bo(nguoi.rawCookie(), "khong-phai-uuid")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Khách nhận 401, kiểm duyệt viên và quản trị viên nhận 403 ở cả ba thao tác yêu thích")
    void phanQuyenYeuThich() {
        TinTao tin = tinDaDuyet();
        String id = tin.tinId().toString();
        String kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN).rawCookie();
        String qtv = ht.taoTaiKhoan(VaiTroTaiKhoan.QUAN_TRI_VIEN).rawCookie();

        assertThatThrownBy(() -> yeuThichService.luu(null, id)).isInstanceOf(UnauthenticatedSessionException.class);
        assertThatThrownBy(() -> yeuThichService.bo(null, id)).isInstanceOf(UnauthenticatedSessionException.class);
        assertThatThrownBy(() -> yeuThichService.danhSach(null, null, null)).isInstanceOf(UnauthenticatedSessionException.class);
        for (String cookie : List.of(kdv, qtv)) {
            assertThatThrownBy(() -> yeuThichService.luu(cookie, id)).isInstanceOf(InsufficientPermissionException.class);
            assertThatThrownBy(() -> yeuThichService.bo(cookie, id)).isInstanceOf(InsufficientPermissionException.class);
            assertThatThrownBy(() -> yeuThichService.danhSach(cookie, null, null)).isInstanceOf(InsufficientPermissionException.class);
        }
    }

    // ------------------------------------------------------------------ yêu thích: danh sách

    @Test
    @DisplayName("Danh sách rỗng khi chưa lưu gì")
    void danhSachRong() {
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);

        DanhSachYeuThichResponse kq = danhSach(nguoi, null, null);

        assertThat(kq.items()).isEmpty();
        assertThat(kq.conTroTiepTheo()).isNull();
    }

    @Test
    @DisplayName("Phân trang keyset với nhiều luu_luc bằng nhau: ổn định, không trùng, không sót, mới lưu trước")
    void danhSachPhanTrangLuuLucBangNhau() {
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        List<TinTao> tins = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            tins.add(tinDaDuyet());
        }
        // 3 nhóm, mỗi nhóm 3 tin cùng luu_luc (cùng micro giây).
        for (int i = 0; i < tins.size(); i++) {
            ht.chenYeuThich(nguoi.taiKhoan().getId(), tins.get(i).tinId(), GOC.plusSeconds(60L * (i / 3)));
        }
        List<UUID> mong = tins.stream()
                .sorted(Comparator
                        .comparing((TinTao t) -> ht.luuLucYeuThich(nguoi.taiKhoan().getId(), t.tinId()))
                        .thenComparing(t -> t.tinId().toString())
                        .reversed())
                .map(TinTao::tinId)
                .toList();

        for (int limit : new int[] {1, 2, 4, 9, 50}) {
            List<UUID> nhanDuoc = new ArrayList<>();
            String cursor = null;
            int baoVe = 0;
            do {
                DanhSachYeuThichResponse trang = danhSach(nguoi, cursor, String.valueOf(limit));
                assertThat(trang.items().size()).isLessThanOrEqualTo(limit);
                nhanDuoc.addAll(ids(trang.items()));
                cursor = trang.conTroTiepTheo();
                baoVe++;
            } while (cursor != null && baoVe < 50);
            assertThat(nhanDuoc).as("limit=%d", limit).containsExactlyElementsOf(mong);
            assertThat(new HashSet<>(nhanDuoc)).hasSameSizeAs(nhanDuoc);
        }
        assertThat(danhSach(nguoi, null, "9").conTroTiepTheo()).isNull();
        assertThat(danhSach(nguoi, null, "8").conTroTiepTheo()).isNotNull();
    }

    @Test
    @DisplayName("Mới lưu hiện trước (luu_luc giảm dần)")
    void danhSachMoiLuuTruoc() {
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TinTao cu = tinDaDuyet();
        TinTao moi = tinDaDuyet();
        ht.chenYeuThich(nguoi.taiKhoan().getId(), cu.tinId(), GOC);
        ht.chenYeuThich(nguoi.taiKhoan().getId(), moi.tinId(), GOC.plusSeconds(5));

        assertThat(ids(danhSach(nguoi, null, null).items())).containsExactly(moi.tinId(), cu.tinId());
    }

    @Test
    @DisplayName("Cô lập giữa hai người dùng: mỗi người chỉ thấy yêu thích của mình")
    void danhSachCoLap() {
        TaiKhoanSession a = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession b = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TinTao tinA = tinDaDuyet();
        TinTao tinB = tinDaDuyet();
        TinTao chung = tinDaDuyet();
        luu(a, tinA);
        luu(a, chung);
        luu(b, tinB);
        luu(b, chung);

        assertThat(ids(danhSach(a, null, null).items())).containsExactlyInAnyOrder(tinA.tinId(), chung.tinId());
        assertThat(ids(danhSach(b, null, null).items())).containsExactlyInAnyOrder(tinB.tinId(), chung.tinId());
        bo(a, chung);
        assertThat(ids(danhSach(b, null, null).items())).contains(chung.tinId());
        assertThat(ids(danhSach(a, null, null).items())).containsExactly(tinA.tinId());
    }

    @Test
    @DisplayName("Mục công khai có tin giống kết quả tìm kiếm; tin tài trợ có laTaiTro true")
    void danhSachTinGiongTimKiem() {
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TinTao thuong = ht.taoTinDaDuyet(NoiDung.banMau(kv).voiGia("2500000000").voiDienTich("68.5"), GOC);
        TinTao taiTro = tinDaDuyet();
        ht.taoGoiDangHoatDong(taiTro.tinId());
        luu(nguoi, thuong);
        luu(nguoi, taiTro);

        List<YeuThichItem> items = danhSach(nguoi, null, null).items();
        KetQuaTimKiemResponse timKiem = timKiemTinService.timKiem(
                null, new ThamSoTimKiem(null, null, kv, null, null, null, null, null, null, null, "50"));
        TinTimKiemItem timThuong = timKiem.tuNhien().stream()
                .filter(t -> t.tinId().equals(thuong.tinId())).findFirst().orElseThrow();

        YeuThichItem muc = items.stream().filter(i -> i.tinId().equals(thuong.tinId())).findFirst().orElseThrow();
        assertThat(muc.conHienThi()).isTrue();
        assertThat(muc.luuLuc()).isNotNull();
        assertThat(muc.tin()).isEqualTo(timThuong);
        assertThat(muc.tin().anhDaiDien()).isEqualTo(ht.khoaAnhHienThi(thuong.phienBanId())).isNotBlank();
        YeuThichItem mucTaiTro = items.stream().filter(i -> i.tinId().equals(taiTro.tinId())).findFirst().orElseThrow();
        assertThat(mucTaiTro.tin().laTaiTro()).isTrue();
    }

    @Test
    @DisplayName("Tin bị ẩn sau khi lưu (xóa, gỡ, chưa duyệt, bị từ chối) vẫn có mục với conHienThi=false và tin=null")
    void danhSachTinAnSauKhiLuu() {
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TinTao conHien = tinDaDuyet();
        TinTao daXoa = tinDaDuyet();
        TinTao biGo = tinDaDuyet();
        luu(nguoi, conHien);
        luu(nguoi, daXoa);
        luu(nguoi, biGo);
        ht.xoaTin(daXoa);
        ht.goViPham(biGo.tinId());
        // Không thể lưu qua API: chèn thẳng để mô phỏng dữ liệu lệch (chưa duyệt / bị từ chối).
        TinTao choDuyet = ht.taoTinChoDuyet(NoiDung.banMau(kv));
        TinTao biTuChoi = ht.taoTinChoDuyet(NoiDung.banMau(kv));
        ht.tuChoiPhienBanHienTai(biTuChoi.tinId());
        ht.chenYeuThich(nguoi.taiKhoan().getId(), choDuyet.tinId(), GOC);
        ht.chenYeuThich(nguoi.taiKhoan().getId(), biTuChoi.tinId(), GOC);

        List<YeuThichItem> items = danhSach(nguoi, null, "50").items();

        assertThat(items).hasSize(5);
        for (YeuThichItem muc : items) {
            if (muc.tinId().equals(conHien.tinId())) {
                assertThat(muc.conHienThi()).isTrue();
                assertThat(muc.tin()).isNotNull();
            } else {
                assertThat(muc.conHienThi()).as("tin %s", muc.tinId()).isFalse();
                assertThat(muc.tin()).isNull();
                assertThat(muc.luuLuc()).isNotNull();
            }
        }
        // Người dùng vẫn bỏ lưu được các mục ẩn.
        bo(nguoi, daXoa);
        assertThat(ids(danhSach(nguoi, null, "50").items())).doesNotContain(daXoa.tinId());
    }

    @Test
    @DisplayName("Tin có bản sửa chờ duyệt vẫn hiện nội dung cũ trong danh sách yêu thích")
    void danhSachBanSuaChoDuyet() {
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TinTao tin = tinDaDuyet();
        luu(nguoi, tin);
        ht.suaTin(tin, NoiDung.banMau(kv).voiTieuDe("Tiêu đề mới chờ duyệt"));

        YeuThichItem muc = danhSach(nguoi, null, null).items().get(0);

        assertThat(muc.conHienThi()).isTrue();
        assertThat(muc.tin().tieuDe()).isEqualTo("Căn hộ 2 phòng ngủ view sông");
    }

    @Test
    @DisplayName("cursor hoặc limit không hợp lệ trả 400 theo trường")
    void danhSachThamSoKhongHopLe() {
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);

        kiemTraLoiTruong(() -> danhSach(nguoi, "rac-khong-giai-ma-duoc", null), "cursor");
        kiemTraLoiTruong(() -> danhSach(nguoi, "eTF8MjAyNi0xMS0wMSAwMDowMDowMCswMHwxfGFiYw", null), "cursor");
        for (String limit : new String[] {"0", "51", "-1", "abc", "1.5", "99999999999"}) {
            kiemTraLoiTruong(() -> danhSach(nguoi, null, limit), "limit");
        }
    }
}
