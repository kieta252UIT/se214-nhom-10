# HƯỚNG DẪN THỰC THI BỘ KIỂM THỬ TỰ ĐỘNG (AUTOMATED TESTING GUIDE)
## DỰ ÁN: HỆ THỐNG TÌM KIẾM VÀ GỢI Ý BẤT ĐỘNG SẢN (SE214 / SE113 / SE347)
### Hạng mục: TASK-07 — Thiết lập bộ chạy unit/API/PostgreSQL và hướng dẫn thực thi
**Tài liệu tham chiếu:** `testplan-v1.1.md` (chuẩn IEEE 829-2008)

---

## 1. TỔNG QUAN VÀ CHIẾN LƯỢC KIỂM THỬ

Hệ thống áp dụng mô hình **Kim tự tháp kiểm thử (Test Pyramid)** theo quy chuẩn tại Mục 7 của `testplan-v1.1.md`:

```
               /\
              /  \     E2E / Browser Tests (Playwright) - 11 bước cốt lõi
             /----\
            /      \   API & Contract Tests (MockMvc, RestAssured) - 30+ endpoints
           /--------\
          /          \ Integration DB Tests (PostgreSQL 17 / TestDatabaseSettings) - Locking/ACID
         /------------\
        /              \ Unit Tests (JUnit 5, AssertJ) - Domain Entities & Price Converters
       /----------------\
```

### Các công cụ kiểm thử chuẩn hóa:
- **Unit Testing (Mục 7.1):** JUnit Jupiter 5, AssertJ, Mockito. Chạy kiểm thử logic Domain POJO độc lập, tốc độ siêu tốc (< 100ms).
- **Database Integration Testing (Mục 7.2):** Spring Boot Test, Flyway Migration, `TestDatabaseSettings`. Chạy trực tiếp trên PostgreSQL 17 thật (qua CI service container hoặc Docker container cục bộ), tuyệt đối không dùng H2 in-memory nhằm bảo toàn các tính năng khóa dòng (`SELECT ... FOR UPDATE`), số thực chính xác `NUMERIC`, ràng buộc CHECK và giao dịch ACID.
- **API Contract & Security Testing (Mục 7.3):** Spring MockMvc, RestAssured, Bean Validation (`@Valid`). Kiểm tra hợp đồng DTO, mã trạng thái HTTP chuẩn, format lỗi RFC 7807 (`ApiErrorResponse`), CSRF và HTTP headers.
- **Concurrency & Race Condition Testing (Mục 7.5):** Java `ExecutorService` phối hợp `CountDownLatch(1)` chạy trên PostgreSQL 17 thật để bắt triệt để các lỗi tranh chấp tài nguyên (đua hạn mức đăng tin TS1-29, 2 KDV duyệt cùng lúc TS1-55). Tự động dọn dẹp dữ liệu thử sau mỗi lần chạy trong khối `try ... finally` để giữ sạch CSDL.
- **Độ bao phủ mã nguồn (Code Coverage):** JaCoCo Maven Plugin đo lường nhánh nghiệp vụ (Branch Coverage $\ge 90\%$). Cấu hình Surefire bảo toàn `@{argLine}` để tạo báo cáo `jacoco.exec`.

---

## 2. YÊU CẦU MÔI TRƯỜNG THỰC THI (ENVIRONMENT PREREQUISITES)

Theo quy định tại Mục 12.1 của `testplan-v1.1.md`:
1. **Java Development Kit (JDK):** Yêu cầu chuẩn **JDK 21 LTS** (`<java.version>21</java.version>`).
   - Nếu máy cài nhiều phiên bản Java, hãy trỏ `JAVA_HOME` về JDK 21 có sẵn trên máy:
     - **Trong Git Bash (MINGW64):**
       ```bash
       export JAVA_HOME="/c/Program Files/Java/jdk-21"
       export PATH="$JAVA_HOME/bin:$PATH"
       java -version
       ```
     - **Trong PowerShell:**
       ```powershell
       $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
       $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
       java -version
       ```
     - **Trong Command Prompt (CMD):**
       ```cmd
       set JAVA_HOME=C:\Program Files\Java\jdk-21
       set PATH=%JAVA_HOME%\bin;%PATH%
       java -version
       ```

2. **Cơ sở dữ liệu PostgreSQL 17 cho kiểm thử:**
   - Khi chạy kiểm thử tích hợp DB hoặc Concurrency, cần có PostgreSQL 17 đang hoạt động.
   - **Lệnh 1 dòng bật nhanh container PostgreSQL 17 bằng Docker:**
     ```bash
     docker run -d --name postgres-test -p 5432:5432 -e POSTGRES_DB=se214_test -e POSTGRES_USER=se214_test -e POSTGRES_PASSWORD=local-ci-only postgres:17
     ```
   - *Lưu ý:* Lệnh chạy Unit Test (`.\mvnw.cmd test -Dtest=*UnitTest`) là POJO thuần túy, **hoạt động ngay mà không cần bật PostgreSQL/Docker**.

3. **Bốn (4) biến môi trường kiểm thử bắt buộc:**
   Lớp cấu hình bảo vệ `TestDatabaseSettings` yêu cầu khai báo đủ 4 biến môi trường và bắt buộc tên database phải kết thúc bằng `_test` để ngăn chặn việc reset nhầm database chính:
   - `ALLOW_TEST_SCHEMA_RESET=true`
   - `DB_TEST_URL=jdbc:postgresql://localhost:5432/se214_test`
   - `DB_TEST_USERNAME=se214_test`
   - `DB_TEST_PASSWORD=local-ci-only`

   **Cách thiết lập 4 biến môi trường trước khi chạy test:**
   - **Trong PowerShell:**
     ```powershell
     $env:ALLOW_TEST_SCHEMA_RESET = "true"
     $env:DB_TEST_URL = "jdbc:postgresql://localhost:5432/se214_test"
     $env:DB_TEST_USERNAME = "se214_test"
     $env:DB_TEST_PASSWORD = "local-ci-only"
     ```
   - **Trong Command Prompt (CMD):**
     ```cmd
     set ALLOW_TEST_SCHEMA_RESET=true
     set DB_TEST_URL=jdbc:postgresql://localhost:5432/se214_test
     set DB_TEST_USERNAME=se214_test
     set DB_TEST_PASSWORD=local-ci-only
     ```
   - **Trong Git Bash / Linux / macOS:**
     ```bash
     export ALLOW_TEST_SCHEMA_RESET=true
     export DB_TEST_URL=jdbc:postgresql://localhost:5432/se214_test
     export DB_TEST_USERNAME=se214_test
     export DB_TEST_PASSWORD=local-ci-only
     ```

4. **Maven Wrapper:** Đã tích hợp sẵn trong thư mục `backend/` (`./mvnw` trên Git Bash/Linux hoặc `.\mvnw.cmd` trên Windows PowerShell/CMD).

---

## 3. CẤU TRÚC THƯ MỤC BỘ KIỂM THỬ

Toàn bộ mã nguồn kiểm thử được bố trí phân lớp rõ ràng trong thư mục `backend/src/test/`:

```
backend/
├── pom.xml                                      # Quản lý dependencies (Flyway, RestAssured, JaCoCo, v.v.)
├── TESTING_GUIDE.md                             # Tài liệu hướng dẫn thực thi này
└── src/
    ├── main/
    │   ├── java/com/realestates/backend/
    │   │   ├── domain/model/PriceConverter.java # Domain Service quy đổi 7 đơn vị giá chuẩn hóa
    │   │   └── ...
    │   └── resources/
    │       ├── application.yaml
    │       └── db/migration/
    │           └── V1__khoi_tao_schema_sprint_1.sql # Flyway migration DDL khởi tạo 12 bảng schema se214
    └── test/
        └── java/com/realestates/backend/
            ├── database/
            │   ├── TestDatabaseSettings.java    # Khởi tạo kết nối DB test chuyên dụng & SpringInitializer
            │   └── FlywayMigrationTests.java    # Kiểm thử migration Flyway V1, V2 rollback và ràng buộc DDL
            ├── unit/
            │   └── PriceConverterUnitTest.java  # Unit test 7 đơn vị giá (TS1-63, TS1-64, BR-03)
            ├── api/
            │   └── ApiContractAndSecurityTest.java # Test hợp đồng API & format lỗi RFC 7807
            ├── integration/
            │   └── PostgresDatabaseIntegrationTest.java # Test tích hợp Flyway & Constraints trên Postgres 17
            ├── concurrency/
            │   └── ConcurrencyRaceConditionTest.java # Test đa luồng CountDownLatch đua hạn mức (TS1-29)
            ├── auth/
            │   └── SupabaseAuthRegistrationTests.java # Test nghiệp vụ đăng ký và xác thực Supabase
            ├── HttpsSameOriginAndApiRoutingTests.java # Test định tuyến HTTPS, Cookie & CORS cùng nguồn
            └── BackendApplicationTests.java     # Kiểm tra Spring ApplicationContext khởi động thành công
```

---

## 4. HƯỚNG DẪN CÂU LỆNH THỰC THI (TEST EXECUTION COMMANDS)

> [!IMPORTANT]
> **ĐIỀU KIỆN TIÊN QUYẾT BẮT BUỘC TRƯỚC KHI CHẠY TEST TÍCH HỢP / API / CONCURRENCY:**
>
> 1. **Khởi chạy container PostgreSQL 17 (nếu chưa chạy):**
>    ```bash
>    docker run -d --name postgres-test -p 5432:5432 -e POSTGRES_DB=se214_test -e POSTGRES_USER=se214_test -e POSTGRES_PASSWORD=local-ci-only postgres:17
>    ```
> 2. **Thiết lập 4 biến môi trường (Tên database BẮT BUỘC có đuôi `_test`):**
>    - **PowerShell:**
>      ```powershell
>      $env:ALLOW_TEST_SCHEMA_RESET = "true"
>      $env:DB_TEST_URL = "jdbc:postgresql://localhost:5432/se214_test"
>      $env:DB_TEST_USERNAME = "se214_test"
>      $env:DB_TEST_PASSWORD = "local-ci-only"
>      ```
>    - **Command Prompt (CMD):**
>      ```cmd
>      set ALLOW_TEST_SCHEMA_RESET=true
>      set DB_TEST_URL=jdbc:postgresql://localhost:5432/se214_test
>      set DB_TEST_USERNAME=se214_test
>      set DB_TEST_PASSWORD=local-ci-only
>      ```
>    - **Git Bash / Linux / macOS:**
>      ```bash
>      export ALLOW_TEST_SCHEMA_RESET=true
>      export DB_TEST_URL=jdbc:postgresql://localhost:5432/se214_test
>      export DB_TEST_USERNAME=se214_test
>      export DB_TEST_PASSWORD=local-ci-only
>      ```

Mở terminal tại thư mục `backend/` và thực thi các lệnh tương ứng:

### 4.1. Chạy toàn bộ các ca kiểm thử (Full Test Suite)
Lệnh này sẽ biên dịch, chạy toàn bộ Unit test, API test, Database Integration test, Flyway migration test và Concurrency test:
```powershell
# Trên Windows PowerShell:
.\mvnw.cmd clean test

# Trên Linux / macOS / Git Bash:
./mvnw clean test
```

### 4.2. Chạy nhanh riêng Unit Test (Không cần bật PostgreSQL)
Dành cho lập trình viên muốn phản hồi tức thì (feedback loop < 3 giây) khi viết logic Domain/POJO:
```powershell
.\mvnw.cmd test -Dtest=*UnitTest
```

### 4.3. Chạy riêng Database Integration Test trên PostgreSQL 17
Kiểm tra Flyway Migration, khóa dòng và các ràng buộc dữ liệu toàn vẹn:
```powershell
.\mvnw.cmd test -Dtest=PostgresDatabaseIntegrationTest
```

### 4.4. Chạy kiểm thử Flyway Migration & Ràng buộc DDL
```powershell
.\mvnw.cmd test -Dtest=FlywayMigrationTests
```

### 4.5. Chạy riêng API Contract & Security Test
Kiểm tra tính tuân thủ mã lỗi JSON theo RFC 7807 và phân quyền truy cập:
```powershell
.\mvnw.cmd test -Dtest=ApiContractAndSecurityTest
```

### 4.6. Chạy kiểm thử đồng thời (Concurrency & Race Condition)
Kích hoạt đa luồng bắn đồng thời để kiểm tra cơ chế chống đua hạn mức đăng tin:
```powershell
.\mvnw.cmd test -Dtest=ConcurrencyRaceConditionTest
```

### 4.7. Xuất báo cáo độ bao phủ mã nguồn (JaCoCo Coverage Report)
```powershell
.\mvnw.cmd clean test jacoco:report
```
- Sau khi chạy xong, mở file báo cáo HTML tại: `backend/target/site/jacoco/index.html`.
- Báo cáo sẽ hiển thị chi tiết độ bao phủ dòng code (Instruction Coverage) và nhánh logic (Branch Coverage).

---

## 5. TÍCH HỢP CI/CD (GITHUB ACTIONS PIPELINE)

Quy trình kiểm thử tự động trên GitHub Actions được đồng bộ hóa trong file `.github/workflows/backend-ci.yml`.
Các bước thực hiện trong CI:
1. Checkout mã nguồn.
2. Thiết lập OpenJDK 21 LTS (`temurin`).
3. Khởi chạy PostgreSQL 17 dưới dạng GitHub Actions Service Container:
   ```yaml
   services:
     postgres:
       image: postgres:17
       env:
         POSTGRES_DB: se214_test
         POSTGRES_USER: se214_test
         POSTGRES_PASSWORD: local-ci-only
       ports:
         - 5432:5432
   ```
4. Cấp tự động 4 biến môi trường:
   - `DB_TEST_URL: jdbc:postgresql://localhost:5432/se214_test`
   - `DB_TEST_USERNAME: se214_test`
   - `DB_TEST_PASSWORD: local-ci-only`
   - `ALLOW_TEST_SCHEMA_RESET: 'true'`
5. Chạy lệnh `./mvnw --batch-mode verify`.
6. Tự động lưu trữ báo cáo Surefire reports làm artifact bằng chứng kiểm thử môn học.

---

## 6. XỬ LÝ SỰ CỐ THƯỜNG GẶP (TROUBLESHOOTING)

1. **Lỗi `IllegalStateException: Test yêu cầu ALLOW_TEST_SCHEMA_RESET=true trên DB thử riêng`:**
   - *Nguyên nhân:* Chưa khai báo biến môi trường cho phép reset schema kiểm thử.
   - *Khắc phục:* Thiết lập biến môi trường `ALLOW_TEST_SCHEMA_RESET=true` (xem hướng dẫn ở Mục 2 & Mục 4).

2. **Lỗi `IllegalStateException: Từ chối chạy test: tên DB thực tế phải kết thúc bằng _test`:**
   - *Nguyên nhân:* Tên database trong chuỗi kết nối `DB_TEST_URL` không kết thúc bằng `_test`.
   - *Khắc phục:* Đảm bảo tên database là `se214_test` (ví dụ `jdbc:postgresql://localhost:5432/se214_test`), tránh trỏ vào database chính.

3. **Lỗi `IllegalStateException: Thiếu biến môi trường thử DB_TEST_...`:**
   - *Nguyên nhân:* Chưa khai báo đủ các biến `DB_TEST_URL`, `DB_TEST_USERNAME`, `DB_TEST_PASSWORD`.
   - *Khắc phục:* Thiết lập đầy đủ 4 biến môi trường như hướng dẫn ở Mục 2 & Mục 4.

4. **Lỗi `Connection to localhost:5432 refused`:**
   - *Nguyên nhân:* Container PostgreSQL 17 chưa được khởi động hoặc cổng 5432 bị chiếm dụng.
   - *Khắc phục:* Chạy lệnh `docker ps` để kiểm tra container `postgres-test` đang chạy; nếu chưa, khởi động lại container theo lệnh ở Mục 2 & Mục 4.

5. **Cần làm sạch cache khi sửa đổi migration SQL:**
   - Chạy lệnh `.\mvnw.cmd clean test-compile` để làm sạch target và biên dịch lại các tài nguyên mới.
