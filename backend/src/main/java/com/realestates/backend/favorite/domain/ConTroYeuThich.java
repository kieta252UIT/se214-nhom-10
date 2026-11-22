package com.realestates.backend.favorite.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Con trỏ keyset mờ của danh sách yêu thích: base64url("y1|luuLuc|tinId|checksum"). luuLuc là text timestamptz do
 * PostgreSQL trả về (giữ nguyên micro giây, kèm múi giờ). checksum chỉ chống hỏng/sửa tay (không ký bảo mật).
 */
public record ConTroYeuThich(String luuLuc, UUID tinId) {

    private static final String PHIEN_BAN = "y1";
    private static final int DO_DAI_TOI_DA = 256;
    private static final Pattern UUID_CHUAN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );
    private static final Pattern THOI_GIAN = Pattern.compile(
            "^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?[+-][0-9]{2}(:[0-9]{2})?$"
    );

    public String maHoa() {
        String than = PHIEN_BAN + "|" + luuLuc + "|" + tinId;
        String nguon = than + "|" + checksum(than);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nguon.getBytes(StandardCharsets.UTF_8));
    }

    /** Trả empty nếu con trỏ sai định dạng hoặc checksum không khớp. */
    public static Optional<ConTroYeuThich> giaiMa(String giaTri) {
        if (giaTri == null || giaTri.isEmpty() || giaTri.length() > DO_DAI_TOI_DA) {
            return Optional.empty();
        }
        try {
            String nguon = new String(Base64.getUrlDecoder().decode(giaTri), StandardCharsets.UTF_8);
            String[] phan = nguon.split("\\|", -1);
            if (phan.length != 4 || !PHIEN_BAN.equals(phan[0])) {
                return Optional.empty();
            }
            String than = String.join("|", phan[0], phan[1], phan[2]);
            if (!checksum(than).equals(phan[3])
                    || !THOI_GIAN.matcher(phan[1]).matches()
                    || !UUID_CHUAN.matcher(phan[2]).matches()) {
                return Optional.empty();
            }
            return Optional.of(new ConTroYeuThich(phan[1], UUID.fromString(phan[2])));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static String checksum(String than) {
        try {
            byte[] bam = MessageDigest.getInstance("SHA-256")
                    .digest(("checksum|" + than).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bam, 0, 4);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Thiếu SHA-256", ex);
        }
    }
}
