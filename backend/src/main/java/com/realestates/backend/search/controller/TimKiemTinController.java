package com.realestates.backend.search.controller;

import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.search.application.TimKiemTinService;
import com.realestates.backend.search.dto.KetQuaTimKiemResponse;
import com.realestates.backend.search.dto.ThamSoTimKiem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/listings: tìm kiếm, lọc, sắp xếp, phân trang công khai (khách được phép). */
@RestController
@RequestMapping("/api/listings")
public class TimKiemTinController {

    private final TimKiemTinService timKiemTinService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public TimKiemTinController(TimKiemTinService timKiemTinService, AuthSessionCookieConfig authSessionCookieConfig) {
        this.timKiemTinService = Objects.requireNonNull(timKiemTinService, "timKiemTinService không được null");
        this.authSessionCookieConfig = Objects.requireNonNull(authSessionCookieConfig, "authSessionCookieConfig không được null");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KetQuaTimKiemResponse> timKiem(
            HttpServletRequest request,
            @RequestParam(value = "loaiGiaoDich", required = false) String loaiGiaoDich,
            @RequestParam(value = "loaiBds", required = false) List<String> loaiBds,
            @RequestParam(value = "khuVuc", required = false) String khuVuc,
            @RequestParam(value = "giaTu", required = false) String giaTu,
            @RequestParam(value = "giaDen", required = false) String giaDen,
            @RequestParam(value = "kieuGia", required = false) String kieuGia,
            @RequestParam(value = "dienTichTu", required = false) String dienTichTu,
            @RequestParam(value = "dienTichDen", required = false) String dienTichDen,
            @RequestParam(value = "sapXep", required = false) String sapXep,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) String limit
    ) {
        String rawSessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                request,
                authSessionCookieConfig.getSessionCookieName()
        );
        KetQuaTimKiemResponse ketQua = timKiemTinService.timKiem(
                rawSessionCookie,
                new ThamSoTimKiem(
                        loaiGiaoDich, loaiBds, khuVuc, giaTu, giaDen, kieuGia,
                        dienTichTu, dienTichDen, sapXep, cursor, limit
                )
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(ketQua);
    }
}
