package com.realestates.backend.moderation.infrastructure;

import com.realestates.backend.listing.domain.ChuanHoaJson;
import com.realestates.backend.moderation.domain.ConTroHangCho;
import com.realestates.backend.moderation.domain.KiemDuyetRepository;
import com.realestates.backend.moderation.dto.AnhPhienBan;
import com.realestates.backend.moderation.dto.ChiTietPhienBanResponse;
import com.realestates.backend.moderation.dto.KetQuaQuyetDinh;
import com.realestates.backend.moderation.dto.MucHangCho;
import com.realestates.backend.moderation.dto.NoiDungPhienBan;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlKiemDuyetRepository implements KiemDuyetRepository {

    private static final String TRANG_THAI_DA_DUYET = "da_duyet";
    private static final String TRANG_THAI_BI_TU_CHOI = "bi_tu_choi";
    private static final String TRANG_THAI_DA_XOA = "da_xoa";
    private static final String TRANG_THAI_BI_GO = "bi_go_vi_pham";
    private static final String KET_QUA_CHO = "cho_kiem_duyet";

    private record TinKhoaRow(
            UUID chuId,
            String trangThai,
            UUID phienBanHienTaiId,
            UUID phienBanDaDuyetId,
            long phienBanCapNhat
    ) {
    }

    private record PhienBanQuyetDinhRow(UUID tinId, String ketQuaKiemDuyet) {
    }

    private record GoiTamDungRow(UUID id) {
    }

    private static final String SQL_HANG_CHO_DAU = """
            SELECT t.id AS tin_id, p.id AS phien_ban_id, p.so_phien_ban,
                   (t.phien_ban_da_duyet_id IS NOT NULL) AS la_ban_sua,
                   p.tieu_de, p.loai_giao_dich, p.loai_bds, p.khu_vuc, p.gia_goc, p.don_vi_gia,
                   p.dien_tich_m2, p.ket_qua_so_bo, p.tao_luc, t.chu_id
            FROM se214.tin_bat_dong_san t
            JOIN se214.phien_ban_tin p ON p.id = t.phien_ban_hien_tai_id AND p.tin_id = t.id
            WHERE p.ket_qua_kiem_duyet = 'cho_kiem_duyet'
              AND t.trang_thai NOT IN ('da_xoa', 'bi_go_vi_pham')
            ORDER BY p.tao_luc ASC, p.id ASC
            LIMIT ?
            """;

    private static final String SQL_HANG_CHO_TIEP = """
            SELECT t.id AS tin_id, p.id AS phien_ban_id, p.so_phien_ban,
                   (t.phien_ban_da_duyet_id IS NOT NULL) AS la_ban_sua,
                   p.tieu_de, p.loai_giao_dich, p.loai_bds, p.khu_vuc, p.gia_goc, p.don_vi_gia,
                   p.dien_tich_m2, p.ket_qua_so_bo, p.tao_luc, t.chu_id
            FROM se214.tin_bat_dong_san t
            JOIN se214.phien_ban_tin p ON p.id = t.phien_ban_hien_tai_id AND p.tin_id = t.id
            WHERE p.ket_qua_kiem_duyet = 'cho_kiem_duyet'
              AND t.trang_thai NOT IN ('da_xoa', 'bi_go_vi_pham')
              AND (p.tao_luc, p.id) > (CAST(? AS timestamptz), CAST(? AS uuid))
            ORDER BY p.tao_luc ASC, p.id ASC
            LIMIT ?
            """;

    private static final String SQL_DOC_TIN = """
            SELECT chu_id, trang_thai, phien_ban_hien_tai_id, phien_ban_da_duyet_id, phien_ban_cap_nhat
            FROM se214.tin_bat_dong_san
            WHERE id = ?
            """;

    private static final String SQL_KHOA_TIN = SQL_DOC_TIN + " FOR UPDATE";

    private static final String SQL_DOC_PHIEN_BAN = """
            SELECT id, so_phien_ban, tieu_de, mo_ta, loai_giao_dich, loai_bds, gia_goc, don_vi_gia,
                   dien_tich_m2, dia_chi, khu_vuc, vi_do, kinh_do, ket_qua_so_bo, ket_qua_kiem_duyet,
                   nguoi_duyet_id, thoi_diem_quyet_dinh, ly_do_tu_choi, tao_luc
            FROM se214.phien_ban_tin
            WHERE id = ?
              AND tin_id = ?
            """;

    private static final String SQL_DOC_ANH_PHIEN_BAN = """
            SELECT id, thu_tu, khoa_anh_hien_thi
            FROM se214.anh_tin
            WHERE phien_ban_tin_id = ?
              AND trang_thai = 'da_gan'
            ORDER BY thu_tu ASC
            """;

    private static final String SQL_TIM_YEU_CAU_QUYET_DINH = """
            SELECT y.du_lieu_bam, y.tin_ket_qua_id, y.phien_ban_ket_qua_id, y.trang_thai_ket_qua,
                   y.phien_ban_cap_nhat_ket_qua,
                   p.ket_qua_kiem_duyet, p.nguoi_duyet_id, p.thoi_diem_quyet_dinh, p.ly_do_tu_choi
            FROM se214.yeu_cau_ghi y
            JOIN se214.phien_ban_tin p ON p.tin_id = y.tin_ket_qua_id AND p.id = y.phien_ban_ket_qua_id
            WHERE y.tai_khoan_id = ?
              AND y.loai_thao_tac = 'quyet_dinh'
              AND y.ma_y_dinh = ?
            """;

    private static final String SQL_DOC_QUYET_DINH_PHIEN_BAN = """
            SELECT ket_qua_kiem_duyet, nguoi_duyet_id, thoi_diem_quyet_dinh, ly_do_tu_choi
            FROM se214.phien_ban_tin
            WHERE id = ?
              AND tin_id = ?
            """;

    private static final String SQL_DOC_PHIEN_BAN_DE_KIEM_TRA = """
            SELECT tin_id, ket_qua_kiem_duyet
            FROM se214.phien_ban_tin
            WHERE id = ?
            """;

    private static final String SQL_GHI_QUYET_DINH_PHIEN_BAN = """
            UPDATE se214.phien_ban_tin
            SET ket_qua_kiem_duyet = ?,
                nguoi_duyet_id = ?,
                thoi_diem_quyet_dinh = ?,
                ly_do_tu_choi = CAST(? AS text)
            WHERE id = ?
              AND tin_id = ?
              AND ket_qua_kiem_duyet = 'cho_kiem_duyet'
            """;

    private static final String SQL_PHE_DUYET_TIN = """
            UPDATE se214.tin_bat_dong_san
            SET trang_thai = 'da_duyet',
                phien_ban_da_duyet_id = ?,
                ly_do_ngung_hien_thi = NULL,
                phien_ban_cap_nhat = phien_ban_cap_nhat + 1
            WHERE id = ?
            """;

    private static final String SQL_TRANG_THAI_TIN_SAU_TU_CHOI = """
            UPDATE se214.tin_bat_dong_san
            SET trang_thai = ?,
                phien_ban_cap_nhat = phien_ban_cap_nhat + 1
            WHERE id = ?
            """;

    private static final String SQL_KHOA_GOI_TAM_DUNG = """
            SELECT id
            FROM se214.goi_day_tin
            WHERE tin_id = ?
              AND trang_thai = 'tam_dung_cho_duyet'
            FOR UPDATE
            """;

    private static final String SQL_CHAY_TIEP_GOI = """
            UPDATE se214.goi_day_tin
            SET trang_thai = 'dang_hoat_dong',
                het_han_luc = CAST(? AS timestamptz) + thoi_luong_con_lai
            WHERE id = ?
              AND trang_thai = 'tam_dung_cho_duyet'
            """;

    private static final String SQL_SU_KIEN_CHAY_TIEP = """
            INSERT INTO se214.su_kien_goi (
                id, goi_id, nguoi_thuc_hien_id, loai, thoi_luong_truoc, thoi_luong_sau, thoi_diem
            ) VALUES (
                ?, ?, ?, 'chay_tiep',
                (SELECT thoi_luong_con_lai FROM se214.goi_day_tin WHERE id = ?),
                (SELECT thoi_luong_con_lai FROM se214.goi_day_tin WHERE id = ?),
                ?
            )
            """;

    private static final String SQL_INSERT_AUDIT = """
            INSERT INTO se214.nhat_ky_kiem_toan (
                id, nguoi_thuc_hien_id, tin_id, phien_ban_tin_id, hanh_dong, du_lieu_truoc_sau, ly_do, thoi_diem
            ) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS text), ?)
            """;

    private static final String SQL_INSERT_YEU_CAU_GHI = """
            INSERT INTO se214.yeu_cau_ghi (
                id, tai_khoan_id, loai_thao_tac, ma_y_dinh, du_lieu_bam,
                tin_ket_qua_id, phien_ban_ket_qua_id, trang_thai_ket_qua, phien_ban_cap_nhat_ket_qua, tao_luc
            ) VALUES (?, ?, 'quyet_dinh', ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlKiemDuyetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null");
    }

    @Override
    public List<MucHangCho> docHangCho(ConTroHangCho sau, int gioiHan) {
        if (sau == null) {
            return jdbcTemplate.query(SQL_HANG_CHO_DAU, (rs, rowNum) -> mucHangCho(rs), gioiHan);
        }
        return jdbcTemplate.query(
                SQL_HANG_CHO_TIEP,
                (rs, rowNum) -> mucHangCho(rs),
                Timestamp.from(sau.guiLuc()),
                sau.phienBanId(),
                gioiHan
        );
    }

    private static MucHangCho mucHangCho(ResultSet rs) throws SQLException {
        return new MucHangCho(
                rs.getObject("tin_id", UUID.class),
                rs.getObject("phien_ban_id", UUID.class),
                rs.getInt("so_phien_ban"),
                rs.getBoolean("la_ban_sua"),
                rs.getString("tieu_de"),
                rs.getString("loai_giao_dich"),
                rs.getString("loai_bds"),
                rs.getString("khu_vuc"),
                rs.getBigDecimal("gia_goc"),
                rs.getString("don_vi_gia"),
                rs.getBigDecimal("dien_tich_m2"),
                rs.getString("ket_qua_so_bo"),
                rs.getTimestamp("tao_luc").toInstant(),
                rs.getObject("chu_id", UUID.class)
        );
    }

    @Override
    public Optional<ChiTietPhienBanResponse> docChiTietPhienBan(UUID tinId, UUID phienBanId) {
        // Đọc tin trước để ETag (phien_ban_cap_nhat) không bao giờ mới hơn nội dung trả về.
        Optional<TinKhoaRow> tinOpt = jdbcTemplate.query(SQL_DOC_TIN, (rs, rowNum) -> tinRow(rs), tinId)
                .stream().findFirst();
        if (tinOpt.isEmpty()) {
            return Optional.empty();
        }
        TinKhoaRow tin = tinOpt.get();
        Optional<NoiDungPhienBan> phienBan = docNoiDungPhienBan(tinId, phienBanId);
        if (phienBan.isEmpty()) {
            return Optional.empty();
        }
        NoiDungPhienBan banDaDuyet = null;
        if (tin.phienBanDaDuyetId() != null && !tin.phienBanDaDuyetId().equals(phienBanId)) {
            banDaDuyet = docNoiDungPhienBan(tinId, tin.phienBanDaDuyetId()).orElse(null);
        }
        return Optional.of(new ChiTietPhienBanResponse(
                tinId,
                tin.chuId(),
                tin.trangThai(),
                tin.phienBanCapNhat(),
                tin.phienBanHienTaiId(),
                tin.phienBanDaDuyetId(),
                tin.phienBanDaDuyetId() != null,
                phienBan.get(),
                banDaDuyet
        ));
    }

    private Optional<NoiDungPhienBan> docNoiDungPhienBan(UUID tinId, UUID phienBanId) {
        Optional<NoiDungPhienBan> goc = jdbcTemplate.query(
                SQL_DOC_PHIEN_BAN,
                (rs, rowNum) -> {
                    Timestamp thoiDiemQuyetDinh = rs.getTimestamp("thoi_diem_quyet_dinh");
                    return new NoiDungPhienBan(
                            rs.getObject("id", UUID.class),
                            rs.getInt("so_phien_ban"),
                            rs.getString("tieu_de"),
                            rs.getString("mo_ta"),
                            rs.getString("loai_giao_dich"),
                            rs.getString("loai_bds"),
                            rs.getBigDecimal("gia_goc"),
                            rs.getString("don_vi_gia"),
                            rs.getBigDecimal("dien_tich_m2"),
                            rs.getString("dia_chi"),
                            rs.getString("khu_vuc"),
                            rs.getBigDecimal("vi_do"),
                            rs.getBigDecimal("kinh_do"),
                            rs.getString("ket_qua_so_bo"),
                            rs.getString("ket_qua_kiem_duyet"),
                            rs.getObject("nguoi_duyet_id", UUID.class),
                            thoiDiemQuyetDinh == null ? null : thoiDiemQuyetDinh.toInstant(),
                            rs.getString("ly_do_tu_choi"),
                            rs.getTimestamp("tao_luc").toInstant(),
                            List.of()
                    );
                },
                phienBanId,
                tinId
        ).stream().findFirst();
        if (goc.isEmpty()) {
            return Optional.empty();
        }
        List<AnhPhienBan> anh = jdbcTemplate.query(
                SQL_DOC_ANH_PHIEN_BAN,
                (rs, rowNum) -> new AnhPhienBan(
                        rs.getObject("id", UUID.class),
                        rs.getInt("thu_tu"),
                        rs.getString("khoa_anh_hien_thi")
                ),
                phienBanId
        );
        NoiDungPhienBan g = goc.get();
        return Optional.of(new NoiDungPhienBan(
                g.id(), g.soPhienBan(), g.tieuDe(), g.moTa(), g.loaiGiaoDich(), g.loaiBds(), g.giaGoc(),
                g.donViGia(), g.dienTichM2(), g.diaChi(), g.khuVuc(), g.viDo(), g.kinhDo(), g.ketQuaSoBo(),
                g.ketQuaKiemDuyet(), g.nguoiDuyetId(), g.thoiDiemQuyetDinh(), g.lyDoTuChoi(), g.taoLuc(), anh
        ));
    }

    @Override
    public Optional<KetQuaYeuCauQuyetDinh> timYeuCauQuyetDinh(UUID taiKhoanId, String maYDinh) {
        if (taiKhoanId == null || maYDinh == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                SQL_TIM_YEU_CAU_QUYET_DINH,
                (rs, rowNum) -> {
                    Timestamp thoiDiemQuyetDinh = rs.getTimestamp("thoi_diem_quyet_dinh");
                    return new KetQuaYeuCauQuyetDinh(
                            rs.getString("du_lieu_bam"),
                            new KetQuaQuyetDinh(
                                    rs.getObject("tin_ket_qua_id", UUID.class),
                                    rs.getObject("phien_ban_ket_qua_id", UUID.class),
                                    rs.getString("ket_qua_kiem_duyet"),
                                    rs.getString("trang_thai_ket_qua"),
                                    rs.getObject("nguoi_duyet_id", UUID.class),
                                    thoiDiemQuyetDinh == null ? null : thoiDiemQuyetDinh.toInstant(),
                                    rs.getString("ly_do_tu_choi"),
                                    rs.getLong("phien_ban_cap_nhat_ket_qua")
                            )
                    );
                },
                taiKhoanId,
                maYDinh
        ).stream().findFirst();
    }

    @Override
    public KetQuaQuyetDinh quyetDinhTrongTransaction(DuLieuQuyetDinh duLieu) {
        boolean pheDuyet = KET_QUA_DA_DUYET.equals(duLieu.ketQua());
        if (!pheDuyet && !KET_QUA_BI_TU_CHOI.equals(duLieu.ketQua())) {
            throw new IllegalArgumentException("Kết quả quyết định không hợp lệ: " + duLieu.ketQua());
        }

        // 1. Khóa dòng tin trước mọi thứ khác (404 nếu không có).
        TinKhoaRow tin = jdbcTemplate.query(SQL_KHOA_TIN, (rs, rowNum) -> tinRow(rs), duLieu.tinId())
                .stream().findFirst()
                .orElseThrow(() -> new LoiNghiepVuKiemDuyetException(
                        LoaiLoiKiemDuyet.TIN_KHONG_TON_TAI, "Không tìm thấy tin."));

        // 2. Replay Idempotency-Key sau khi đã giữ khóa, nên yêu cầu đồng thời cùng mã luôn thấy bản đã commit.
        Optional<KetQuaYeuCauQuyetDinh> daLuu = timYeuCauQuyetDinh(duLieu.nguoiDuyetId(), duLieu.maYDinh());
        if (daLuu.isPresent()) {
            if (!daLuu.get().duLieuBam().equals(duLieu.duLieuBam())) {
                throw new LoiNghiepVuKiemDuyetException(
                        LoaiLoiKiemDuyet.XUNG_DOT_MA_Y_DINH,
                        "Mã Idempotency-Key đã được sử dụng cho một nội dung yêu cầu khác."
                );
            }
            return daLuu.get().ketQua();
        }

        // 3. Kiểm tra điều kiện theo đúng thứ tự đặc tả.
        if (TRANG_THAI_DA_XOA.equals(tin.trangThai())) {
            throw new LoiNghiepVuKiemDuyetException(LoaiLoiKiemDuyet.TIN_DA_XOA, "Tin đã bị chủ tin xóa.");
        }
        if (TRANG_THAI_BI_GO.equals(tin.trangThai())) {
            throw new LoiNghiepVuKiemDuyetException(LoaiLoiKiemDuyet.TIN_DA_BI_GO, "Tin đã bị gỡ do vi phạm.");
        }
        PhienBanQuyetDinhRow phienBan = jdbcTemplate.query(
                SQL_DOC_PHIEN_BAN_DE_KIEM_TRA,
                (rs, rowNum) -> new PhienBanQuyetDinhRow(
                        rs.getObject("tin_id", UUID.class),
                        rs.getString("ket_qua_kiem_duyet")
                ),
                duLieu.phienBanId()
        ).stream().findFirst().orElse(null);
        if (phienBan == null || !duLieu.tinId().equals(phienBan.tinId())) {
            throw new LoiNghiepVuKiemDuyetException(
                    LoaiLoiKiemDuyet.PHIEN_BAN_KHONG_THUOC_TIN, "Không tìm thấy phiên bản của tin."
            );
        }
        if (tin.phienBanCapNhat() != duLieu.phienBanCapNhatDaXem()) {
            throw new LoiNghiepVuKiemDuyetException(
                    LoaiLoiKiemDuyet.PHIEN_BAN_CU, "Tin đã thay đổi. Hãy tải lại trước khi thao tác."
            );
        }
        if (!duLieu.phienBanId().equals(tin.phienBanHienTaiId())) {
            throw new LoiNghiepVuKiemDuyetException(
                    LoaiLoiKiemDuyet.PHIEN_BAN_DA_CU, "Chủ tin đã gửi phiên bản mới hơn; phiên bản này không còn chờ duyệt."
            );
        }
        if (!KET_QUA_CHO.equals(phienBan.ketQuaKiemDuyet())) {
            throw new LoiNghiepVuKiemDuyetException(
                    LoaiLoiKiemDuyet.DA_CO_QUYET_DINH, "Phiên bản này đã có quyết định kiểm duyệt."
            );
        }

        // 4. Ghi phiên bản.
        Timestamp thoiDiemSql = Timestamp.from(duLieu.thoiDiem());
        int daGhi = jdbcTemplate.update(
                SQL_GHI_QUYET_DINH_PHIEN_BAN,
                duLieu.ketQua(),
                duLieu.nguoiDuyetId(),
                thoiDiemSql,
                pheDuyet ? null : duLieu.lyDo(),
                duLieu.phienBanId(),
                duLieu.tinId()
        );
        if (daGhi != 1) {
            throw new LoiNghiepVuKiemDuyetException(
                    LoaiLoiKiemDuyet.DA_CO_QUYET_DINH, "Phiên bản này đã có quyết định kiểm duyệt."
            );
        }

        // 5. Ghi tin.
        boolean laBanSua = tin.phienBanDaDuyetId() != null;
        String trangThaiMoi;
        UUID phienBanDaDuyetMoi;
        if (pheDuyet) {
            trangThaiMoi = TRANG_THAI_DA_DUYET;
            phienBanDaDuyetMoi = duLieu.phienBanId();
            jdbcTemplate.update(SQL_PHE_DUYET_TIN, duLieu.phienBanId(), duLieu.tinId());
        } else {
            // Từ chối bản sửa: tin quay về bản đã duyệt cũ; từ chối tin mới: bi_tu_choi.
            trangThaiMoi = laBanSua ? TRANG_THAI_DA_DUYET : TRANG_THAI_BI_TU_CHOI;
            phienBanDaDuyetMoi = tin.phienBanDaDuyetId();
            jdbcTemplate.update(SQL_TRANG_THAI_TIN_SAU_TU_CHOI, trangThaiMoi, duLieu.tinId());
        }
        long phienBanCapNhatMoi = tin.phienBanCapNhat() + 1;

        // 6. Gói đẩy tin: chạy tiếp khi tin hiển thị công khai trở lại.
        if (pheDuyet || laBanSua) {
            chayTiepGoiTamDung(duLieu.tinId(), duLieu.nguoiDuyetId(), thoiDiemSql);
        }

        // 7. Nhật ký kiểm toán và yêu cầu ghi.
        jdbcTemplate.update(
                SQL_INSERT_AUDIT,
                UUID.randomUUID(),
                duLieu.nguoiDuyetId(),
                duLieu.tinId(),
                duLieu.phienBanId(),
                pheDuyet ? "phe_duyet" : "tu_choi",
                duLieuAudit(tin, trangThaiMoi, phienBanDaDuyetMoi, phienBanCapNhatMoi),
                pheDuyet ? null : duLieu.lyDo(),
                thoiDiemSql
        );
        jdbcTemplate.update(
                SQL_INSERT_YEU_CAU_GHI,
                UUID.randomUUID(),
                duLieu.nguoiDuyetId(),
                duLieu.maYDinh(),
                duLieu.duLieuBam(),
                duLieu.tinId(),
                duLieu.phienBanId(),
                trangThaiMoi,
                phienBanCapNhatMoi,
                thoiDiemSql
        );

        // 8. Dựng kết quả từ dữ liệu đã lưu để lần đầu và replay trả cùng một thân phản hồi.
        return jdbcTemplate.query(
                SQL_DOC_QUYET_DINH_PHIEN_BAN,
                (rs, rowNum) -> {
                    Timestamp thoiDiemQuyetDinh = rs.getTimestamp("thoi_diem_quyet_dinh");
                    return new KetQuaQuyetDinh(
                            duLieu.tinId(),
                            duLieu.phienBanId(),
                            rs.getString("ket_qua_kiem_duyet"),
                            trangThaiMoi,
                            rs.getObject("nguoi_duyet_id", UUID.class),
                            thoiDiemQuyetDinh == null ? null : thoiDiemQuyetDinh.toInstant(),
                            rs.getString("ly_do_tu_choi"),
                            phienBanCapNhatMoi
                    );
                },
                duLieu.phienBanId(),
                duLieu.tinId()
        ).stream().findFirst().orElseThrow(() -> new IllegalStateException("Không đọc lại được phiên bản vừa quyết định."));
    }

    private void chayTiepGoiTamDung(UUID tinId, UUID nguoiThucHienId, Timestamp thoiDiemSql) {
        List<GoiTamDungRow> cacGoi = jdbcTemplate.query(
                SQL_KHOA_GOI_TAM_DUNG,
                (rs, rowNum) -> new GoiTamDungRow(rs.getObject("id", UUID.class)),
                tinId
        );
        for (GoiTamDungRow goi : cacGoi) {
            jdbcTemplate.update(SQL_CHAY_TIEP_GOI, thoiDiemSql, goi.id());
            jdbcTemplate.update(
                    SQL_SU_KIEN_CHAY_TIEP,
                    UUID.randomUUID(),
                    goi.id(),
                    nguoiThucHienId,
                    goi.id(),
                    goi.id(),
                    thoiDiemSql
            );
        }
    }

    private static String duLieuAudit(
            TinKhoaRow truocRow,
            String trangThaiSau,
            UUID phienBanDaDuyetSau,
            long phienBanCapNhatSau
    ) {
        Map<String, Object> truoc = new LinkedHashMap<>();
        truoc.put("trangThai", truocRow.trangThai());
        truoc.put("phienBanDaDuyetId", truocRow.phienBanDaDuyetId());
        truoc.put("phienBanCapNhat", truocRow.phienBanCapNhat());

        Map<String, Object> sau = new LinkedHashMap<>();
        sau.put("trangThai", trangThaiSau);
        sau.put("phienBanDaDuyetId", phienBanDaDuyetSau);
        sau.put("phienBanCapNhat", phienBanCapNhatSau);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("truoc", truoc);
        audit.put("sau", sau);
        return ChuanHoaJson.toJsonString(audit);
    }

    private static TinKhoaRow tinRow(ResultSet rs) throws SQLException {
        return new TinKhoaRow(
                rs.getObject("chu_id", UUID.class),
                rs.getString("trang_thai"),
                rs.getObject("phien_ban_hien_tai_id", UUID.class),
                rs.getObject("phien_ban_da_duyet_id", UUID.class),
                rs.getLong("phien_ban_cap_nhat")
        );
    }
}
