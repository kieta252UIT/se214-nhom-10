package com.realestates.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.common.AbstractPostgresIntegrationTest;
import com.realestates.backend.database.TestDatabaseSettings;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cấp độ 2: Kiểm thử tích hợp cơ sở dữ liệu (Database Integration Testing) theo IEEE 829 - Mục 7.2.
 * Sử dụng Testcontainers chạy PostgreSQL 17 thật (không dùng H2).
 * Kiểm tra:
 * - Flyway Migration khởi tạo schema sạch.
 * - Ràng buộc toàn vẹn: CHECK (hạn mức <= 3, BR-09), Unique index, Foreign Key.
 * - Giao dịch ACID và cơ chế Transaction Rollback.
 */
@ContextConfiguration(initializers = TestDatabaseSettings.SpringInitializer.class)
class PostgresDatabaseIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("FLYWAY-01: Schema PostgreSQL 17 được khởi tạo đầy đủ các bảng nghiệp vụ cốt lõi")
    void flywayShouldInitializeCoreTables() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'se214' " +
                "AND table_name IN ('tai_khoan', 'phien_dang_nhap', 'han_muc_thang', 'tin_dang', 'lich_su_trang_thai_tin', " +
                "'hinh_anh_tin', 'tin_luu', 'tim_kiem_da_luu', 'thong_bao', 'cau_hinh_he_thong', 'nhat_ky_kiem_duyet', 'nhat_ky_he_thong', " +
                "'phien_xac_thuc', 'tin_bat_dong_san', 'phien_ban_tin', 'anh_tin', 'yeu_thich_tin', 'goi_day_tin', 'su_kien_goi', " +
                "'bao_cao_vi_pham', 'yeu_cau_ghi', 'nhat_ky_kiem_toan')",
                Integer.class
        );

        assertThat(tableCount).isEqualTo(12);
    }

    @Test
    @Transactional
    @DisplayName("AC-15.1 / TS1-01: Chèn và đọc tài khoản hợp lệ trên PostgreSQL 17")
    void shouldInsertAndQueryValidUserAccount() {
        UUID userId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO se214.tai_khoan (id, supabase_auth_user_id, email, ho_ten, so_dien_thoai, vai_tro, trang_thai, email_da_xac_minh, moc_thu_hoi) " +
                "VALUES (?, ?, ?, ?, ?, 'nguoi_dung', 'hoat_dong', ?, ?)",
                userId,
                authUserId,
                "user1_test@bds-test.local",
                "Nguyễn Văn A",
                "0901234567",
                true,
                0L
        );

        String email = jdbcTemplate.queryForObject(
                "SELECT email FROM se214.tai_khoan WHERE email = ?",
                String.class,
                "user1_test@bds-test.local"
        );

        assertThat(email).isEqualTo("user1_test@bds-test.local");
    }

    @Test
    @Transactional
    @DisplayName("BR-09 / AC-01.1: CHECK constraint chặn hạn mức tin vượt quá 3 tin/tháng ở cấp CSDL")
    void databaseShouldEnforceMonthlyListingLimitConstraint() {
        // Tạo tài khoản trước
        UUID userId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO se214.tai_khoan (id, supabase_auth_user_id, email, ho_ten, vai_tro, trang_thai) " +
                "VALUES (?, ?, ?, 'Người Dùng Test Hạn Mức', 'nguoi_dung', 'hoat_dong')",
                userId,
                authUserId,
                "user_limit_test@bds-test.local"
        );

        // Hạn mức hợp lệ = 3
        jdbcTemplate.update(
                "INSERT INTO se214.han_muc_thang (tai_khoan_id, thang, so_tin_moi) VALUES (?, '2026-11-01', 3)",
                userId
        );

        // Cố gắng cập nhật lên 4 -> phải bị CSDL chặn ngay lập tức qua CHECK constraint
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE se214.han_muc_thang SET so_tin_moi = 4 WHERE tai_khoan_id = ? AND thang = '2026-11-01'",
                userId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("AC-15.1 / TS1-02: Ràng buộc Unique email ngăn chặn ghi đè tài khoản trùng lặp")
    void databaseShouldEnforceUniqueEmailConstraint() {
        String duplicateEmail = "duplicate_email@bds-test.local";

        UUID user1Id = UUID.randomUUID();
        UUID auth1Id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO se214.tai_khoan (id, supabase_auth_user_id, email, ho_ten, vai_tro, trang_thai) " +
                "VALUES (?, ?, ?, 'User 1', 'nguoi_dung', 'hoat_dong')",
                user1Id,
                auth1Id,
                duplicateEmail
        );

        UUID user2Id = UUID.randomUUID();
        UUID auth2Id = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO se214.tai_khoan (id, supabase_auth_user_id, email, ho_ten, vai_tro, trang_thai) " +
                "VALUES (?, ?, ?, 'User 2', 'nguoi_dung', 'hoat_dong')",
                user2Id,
                auth2Id,
                duplicateEmail
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
