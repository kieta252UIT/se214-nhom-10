package com.realestates.backend.moderation.controller;

import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.moderation.application.KiemDuyetService;
import com.realestates.backend.moderation.dto.ChiTietPhienBanResponse;
import com.realestates.backend.moderation.dto.HangChoResponse;
import com.realestates.backend.moderation.dto.KetQuaQuyetDinh;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/moderation")
public class KiemDuyetController {

    private final KiemDuyetService kiemDuyetService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public KiemDuyetController(KiemDuyetService kiemDuyetService, AuthSessionCookieConfig authSessionCookieConfig) {
        this.kiemDuyetService = Objects.requireNonNull(kiemDuyetService, "kiemDuyetService không được null");
        this.authSessionCookieConfig = Objects.requireNonNull(authSessionCookieConfig, "authSessionCookieConfig không được null");
    }

    @GetMapping(value = "/queue", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HangChoResponse> layHangCho(
            HttpServletRequest request,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) String limit
    ) {
        HangChoResponse ketQua = kiemDuyetService.layHangCho(docCookiePhien(request), cursor, limit);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(ketQua);
    }

    @GetMapping(value = "/listings/{listingId}/versions/{versionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChiTietPhienBanResponse> layChiTietPhienBan(
            HttpServletRequest request,
            @PathVariable("listingId") UUID tinId,
            @PathVariable("versionId") UUID phienBanId
    ) {
        ChiTietPhienBanResponse ketQua = kiemDuyetService.layChiTietPhienBan(docCookiePhien(request), tinId, phienBanId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .header(HttpHeaders.ETAG, "\"" + ketQua.phienBanCapNhat() + "\"")
                .body(ketQua);
    }

    @PostMapping(value = "/listings/{listingId}/versions/{versionId}/approve", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KetQuaQuyetDinh> pheDuyet(
            HttpServletRequest request,
            @PathVariable("listingId") UUID tinId,
            @PathVariable("versionId") UUID phienBanId,
            @RequestHeader(value = TinDangService.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(value = KiemDuyetService.HEADER_IF_MATCH, required = false) String ifMatch
    ) {
        KetQuaQuyetDinh ketQua = kiemDuyetService.pheDuyet(
                docCookiePhien(request), tinId, phienBanId, idempotencyKey, ifMatch
        );
        return phanHoiQuyetDinh(ketQua);
    }

    @PostMapping(value = "/listings/{listingId}/versions/{versionId}/reject", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KetQuaQuyetDinh> tuChoi(
            HttpServletRequest request,
            @PathVariable("listingId") UUID tinId,
            @PathVariable("versionId") UUID phienBanId,
            @RequestHeader(value = TinDangService.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(value = KiemDuyetService.HEADER_IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        KetQuaQuyetDinh ketQua = kiemDuyetService.tuChoi(
                docCookiePhien(request), tinId, phienBanId, idempotencyKey, ifMatch, body
        );
        return phanHoiQuyetDinh(ketQua);
    }

    private String docCookiePhien(HttpServletRequest request) {
        return AuthCsrfAndMeController.docGiaTriCookie(request, authSessionCookieConfig.getSessionCookieName());
    }

    private static ResponseEntity<KetQuaQuyetDinh> phanHoiQuyetDinh(KetQuaQuyetDinh ketQua) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .header(HttpHeaders.ETAG, "\"" + ketQua.phienBanCapNhat() + "\"")
                .body(ketQua);
    }
}
