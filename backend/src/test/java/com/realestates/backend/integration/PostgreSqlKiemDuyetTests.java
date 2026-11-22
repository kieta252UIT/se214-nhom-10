package com.realestates.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.auth.application.AuthBrowserSessionLifecyclePort;
import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.InsufficientPermissionException;
import com.realestates.backend.auth.exception.UnauthenticatedSessionException;
import com.realestates.backend.database.TestDatabaseSettings;
import com.realestates.backend.listing.application.BanDoPort;
import com.realestates.backend.listing.application.QuanLyTinService;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.domain.TinDangRepository;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import com.realestates.backend.listing.exception.ListingConflictException;
import com.realestates.backend.listing.exception.ListingNotFoundException;
import com.realestates.backend.listing.exception.ListingPreconditionException;
import com.realestates.backend.moderation.KiemDuyetPgTestSupport;
import com.realestates.backend.moderation.KiemDuyetPgTestSupport.TaiKhoanSession;
import com.realestates.backend.moderation.KiemDuyetPgTestSupport.TinCho;
import com.realestates.backend.moderation.application.KiemDuyetService;
import com.realestates.backend.moderation.domain.KiemDuyetRepository;
import com.realestates.backend.moderation.dto.ChiTietPhienBanResponse;
import com.realestates.backend.moderation.dto.HangChoResponse;
import com.realestates.backend.moderation.dto.KetQuaQuyetDinh;
import com.realestates.backend.moderation.dto.MucHangCho;
import com.realestates.backend.moderation.infrastructure.PostgreSqlKiemDuyetRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = "app.repository.type=postgres")
@ContextConfiguration(initializers = TestDatabaseSettings.SpringInitializer.class)
class PostgreSqlKiemDuyetTests {

    private static final Instant THOI_DIEM = KiemDuyetPgTestSupport.THOI_DIEM;
    private static final Instant THOI_DIEM_DUYET = KiemDuyetPgTestSupport.THOI_DIEM_DUYET;

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
    private TinDangRepository tinDangRepository;

    @Autowired
    private KiemDuyetRepository kiemDuyetRepository;

    @Autowired
    private TinDangService tinDangService;

    @Autowired
    private CurrentPermissionAndCsrfService permissionAndCsrfService;

    @Autowired
    private AuthBrowserSessionLifecyclePort sessionLifecyclePort;

    @Autowired
    private BanDoPort banDoPort;

    private KiemDuyetService kiemDuyetService;
    private KiemDuyetPgTestSupport ht;

    @BeforeEach
    void setUp() {
        assertThat(kiemDuyetRepository).isInstanceOf(PostgreSqlKiemDuyetRepository.class);
        kiemDuyetService = new KiemDuyetService(
                permissionAndCsrfService,
                kiemDuyetRepository,
                Clock.fixed(THOI_DIEM_DUYET, ZoneOffset.UTC)
        );
        QuanLyTinService quanLyTinService = new QuanLyTinService(
                permissionAndCsrfService,
                tinDangRepository,
                banDoPort,
                Clock.fixed(THOI_DIEM, ZoneOffset.UTC)
        );
        ht = new KiemDuyetPgTestSupport(
                jdbcTemplate,
                transactionManager,
                taiKhoanRepository,
                phienXacThucRepository,
                anhTinRepository,
                tinDangService,
                quanLyTinService,
                kiemDuyetService,
                sessionLifecyclePort
        );
    }

    @AfterEach
    void donDep() {
        ht.donDep();
    }

    // ---------------------------------------------------------------- hàng chờ

    @Test
    @DisplayName("Hàng chờ chỉ chứa phiên bản hiện tại đang chờ duyệt; loại tin đã xóa, bị gỡ, đã quyết định và phiên bản cũ")
    void hangChoChiChuaPhienBanChoDuyetHienTai() {
        TaiKhoanSession chu1 = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession chu2 = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);

        TinCho tinCho = ht.taoTinMoiChoDuyet(chu1);
        TinCho tinDaDuyet = ht.taoTinDaDuyet(chu1, kdv);
        TinCho tinXoa = ht.taoTinMoiChoDuyet(chu1);
        ht.xoaTin(chu1, tinXoa.tinId());
        TinCho tinBiGo = ht.taoTinMoiChoDuyet(chu2);
        jdbcTemplate.update(
                "UPDATE se214.tin_bat_dong_san SET trang_thai = 'bi_go_vi_pham', ly_do_ngung_hien_thi = 'Vi phạm' WHERE id = ?",
                tinBiGo.tinId()
        );
        TinCho tinSua = ht.taoTinDaDuyet(chu2, kdv);
        TinGhiKetQua suaKetQua = ht.suaTin(chu2, tinSua.tinId(), "Tiêu đề bản sửa chờ duyệt");
        TinCho tinTuChoiRoiSua = ht.taoTinMoiChoDuyet(chu2);
        kiemDuyetService.tuChoi(kdv.rawCookie(), tinTuChoiRoiSua.tinId(), tinTuChoiRoiSua.phienBanId(), "tc-1",
                ht.etag(tinTuChoiRoiSua.tinId()), Map.of("lyDo", "Thiếu thông tin"));
        TinGhiKetQua suaLaiKetQua = ht.suaTin(chu2, tinTuChoiRoiSua.tinId(), "Tiêu đề đã chỉnh theo lý do");

        List<MucHangCho> tatCa = docTatCaHangCho(kdv, 50);
        Set<UUID> phienBanTrongHang = new HashSet<>();
        for (MucHangCho muc : tatCa) {
            phienBanTrongHang.add(muc.phienBanId());
        }

        assertThat(phienBanTrongHang).contains(tinCho.phienBanId(), suaKetQua.phienBanHienTaiId(), suaLaiKetQua.phienBanHienTaiId());
        assertThat(phienBanTrongHang).doesNotContain(
                tinDaDuyet.phienBanId(),
                tinXoa.phienBanId(),
                tinBiGo.phienBanId(),
                tinSua.phienBanId(),
                tinTuChoiRoiSua.phienBanId()
        );
        MucHangCho banSua = tatCa.stream().filter(m -> m.phienBanId().equals(suaKetQua.phienBanHienTaiId())).findFirst().orElseThrow();
        assertThat(banSua.laBanSua()).isTrue();
        assertThat(banSua.soPhienBan()).isEqualTo(2);
        assertThat(banSua.tinId()).isEqualTo(tinSua.tinId());
        assertThat(banSua.chuId()).isEqualTo(chu2.taiKhoan().getId());
        assertThat(banSua.tieuDe()).isEqualTo("Tiêu đề bản sửa chờ duyệt");
        MucHangCho banMoi = tatCa.stream().filter(m -> m.phienBanId().equals(tinCho.phienBanId())).findFirst().orElseThrow();
        assertThat(banMoi.laBanSua()).isFalse();
        assertThat(banMoi.soPhienBan()).isEqualTo(1);
        assertThat(banMoi.loaiGiaoDich()).isEqualTo("ban");
        assertThat(banMoi.khuVuc()).isEqualTo("TP. Hồ Chí Minh");
        assertThat(banMoi.ketQuaSoBo()).isNotBlank();
        assertThat(banMoi.guiLuc()).isNotNull();
        // Tin bị từ chối rồi sửa lại là tin mới chưa từng được duyệt.
        MucHangCho banSuaLai = tatCa.stream().filter(m -> m.phienBanId().equals(suaLaiKetQua.phienBanHienTaiId())).findFirst().orElseThrow();
        assertThat(banSuaLai.laBanSua()).isFalse();
    }

    @Test
    @DisplayName("Phân trang hàng chờ ổn định khi nhiều bản có cùng tao_luc: không trùng, không sót, đúng thứ tự (tao_luc, id)")
    void phanTrangOnDinhKhiCungTaoLuc() {
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        List<TinCho> cacTin = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
            cacTin.add(ht.taoTinMoiChoDuyet(chu));
            cacTin.add(ht.taoTinMoiChoDuyet(chu));
        }
        Timestamp cungThoiDiem = Timestamp.from(Instant.parse("2001-01-01T00:00:00.123456Z"));
        for (TinCho tin : cacTin) {
            jdbcTemplate.update("UPDATE se214.phien_ban_tin SET tao_luc = ? WHERE id = ?", cungThoiDiem, tin.phienBanId());
        }

        List<MucHangCho> tatCa = new ArrayList<>();
        String conTro = null;
        int soTrang = 0;
        do {
            HangChoResponse trang = kiemDuyetService.layHangCho(kdv.rawCookie(), conTro, "2");
            assertThat(trang.items().size()).isLessThanOrEqualTo(2);
            tatCa.addAll(trang.items());
            conTro = trang.nextCursor();
            soTrang++;
            assertThat(soTrang).isLessThan(100000);
        } while (conTro != null);

        Set<UUID> duyet = new HashSet<>();
        for (MucHangCho muc : tatCa) {
            assertThat(duyet.add(muc.phienBanId())).as("Không được trả trùng " + muc.phienBanId()).isTrue();
        }
        List<MucHangCho> cuaToi = tatCa.stream()
                .filter(m -> cacTin.stream().anyMatch(t -> t.phienBanId().equals(m.phienBanId())))
                .toList();
        assertThat(cuaToi).hasSize(cacTin.size());
        assertThat(cuaToi).extracting(MucHangCho::guiLuc).containsOnly(cungThoiDiem.toInstant());
        List<String> idThuTuTra = cuaToi.stream().map(m -> m.phienBanId().toString()).toList();
        assertThat(idThuTuTra).isSorted();
        // Toàn bộ hàng chờ đơn điệu tăng theo (guiLuc, id).
        for (int i = 1; i < tatCa.size(); i++) {
            MucHangCho truoc = tatCa.get(i - 1);
            MucHangCho sau = tatCa.get(i);
            int soSanh = truoc.guiLuc().compareTo(sau.guiLuc());
            assertThat(soSanh < 0 || (soSanh == 0 && truoc.phienBanId().toString().compareTo(sau.phienBanId().toString()) < 0))
                    .as("Thứ tự tại vị trí " + i)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("cursor và limit sai định dạng hoặc ngoài khoảng bị từ chối 400 (AuthValidationException) với lỗi theo trường")
    void conTroVaGioiHanKhongHopLe() {
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);

        for (String limit : List.of("0", "51", "-1", "abc", "1.5", "+5", "99999999999")) {
            assertThatThrownBy(() -> kiemDuyetService.layHangCho(kdv.rawCookie(), null, limit))
                    .as("limit=" + limit)
                    .isInstanceOfSatisfying(AuthValidationException.class, ex ->
                            assertThat(ex.getTruongLoi()).extracting(f -> f.truong()).containsExactly("limit"));
        }
        String base64KhongPhaiConTro = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("khong-co-dau-gach-dung".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String base64SaiUuid = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("2026-11-18T03:00:00Z|khong-phai-uuid".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String base64SaiThoiDiem = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("hom-qua|" + UUID.randomUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (String cursor : List.of("***khong-phai-base64***", base64KhongPhaiConTro, base64SaiUuid, base64SaiThoiDiem)) {
            assertThatThrownBy(() -> kiemDuyetService.layHangCho(kdv.rawCookie(), cursor, null))
                    .as("cursor=" + cursor)
                    .isInstanceOfSatisfying(AuthValidationException.class, ex ->
                            assertThat(ex.getTruongLoi()).extracting(f -> f.truong()).containsExactly("cursor"));
        }
        assertThat(kiemDuyetService.layHangCho(kdv.rawCookie(), null, "50")).isNotNull();
        assertThat(kiemDuyetService.layHangCho(kdv.rawCookie(), null, "1").items().size()).isLessThanOrEqualTo(1);
    }

    // ---------------------------------------------------------------- chi tiết

    @Test
    @DisplayName("Chi tiết trả đúng phiên bản yêu cầu, ảnh đã gán theo thứ tự, bản đã duyệt để so sánh khi là bản sửa; sai tin thì 404")
    void chiTietPhienBan() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinDaDuyet(chu, kdv);
        TinGhiKetQua sua = ht.suaTin(chu, tin.tinId(), "Tiêu đề bản sửa");
        TinCho tinKhac = ht.taoTinMoiChoDuyet(chu);

        ChiTietPhienBanResponse banSua = kiemDuyetService.layChiTietPhienBan(kdv.rawCookie(), tin.tinId(), sua.phienBanHienTaiId());
        assertThat(banSua.tinId()).isEqualTo(tin.tinId());
        assertThat(banSua.chuId()).isEqualTo(chu.taiKhoan().getId());
        assertThat(banSua.trangThaiTin()).isEqualTo("cho_kiem_duyet");
        assertThat(banSua.phienBanCapNhat()).isEqualTo(ht.phienBanCapNhat(tin.tinId()));
        assertThat(banSua.laBanSua()).isTrue();
        assertThat(banSua.phienBanHienTaiId()).isEqualTo(sua.phienBanHienTaiId());
        assertThat(banSua.phienBanDaDuyetId()).isEqualTo(tin.phienBanId());
        assertThat(banSua.phienBan().id()).isEqualTo(sua.phienBanHienTaiId());
        assertThat(banSua.phienBan().soPhienBan()).isEqualTo(2);
        assertThat(banSua.phienBan().tieuDe()).isEqualTo("Tiêu đề bản sửa");
        assertThat(banSua.phienBan().ketQuaKiemDuyet()).isEqualTo("cho_kiem_duyet");
        assertThat(banSua.phienBan().nguoiDuyetId()).isNull();
        assertThat(banSua.phienBan().thoiDiemQuyetDinh()).isNull();
        assertThat(banSua.phienBan().lyDoTuChoi()).isNull();
        assertThat(banSua.phienBan().anh()).hasSize(1);
        assertThat(banSua.phienBan().anh().get(0).thuTu()).isEqualTo(1);
        assertThat(banSua.phienBan().anh().get(0).khoaAnhHienThi()).isNotBlank();
        assertThat(banSua.phienBanDaDuyet()).isNotNull();
        assertThat(banSua.phienBanDaDuyet().id()).isEqualTo(tin.phienBanId());
        assertThat(banSua.phienBanDaDuyet().soPhienBan()).isEqualTo(1);
        assertThat(banSua.phienBanDaDuyet().tieuDe()).isEqualTo("Căn hộ 2 phòng ngủ view sông");
        assertThat(banSua.phienBanDaDuyet().ketQuaKiemDuyet()).isEqualTo("da_duyet");
        assertThat(banSua.phienBanDaDuyet().nguoiDuyetId()).isEqualTo(kdv.taiKhoan().getId());
        assertThat(banSua.phienBanDaDuyet().thoiDiemQuyetDinh()).isEqualTo(THOI_DIEM_DUYET);
        assertThat(banSua.phienBanDaDuyet().anh()).hasSize(1);
        assertThat(banSua.phienBanDaDuyet().anh().get(0).id()).isEqualTo(tin.anhId());

        // Xem phiên bản đã quyết định cũng được; không có bản so sánh vì chính nó là bản đã duyệt.
        ChiTietPhienBanResponse banCu = kiemDuyetService.layChiTietPhienBan(kdv.rawCookie(), tin.tinId(), tin.phienBanId());
        assertThat(banCu.phienBan().id()).isEqualTo(tin.phienBanId());
        assertThat(banCu.phienBan().ketQuaKiemDuyet()).isEqualTo("da_duyet");
        assertThat(banCu.phienBanDaDuyet()).isNull();
        assertThat(banCu.phienBanCapNhat()).isEqualTo(banSua.phienBanCapNhat());

        // Tin mới: không phải bản sửa, không có bản so sánh.
        ChiTietPhienBanResponse banMoi = kiemDuyetService.layChiTietPhienBan(kdv.rawCookie(), tinKhac.tinId(), tinKhac.phienBanId());
        assertThat(banMoi.laBanSua()).isFalse();
        assertThat(banMoi.phienBanDaDuyet()).isNull();
        assertThat(banMoi.phienBanDaDuyetId()).isNull();

        // Phiên bản không thuộc tin này hoặc tin không tồn tại -> 404.
        assertThatThrownBy(() -> kiemDuyetService.layChiTietPhienBan(kdv.rawCookie(), tin.tinId(), tinKhac.phienBanId()))
                .isInstanceOf(ListingNotFoundException.class);
        assertThatThrownBy(() -> kiemDuyetService.layChiTietPhienBan(kdv.rawCookie(), UUID.randomUUID(), tin.phienBanId()))
                .isInstanceOf(ListingNotFoundException.class);
        assertThatThrownBy(() -> kiemDuyetService.layChiTietPhienBan(kdv.rawCookie(), tin.tinId(), UUID.randomUUID()))
                .isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    @DisplayName("Người dùng thường bị 403 và khách bị 401 ở hàng chờ, chi tiết, phê duyệt và từ chối")
    void phanQuyen() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);

        assertThatThrownBy(() -> kiemDuyetService.layHangCho(chu.rawCookie(), null, null))
                .isInstanceOf(InsufficientPermissionException.class);
        assertThatThrownBy(() -> kiemDuyetService.layChiTietPhienBan(chu.rawCookie(), tin.tinId(), tin.phienBanId()))
                .isInstanceOf(InsufficientPermissionException.class);
        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(chu.rawCookie(), tin.tinId(), tin.phienBanId(), "k-1", "\"0\""))
                .isInstanceOf(InsufficientPermissionException.class);
        assertThatThrownBy(() -> kiemDuyetService.tuChoi(chu.rawCookie(), tin.tinId(), tin.phienBanId(), "k-2", "\"0\"", Map.of("lyDo", "x")))
                .isInstanceOf(InsufficientPermissionException.class);
        assertThatThrownBy(() -> kiemDuyetService.layHangCho(null, null, null))
                .isInstanceOf(UnauthenticatedSessionException.class);
        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(null, tin.tinId(), tin.phienBanId(), "k-3", "\"0\""))
                .isInstanceOf(UnauthenticatedSessionException.class);
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong IN ('phe_duyet', 'tu_choi')", tin.tinId()))
                .isZero();
    }

    // ---------------------------------------------------------------- phê duyệt

    @Test
    @DisplayName("Phê duyệt tin mới: phiên bản, tin, nhật ký kiểm toán, yêu cầu ghi đều đúng; replay trả đúng kết quả cũ, không ghi thêm")
    void pheDuyetTinMoi() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);

        KetQuaQuyetDinh ketQua = kiemDuyetService.pheDuyet(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "duyet-1", "\"0\"");

        assertThat(ketQua.tinId()).isEqualTo(tin.tinId());
        assertThat(ketQua.phienBanId()).isEqualTo(tin.phienBanId());
        assertThat(ketQua.ketQuaKiemDuyet()).isEqualTo("da_duyet");
        assertThat(ketQua.trangThaiTin()).isEqualTo("da_duyet");
        assertThat(ketQua.nguoiDuyetId()).isEqualTo(kdv.taiKhoan().getId());
        assertThat(ketQua.thoiDiemQuyetDinh()).isEqualTo(THOI_DIEM_DUYET);
        assertThat(ketQua.lyDoTuChoi()).isNull();
        assertThat(ketQua.phienBanCapNhat()).isEqualTo(1L);

        Map<String, Object> pb = jdbcTemplate.queryForMap(
                "SELECT ket_qua_kiem_duyet, nguoi_duyet_id, thoi_diem_quyet_dinh, ly_do_tu_choi FROM se214.phien_ban_tin WHERE id = ?",
                tin.phienBanId());
        assertThat(pb.get("ket_qua_kiem_duyet")).isEqualTo("da_duyet");
        assertThat(pb.get("nguoi_duyet_id")).isEqualTo(kdv.taiKhoan().getId());
        assertThat(((Timestamp) pb.get("thoi_diem_quyet_dinh")).toInstant()).isEqualTo(THOI_DIEM_DUYET);
        assertThat(pb.get("ly_do_tu_choi")).isNull();

        Map<String, Object> tinRow = jdbcTemplate.queryForMap(
                "SELECT trang_thai, phien_ban_hien_tai_id, phien_ban_da_duyet_id, ly_do_ngung_hien_thi, phien_ban_cap_nhat "
                        + "FROM se214.tin_bat_dong_san WHERE id = ?", tin.tinId());
        assertThat(tinRow.get("trang_thai")).isEqualTo("da_duyet");
        assertThat(tinRow.get("phien_ban_hien_tai_id")).isEqualTo(tin.phienBanId());
        assertThat(tinRow.get("phien_ban_da_duyet_id")).isEqualTo(tin.phienBanId());
        assertThat(tinRow.get("ly_do_ngung_hien_thi")).isNull();
        assertThat(((Number) tinRow.get("phien_ban_cap_nhat")).longValue()).isEqualTo(1L);

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT nguoi_thuc_hien_id, phien_ban_tin_id, ly_do, thoi_diem, "
                        + "du_lieu_truoc_sau->'truoc'->>'trangThai' AS truoc_trang_thai, "
                        + "du_lieu_truoc_sau->'truoc'->>'phienBanCapNhat' AS truoc_cap_nhat, "
                        + "jsonb_typeof(du_lieu_truoc_sau->'truoc'->'phienBanDaDuyetId') AS truoc_da_duyet_kieu, "
                        + "du_lieu_truoc_sau->'sau'->>'trangThai' AS sau_trang_thai, "
                        + "du_lieu_truoc_sau->'sau'->>'phienBanDaDuyetId' AS sau_da_duyet, "
                        + "du_lieu_truoc_sau->'sau'->>'phienBanCapNhat' AS sau_cap_nhat "
                        + "FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'phe_duyet'",
                tin.tinId());
        assertThat(audit.get("nguoi_thuc_hien_id")).isEqualTo(kdv.taiKhoan().getId());
        assertThat(audit.get("phien_ban_tin_id")).isEqualTo(tin.phienBanId());
        assertThat(audit.get("ly_do")).isNull();
        assertThat(((Timestamp) audit.get("thoi_diem")).toInstant()).isEqualTo(THOI_DIEM_DUYET);
        assertThat(audit.get("truoc_trang_thai")).isEqualTo("cho_kiem_duyet");
        assertThat(audit.get("truoc_cap_nhat")).isEqualTo("0");
        assertThat(audit.get("truoc_da_duyet_kieu")).isEqualTo("null");
        assertThat(audit.get("sau_trang_thai")).isEqualTo("da_duyet");
        assertThat(audit.get("sau_da_duyet")).isEqualTo(tin.phienBanId().toString());
        assertThat(audit.get("sau_cap_nhat")).isEqualTo("1");

        Map<String, Object> yc = jdbcTemplate.queryForMap(
                "SELECT tin_ket_qua_id, phien_ban_ket_qua_id, trang_thai_ket_qua, phien_ban_cap_nhat_ket_qua, du_lieu_bam "
                        + "FROM se214.yeu_cau_ghi WHERE tai_khoan_id = ? AND loai_thao_tac = 'quyet_dinh' AND ma_y_dinh = 'duyet-1'",
                kdv.taiKhoan().getId());
        assertThat(yc.get("tin_ket_qua_id")).isEqualTo(tin.tinId());
        assertThat(yc.get("phien_ban_ket_qua_id")).isEqualTo(tin.phienBanId());
        assertThat(yc.get("trang_thai_ket_qua")).isEqualTo("da_duyet");
        assertThat(((Number) yc.get("phien_ban_cap_nhat_ket_qua")).longValue()).isEqualTo(1L);
        assertThat((String) yc.get("du_lieu_bam")).isNotBlank();

        KetQuaQuyetDinh replay = kiemDuyetService.pheDuyet(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "duyet-1", "\"0\"");
        assertThat(replay).isEqualTo(ketQua);
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'phe_duyet'", tin.tinId()))
                .isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.yeu_cau_ghi WHERE tin_ket_qua_id = ? AND loai_thao_tac = 'quyet_dinh'", tin.tinId()))
                .isEqualTo(1);
        assertThat(ht.phienBanCapNhat(tin.tinId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("Phê duyệt bản sửa: gói đang tạm dừng chạy tiếp với het_han_luc = thời điểm duyệt + thời lượng còn lại, ghi sự kiện chay_tiep")
    void pheDuyetBanSuaChayTiepGoi() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinDaDuyet(chu, kdv);
        Duration conLai = Duration.ofHours(123);
        UUID goiId = ht.taoGoi(tin.tinId(), "dang_hoat_dong", THOI_DIEM.plus(conLai), "7 days");
        TinGhiKetQua sua = ht.suaTin(chu, tin.tinId(), "Tiêu đề bản sửa");
        assertThat(ht.dem("SELECT count(*) FROM se214.goi_day_tin WHERE id = ? AND trang_thai = 'tam_dung_cho_duyet' AND het_han_luc IS NULL", goiId))
                .isEqualTo(1);
        long capNhatTruoc = ht.phienBanCapNhat(tin.tinId());

        KetQuaQuyetDinh ketQua = kiemDuyetService.pheDuyet(kdv.rawCookie(), tin.tinId(), sua.phienBanHienTaiId(), "duyet-sua", ht.etag(tin.tinId()));

        assertThat(ketQua.trangThaiTin()).isEqualTo("da_duyet");
        assertThat(ketQua.phienBanCapNhat()).isEqualTo(capNhatTruoc + 1);
        Map<String, Object> tinRow = jdbcTemplate.queryForMap(
                "SELECT trang_thai, phien_ban_hien_tai_id, phien_ban_da_duyet_id FROM se214.tin_bat_dong_san WHERE id = ?", tin.tinId());
        assertThat(tinRow.get("trang_thai")).isEqualTo("da_duyet");
        assertThat(tinRow.get("phien_ban_hien_tai_id")).isEqualTo(sua.phienBanHienTaiId());
        assertThat(tinRow.get("phien_ban_da_duyet_id")).isEqualTo(sua.phienBanHienTaiId());

        Map<String, Object> goi = jdbcTemplate.queryForMap(
                "SELECT trang_thai, het_han_luc, EXTRACT(EPOCH FROM thoi_luong_con_lai)::bigint AS giay FROM se214.goi_day_tin WHERE id = ?", goiId);
        assertThat(goi.get("trang_thai")).isEqualTo("dang_hoat_dong");
        assertThat(((Timestamp) goi.get("het_han_luc")).toInstant()).isEqualTo(THOI_DIEM_DUYET.plus(conLai));
        assertThat(((Number) goi.get("giay")).longValue()).isEqualTo(conLai.toSeconds());

        Map<String, Object> chayTiep = jdbcTemplate.queryForMap(
                "SELECT nguoi_thuc_hien_id, thoi_diem, EXTRACT(EPOCH FROM thoi_luong_truoc)::bigint AS truoc, "
                        + "EXTRACT(EPOCH FROM thoi_luong_sau)::bigint AS sau FROM se214.su_kien_goi WHERE goi_id = ? AND loai = 'chay_tiep'", goiId);
        assertThat(chayTiep.get("nguoi_thuc_hien_id")).isEqualTo(kdv.taiKhoan().getId());
        assertThat(((Timestamp) chayTiep.get("thoi_diem")).toInstant()).isEqualTo(THOI_DIEM_DUYET);
        assertThat(((Number) chayTiep.get("truoc")).longValue()).isEqualTo(conLai.toSeconds());
        assertThat(((Number) chayTiep.get("sau")).longValue()).isEqualTo(conLai.toSeconds());
        assertThat(ht.dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ?", goiId)).isEqualTo(2);
        assertThat(ht.dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ? AND loai = 'tam_dung'", goiId)).isEqualTo(1);

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT du_lieu_truoc_sau->'truoc'->>'trangThai' AS truoc_trang_thai, "
                        + "du_lieu_truoc_sau->'truoc'->>'phienBanDaDuyetId' AS truoc_da_duyet, "
                        + "du_lieu_truoc_sau->'sau'->>'phienBanDaDuyetId' AS sau_da_duyet "
                        + "FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'phe_duyet' AND phien_ban_tin_id = ?",
                tin.tinId(), sua.phienBanHienTaiId());
        assertThat(audit.get("truoc_trang_thai")).isEqualTo("cho_kiem_duyet");
        assertThat(audit.get("truoc_da_duyet")).isEqualTo(tin.phienBanId().toString());
        assertThat(audit.get("sau_da_duyet")).isEqualTo(sua.phienBanHienTaiId().toString());
    }

    // ---------------------------------------------------------------- từ chối

    @Test
    @DisplayName("Từ chối bắt buộc có lý do: null, rỗng, toàn khoảng trắng, không phải chuỗi, 1001 ký tự đều bị 400 trường lyDo và không ghi gì")
    void tuChoiPhaiCoLyDo() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);

        java.util.Map<String, Object> lyDoNull = new java.util.HashMap<>();
        lyDoNull.put("lyDo", null);
        List<Map<String, Object>> cacBodyLoi = List.of(
                lyDoNull,
                Map.of("lyDo", ""),
                Map.of("lyDo", "   \t\n "),
                Map.of("lyDo", 123),
                Map.of("lyDo", "a".repeat(1001)),
                Map.of("khac", "x")
        );
        int stt = 0;
        for (Map<String, Object> body : cacBodyLoi) {
            String maYDinh = "tc-loi-" + stt++;
            assertThatThrownBy(() -> kiemDuyetService.tuChoi(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), maYDinh, "\"0\"", body))
                    .as("body=" + body)
                    .isInstanceOfSatisfying(AuthValidationException.class, ex ->
                            assertThat(ex.getTruongLoi()).extracting(f -> f.truong()).containsExactly("lyDo"));
        }
        assertThatThrownBy(() -> kiemDuyetService.tuChoi(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "tc-null-body", "\"0\"", null))
                .isInstanceOfSatisfying(AuthValidationException.class, ex ->
                        assertThat(ex.getTruongLoi()).extracting(f -> f.truong()).containsExactly("lyDo"));

        assertThat(ht.dem("SELECT count(*) FROM se214.phien_ban_tin WHERE id = ? AND ket_qua_kiem_duyet = 'cho_kiem_duyet'", tin.phienBanId())).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'tu_choi'", tin.tinId())).isZero();
        assertThat(ht.dem("SELECT count(*) FROM se214.yeu_cau_ghi WHERE tin_ket_qua_id = ? AND loai_thao_tac = 'quyet_dinh'", tin.tinId())).isZero();

        // Đúng 1000 ký tự sau khi trim là hợp lệ.
        String lyDo1000 = "b".repeat(1000);
        KetQuaQuyetDinh ketQua = kiemDuyetService.tuChoi(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "tc-1000", "\"0\"", Map.of("lyDo", "  " + lyDo1000 + "  "));
        assertThat(ketQua.lyDoTuChoi()).isEqualTo(lyDo1000);
    }

    @Test
    @DisplayName("Từ chối tin mới: tin thành bi_tu_choi, lý do được trim và lưu vào phiên bản và nhật ký; replay không ghi thêm")
    void tuChoiTinMoi() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);

        KetQuaQuyetDinh ketQua = kiemDuyetService.tuChoi(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "tc-1", "\"0\"", Map.of("lyDo", "  Ảnh không đúng thực tế  "));

        assertThat(ketQua.ketQuaKiemDuyet()).isEqualTo("bi_tu_choi");
        assertThat(ketQua.trangThaiTin()).isEqualTo("bi_tu_choi");
        assertThat(ketQua.lyDoTuChoi()).isEqualTo("Ảnh không đúng thực tế");
        assertThat(ketQua.nguoiDuyetId()).isEqualTo(kdv.taiKhoan().getId());
        assertThat(ketQua.thoiDiemQuyetDinh()).isEqualTo(THOI_DIEM_DUYET);
        assertThat(ketQua.phienBanCapNhat()).isEqualTo(1L);

        Map<String, Object> tinRow = jdbcTemplate.queryForMap(
                "SELECT trang_thai, phien_ban_hien_tai_id, phien_ban_da_duyet_id, phien_ban_cap_nhat FROM se214.tin_bat_dong_san WHERE id = ?", tin.tinId());
        assertThat(tinRow.get("trang_thai")).isEqualTo("bi_tu_choi");
        assertThat(tinRow.get("phien_ban_hien_tai_id")).isEqualTo(tin.phienBanId());
        assertThat(tinRow.get("phien_ban_da_duyet_id")).isNull();
        assertThat(((Number) tinRow.get("phien_ban_cap_nhat")).longValue()).isEqualTo(1L);
        Map<String, Object> pb = jdbcTemplate.queryForMap(
                "SELECT ket_qua_kiem_duyet, nguoi_duyet_id, ly_do_tu_choi FROM se214.phien_ban_tin WHERE id = ?", tin.phienBanId());
        assertThat(pb.get("ket_qua_kiem_duyet")).isEqualTo("bi_tu_choi");
        assertThat(pb.get("nguoi_duyet_id")).isEqualTo(kdv.taiKhoan().getId());
        assertThat(pb.get("ly_do_tu_choi")).isEqualTo("Ảnh không đúng thực tế");
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT ly_do, du_lieu_truoc_sau->'sau'->>'trangThai' AS sau_trang_thai FROM se214.nhat_ky_kiem_toan "
                        + "WHERE tin_id = ? AND hanh_dong = 'tu_choi' AND phien_ban_tin_id = ?", tin.tinId(), tin.phienBanId());
        assertThat(audit.get("ly_do")).isEqualTo("Ảnh không đúng thực tế");
        assertThat(audit.get("sau_trang_thai")).isEqualTo("bi_tu_choi");

        KetQuaQuyetDinh replay = kiemDuyetService.tuChoi(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "tc-1", "\"0\"", Map.of("lyDo", "Ảnh không đúng thực tế"));
        assertThat(replay).isEqualTo(ketQua);
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'tu_choi'", tin.tinId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Từ chối bản sửa: tin quay về da_duyet với bản đã duyệt cũ, bản sửa bị từ chối, gói tạm dừng chạy tiếp")
    void tuChoiBanSuaTraTinVeBanCu() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinDaDuyet(chu, kdv);
        Duration conLai = Duration.ofDays(3);
        UUID goiId = ht.taoGoi(tin.tinId(), "dang_hoat_dong", THOI_DIEM.plus(conLai), "3 days");
        TinGhiKetQua sua = ht.suaTin(chu, tin.tinId(), "Tiêu đề bản sửa bị từ chối");
        long capNhatTruoc = ht.phienBanCapNhat(tin.tinId());

        KetQuaQuyetDinh ketQua = kiemDuyetService.tuChoi(kdv.rawCookie(), tin.tinId(), sua.phienBanHienTaiId(), "tc-sua", ht.etag(tin.tinId()), Map.of("lyDo", "Giá không hợp lý"));

        assertThat(ketQua.ketQuaKiemDuyet()).isEqualTo("bi_tu_choi");
        assertThat(ketQua.trangThaiTin()).isEqualTo("da_duyet");
        assertThat(ketQua.phienBanCapNhat()).isEqualTo(capNhatTruoc + 1);
        Map<String, Object> tinRow = jdbcTemplate.queryForMap(
                "SELECT trang_thai, phien_ban_hien_tai_id, phien_ban_da_duyet_id, phien_ban_cap_nhat FROM se214.tin_bat_dong_san WHERE id = ?", tin.tinId());
        assertThat(tinRow.get("trang_thai")).isEqualTo("da_duyet");
        assertThat(tinRow.get("phien_ban_da_duyet_id")).isEqualTo(tin.phienBanId());
        assertThat(tinRow.get("phien_ban_hien_tai_id")).isEqualTo(sua.phienBanHienTaiId());
        assertThat(((Number) tinRow.get("phien_ban_cap_nhat")).longValue()).isEqualTo(capNhatTruoc + 1);
        assertThat(ht.dem("SELECT count(*) FROM se214.phien_ban_tin WHERE id = ? AND ket_qua_kiem_duyet = 'bi_tu_choi' AND ly_do_tu_choi = 'Giá không hợp lý'",
                sua.phienBanHienTaiId())).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.phien_ban_tin WHERE id = ? AND ket_qua_kiem_duyet = 'da_duyet'", tin.phienBanId())).isEqualTo(1);

        Map<String, Object> goi = jdbcTemplate.queryForMap(
                "SELECT trang_thai, het_han_luc FROM se214.goi_day_tin WHERE id = ?", goiId);
        assertThat(goi.get("trang_thai")).isEqualTo("dang_hoat_dong");
        assertThat(((Timestamp) goi.get("het_han_luc")).toInstant()).isEqualTo(THOI_DIEM_DUYET.plus(conLai));
        assertThat(ht.dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ? AND loai = 'chay_tiep'", goiId)).isEqualTo(1);

        // Tin công khai vẫn là bản cũ: không còn trong hàng chờ.
        Set<UUID> trongHang = new HashSet<>();
        docTatCaHangCho(kdv, 50).forEach(m -> trongHang.add(m.phienBanId()));
        assertThat(trongHang).doesNotContain(sua.phienBanHienTaiId());
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT du_lieu_truoc_sau->'sau'->>'trangThai' AS sau_trang_thai, du_lieu_truoc_sau->'sau'->>'phienBanDaDuyetId' AS sau_da_duyet "
                        + "FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'tu_choi'", tin.tinId());
        assertThat(audit.get("sau_trang_thai")).isEqualTo("da_duyet");
        assertThat(audit.get("sau_da_duyet")).isEqualTo(tin.phienBanId().toString());
    }

    @Test
    @DisplayName("Từ chối tin mới không đụng tới gói đẩy tin và không có sự kiện chay_tiep")
    void tuChoiTinMoiKhongDungGoi() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);
        UUID goiId = ht.taoGoi(tin.tinId(), "tam_dung_cho_duyet", null, "2 days");

        kiemDuyetService.tuChoi(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "tc-1", "\"0\"", Map.of("lyDo", "Không hợp lệ"));

        assertThat(ht.dem("SELECT count(*) FROM se214.goi_day_tin WHERE id = ? AND trang_thai = 'tam_dung_cho_duyet' AND het_han_luc IS NULL", goiId)).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ?", goiId)).isZero();
    }

    // ---------------------------------------------------------------- điều kiện và xung đột

    @Test
    @DisplayName("Quyết định lần hai trên cùng phiên bản bị 409 DA_CO_QUYET_DINH; ETag cũ bị 412 trước")
    void quyetDinhLanHai() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);
        kiemDuyetService.pheDuyet(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "duyet-1", "\"0\"");

        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "duyet-2", "\"1\""))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("DA_CO_QUYET_DINH"));
        assertThatThrownBy(() -> kiemDuyetService.tuChoi(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "tc-2", "\"1\"", Map.of("lyDo", "Muộn rồi")))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("DA_CO_QUYET_DINH"));
        assertThatThrownBy(() -> kiemDuyetService.tuChoi(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "tc-3", "\"0\"", Map.of("lyDo", "Muộn rồi")))
                .isInstanceOfSatisfying(ListingPreconditionException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("PHIEN_BAN_CU"));

        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong IN ('phe_duyet', 'tu_choi')", tin.tinId())).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.tin_bat_dong_san WHERE id = ? AND trang_thai = 'da_duyet' AND phien_ban_cap_nhat = 1", tin.tinId())).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.yeu_cau_ghi WHERE tin_ket_qua_id = ? AND loai_thao_tac = 'quyet_dinh'", tin.tinId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Tin đã xóa (qua luồng xóa thật) -> 409 TIN_DA_XOA; tin bị gỡ -> 409 TIN_DA_BI_GO; phiên bản không đổi")
    void tinDaXoaVaBiGo() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tinXoa = ht.taoTinMoiChoDuyet(chu);
        ht.xoaTin(chu, tinXoa.tinId());
        TinCho tinGo = ht.taoTinMoiChoDuyet(chu);
        jdbcTemplate.update("UPDATE se214.tin_bat_dong_san SET trang_thai = 'bi_go_vi_pham', ly_do_ngung_hien_thi = 'Vi phạm' WHERE id = ?", tinGo.tinId());

        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(kdv.rawCookie(), tinXoa.tinId(), tinXoa.phienBanId(), "duyet-xoa", ht.etag(tinXoa.tinId())))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("TIN_DA_XOA"));
        assertThatThrownBy(() -> kiemDuyetService.tuChoi(kdv.rawCookie(), tinXoa.tinId(), tinXoa.phienBanId(), "tc-xoa", ht.etag(tinXoa.tinId()), Map.of("lyDo", "x")))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("TIN_DA_XOA"));
        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(kdv.rawCookie(), tinGo.tinId(), tinGo.phienBanId(), "duyet-go", ht.etag(tinGo.tinId())))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("TIN_DA_BI_GO"));

        assertThat(ht.dem("SELECT count(*) FROM se214.phien_ban_tin WHERE id IN (?, ?) AND ket_qua_kiem_duyet = 'cho_kiem_duyet' AND nguoi_duyet_id IS NULL",
                tinXoa.phienBanId(), tinGo.phienBanId())).isEqualTo(2);
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id IN (?, ?) AND hanh_dong IN ('phe_duyet', 'tu_choi')",
                tinXoa.tinId(), tinGo.tinId())).isZero();
    }

    @Test
    @DisplayName("Chủ tin gửi bản sửa mới khi kiểm duyệt viên còn giữ bản cũ -> 409 PHIEN_BAN_DA_CU (ETag mới) hoặc 412 (ETag cũ)")
    void banSuaMoiLamPhienBanCuLoiThoi() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);
        String etagKdvDangGiu = ht.etag(tin.tinId());
        kiemDuyetService.tuChoi(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "tc-1", etagKdvDangGiu, Map.of("lyDo", "Thiếu giấy tờ"));
        TinGhiKetQua suaLai = ht.suaTin(chu, tin.tinId(), "Tiêu đề đã bổ sung giấy tờ");

        // ETag kiểm duyệt viên giữ đã cũ.
        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "duyet-cu-1", etagKdvDangGiu))
                .isInstanceOfSatisfying(ListingPreconditionException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("PHIEN_BAN_CU"));
        // Tải lại ETag nhưng vẫn quyết định trên phiên bản cũ.
        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "duyet-cu-2", ht.etag(tin.tinId())))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("PHIEN_BAN_DA_CU"));
        assertThatThrownBy(() -> kiemDuyetService.tuChoi(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "tc-cu-3", ht.etag(tin.tinId()), Map.of("lyDo", "x")))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("PHIEN_BAN_DA_CU"));

        // Phiên bản mới vẫn duyệt bình thường.
        KetQuaQuyetDinh ketQua = kiemDuyetService.pheDuyet(kdv.rawCookie(), tin.tinId(), suaLai.phienBanHienTaiId(), "duyet-moi", ht.etag(tin.tinId()));
        assertThat(ketQua.trangThaiTin()).isEqualTo("da_duyet");
        assertThat(ht.dem("SELECT count(*) FROM se214.tin_bat_dong_san WHERE id = ? AND phien_ban_da_duyet_id = ?", tin.tinId(), suaLai.phienBanHienTaiId())).isEqualTo(1);
    }

    @Test
    @DisplayName("If-Match: thiếu -> 428 THIEU_IF_MATCH, lệch -> 412 PHIEN_BAN_CU, không phải ETag mạnh -> 400; không ghi gì")
    void ifMatch() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);

        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "k-1", null))
                .isInstanceOfSatisfying(ListingPreconditionException.class, ex -> {
                    assertThat(ex.getMaLoi()).isEqualTo("THIEU_IF_MATCH");
                    assertThat(ex.laThieuIfMatch()).isTrue();
                });
        assertThatThrownBy(() -> kiemDuyetService.tuChoi(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "k-2", "  ", Map.of("lyDo", "x")))
                .isInstanceOfSatisfying(ListingPreconditionException.class, ex -> assertThat(ex.laThieuIfMatch()).isTrue());
        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "k-3", "\"7\""))
                .isInstanceOfSatisfying(ListingPreconditionException.class, ex -> {
                    assertThat(ex.getMaLoi()).isEqualTo("PHIEN_BAN_CU");
                    assertThat(ex.laThieuIfMatch()).isFalse();
                });
        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "k-4", "W/\"0\""))
                .isInstanceOfSatisfying(AuthValidationException.class, ex ->
                        assertThat(ex.getTruongLoi()).extracting(f -> f.truong()).containsExactly("If-Match"));
        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), null, "\"0\""))
                .isInstanceOfSatisfying(AuthValidationException.class, ex ->
                        assertThat(ex.getTruongLoi()).extracting(f -> f.truong()).containsExactly("Idempotency-Key"));
        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(kdv.rawCookie(), tin.tinId(), tin.phienBanId(), "k".repeat(256), "\"0\""))
                .isInstanceOf(AuthValidationException.class);

        assertThat(ht.dem("SELECT count(*) FROM se214.phien_ban_tin WHERE id = ? AND ket_qua_kiem_duyet = 'cho_kiem_duyet'", tin.phienBanId())).isEqualTo(1);
        assertThat(ht.phienBanCapNhat(tin.tinId())).isZero();
    }

    @Test
    @DisplayName("Phiên bản không thuộc tin hoặc tin không tồn tại -> 404")
    void phienBanKhongThuocTin() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);
        TinCho tinKhac = ht.taoTinMoiChoDuyet(chu);

        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(kdv.rawCookie(), tin.tinId(), tinKhac.phienBanId(), "k-1", "\"0\""))
                .isInstanceOf(ListingNotFoundException.class);
        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(kdv.rawCookie(), tin.tinId(), UUID.randomUUID(), "k-2", "\"0\""))
                .isInstanceOf(ListingNotFoundException.class);
        assertThatThrownBy(() -> kiemDuyetService.tuChoi(kdv.rawCookie(), UUID.randomUUID(), tin.phienBanId(), "k-3", "\"0\"", Map.of("lyDo", "x")))
                .isInstanceOf(ListingNotFoundException.class);
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id IN (?, ?) AND hanh_dong IN ('phe_duyet', 'tu_choi')",
                tin.tinId(), tinKhac.tinId())).isZero();
    }

    @Test
    @DisplayName("Idempotency-Key: cùng khóa cùng nội dung trả lại kết quả cũ; cùng khóa khác nội dung hoặc khác thao tác -> 409 MA_Y_DINH_KHAC_NOI_DUNG")
    void idempotency() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tinA = ht.taoTinMoiChoDuyet(chu);
        TinCho tinB = ht.taoTinMoiChoDuyet(chu);

        KetQuaQuyetDinh duyetA = kiemDuyetService.pheDuyet(kdv.rawCookie(), tinA.tinId(), tinA.phienBanId(), "khoa-chung", "\"0\"");
        assertThat(kiemDuyetService.pheDuyet(kdv.rawCookie(), tinA.tinId(), tinA.phienBanId(), "khoa-chung", "\"0\"")).isEqualTo(duyetA);
        // Cùng khóa nhưng thao tác khác (từ chối), hoặc tin khác, hoặc If-Match khác.
        assertThatThrownBy(() -> kiemDuyetService.tuChoi(kdv.rawCookie(), tinA.tinId(), tinA.phienBanId(), "khoa-chung", "\"0\"", Map.of("lyDo", "x")))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("MA_Y_DINH_KHAC_NOI_DUNG"));
        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(kdv.rawCookie(), tinB.tinId(), tinB.phienBanId(), "khoa-chung", "\"0\""))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("MA_Y_DINH_KHAC_NOI_DUNG"));
        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(kdv.rawCookie(), tinA.tinId(), tinA.phienBanId(), "khoa-chung", "\"1\""))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("MA_Y_DINH_KHAC_NOI_DUNG"));

        KetQuaQuyetDinh tuChoiB = kiemDuyetService.tuChoi(kdv.rawCookie(), tinB.tinId(), tinB.phienBanId(), "khoa-tc", "\"0\"", Map.of("lyDo", "Lý do một"));
        assertThat(kiemDuyetService.tuChoi(kdv.rawCookie(), tinB.tinId(), tinB.phienBanId(), "khoa-tc", "\"0\"", Map.of("lyDo", "  Lý do một "))).isEqualTo(tuChoiB);
        assertThatThrownBy(() -> kiemDuyetService.tuChoi(kdv.rawCookie(), tinB.tinId(), tinB.phienBanId(), "khoa-tc", "\"0\"", Map.of("lyDo", "Lý do hai")))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("MA_Y_DINH_KHAC_NOI_DUNG"));

        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'phe_duyet'", tinA.tinId())).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'tu_choi'", tinB.tinId())).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.yeu_cau_ghi WHERE tai_khoan_id = ? AND loai_thao_tac = 'quyet_dinh'", kdv.taiKhoan().getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("Cùng Idempotency-Key của hai kiểm duyệt viên khác nhau là hai ý định độc lập")
    void maYDinhTheoTaiKhoan() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv1 = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TaiKhoanSession kdv2 = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);

        kiemDuyetService.pheDuyet(kdv1.rawCookie(), tin.tinId(), tin.phienBanId(), "trung-khoa", "\"0\"");
        assertThatThrownBy(() -> kiemDuyetService.pheDuyet(kdv2.rawCookie(), tin.tinId(), tin.phienBanId(), "trung-khoa", "\"1\""))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("DA_CO_QUYET_DINH"));
    }

    // ---------------------------------------------------------------- tiện ích

    private List<MucHangCho> docTatCaHangCho(TaiKhoanSession kdv, int gioiHan) {
        List<MucHangCho> tatCa = new ArrayList<>();
        String conTro = null;
        int soTrang = 0;
        do {
            HangChoResponse trang = kiemDuyetService.layHangCho(kdv.rawCookie(), conTro, String.valueOf(gioiHan));
            tatCa.addAll(trang.items());
            conTro = trang.nextCursor();
            soTrang++;
            assertThat(soTrang).isLessThan(100000);
        } while (conTro != null);
        return tatCa;
    }
}
