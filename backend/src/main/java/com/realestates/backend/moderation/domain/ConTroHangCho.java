package com.realestates.backend.moderation.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Con trỏ keyset mờ của hàng chờ: base64url(guiLuc|phienBanId). */
public record ConTroHangCho(Instant guiLuc, UUID phienBanId) {

    private static final Pattern UUID_CHUAN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

    public String maHoa() {
        String nguon = guiLuc.toString() + "|" + phienBanId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nguon.getBytes(StandardCharsets.UTF_8));
    }

    /** Trả empty nếu con trỏ sai định dạng. */
    public static Optional<ConTroHangCho> giaiMa(String giaTri) {
        if (giaTri == null || giaTri.isEmpty()) {
            return Optional.empty();
        }
        try {
            String nguon = new String(Base64.getUrlDecoder().decode(giaTri), StandardCharsets.UTF_8);
            int viTri = nguon.indexOf('|');
            if (viTri <= 0 || viTri == nguon.length() - 1) {
                return Optional.empty();
            }
            String phanThoiDiem = nguon.substring(0, viTri);
            String phanId = nguon.substring(viTri + 1);
            if (!UUID_CHUAN.matcher(phanId).matches()) {
                return Optional.empty();
            }
            return Optional.of(new ConTroHangCho(Instant.parse(phanThoiDiem), UUID.fromString(phanId)));
        } catch (RuntimeException ex) {
            // IllegalArgumentException (base64) hoặc DateTimeParseException
            return Optional.empty();
        }
    }
}
