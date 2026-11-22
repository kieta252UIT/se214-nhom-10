package com.realestates.backend.favorite.controller;

import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.favorite.application.YeuThichService;
import com.realestates.backend.favorite.dto.DanhSachYeuThichResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PUT/DELETE /api/listings/{listingId}/favorite (204, idempotent; CSRF do CsrfAndOriginProtectionFilter) và
 * GET /api/me/favorites. listingId nhận dạng chuỗi để id sai định dạng được xử lý theo quy ước của service.
 */
@RestController
public class YeuThichController {

    private final YeuThichService yeuThichService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public YeuThichController(YeuThichService yeuThichService, AuthSessionCookieConfig authSessionCookieConfig) {
        this.yeuThichService = Objects.requireNonNull(yeuThichService, "yeuThichService không được null");
        this.authSessionCookieConfig = Objects.requireNonNull(authSessionCookieConfig, "authSessionCookieConfig không được null");
    }

    @PutMapping("/api/listings/{listingId}/favorite")
    public ResponseEntity<Void> luu(HttpServletRequest request, @PathVariable("listingId") String listingId) {
        yeuThichService.luu(docCookie(request), listingId);
        return khongNoiDung();
    }

    @DeleteMapping("/api/listings/{listingId}/favorite")
    public ResponseEntity<Void> bo(HttpServletRequest request, @PathVariable("listingId") String listingId) {
        yeuThichService.bo(docCookie(request), listingId);
        return khongNoiDung();
    }

    @GetMapping(path = "/api/me/favorites", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DanhSachYeuThichResponse> danhSach(
            HttpServletRequest request,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) String limit
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(yeuThichService.danhSach(docCookie(request), cursor, limit));
    }

    private String docCookie(HttpServletRequest request) {
        return AuthCsrfAndMeController.docGiaTriCookie(request, authSessionCookieConfig.getSessionCookieName());
    }

    private static ResponseEntity<Void> khongNoiDung() {
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .build();
    }
}
