package com.realestates.backend.search.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Con trỏ keyset mờ của trang tìm kiếm: base64url("v1|sapXep|khoa|tinId|dauVanTayLoc|checksum").
 * khoa là giá trị khóa sắp xếp dạng text do PostgreSQL trả về (lossless). checksum chỉ chống hỏng/sửa tay (không ký bảo mật).
 */
public record ConTroTimKiem(SapXepTin sapXep, String khoa, UUID tinId, String dauVanTayLoc) {

    private static final String PHIEN_BAN = "v1";
    private static final int DO_DAI_TOI_DA = 512;
    private static final Pattern UUID_CHUAN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );
    private static final Pattern KHOA_SO = Pattern.compile("^[0-9]{1,40}(\\.[0-9]{1,40})?$");
    private static final Pattern KHOA_THOI_GIAN = Pattern.compile(
            "^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?[+-][0-9]{2}(:[0-9]{2})?$"
    );
    private static final Pattern DAU_VAN_TAY = Pattern.compile("^[0-9a-f]{16}$");

    public String maHoa() {
        String than = PHIEN_BAN + "|" + sapXep.getGiaTri() + "|" + khoa + "|" + tinId + "|" + dauVanTayLoc;
        String nguon = than + "|" + checksum(than);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nguon.getBytes(StandardCharsets.UTF_8));
    }

    /** Trả empty nếu con trỏ sai định dạng hoặc checksum không khớp. */
    public static Optional<ConTroTimKiem> giaiMa(String giaTri) {
        if (giaTri == null || giaTri.isEmpty() || giaTri.length() > DO_DAI_TOI_DA) {
            return Optional.empty();
        }
        try {
            String nguon = new String(Base64.getUrlDecoder().decode(giaTri), StandardCharsets.UTF_8);
            String[] phan = nguon.split("\\|", -1);
            if (phan.length != 6 || !PHIEN_BAN.equals(phan[0])) {
                return Optional.empty();
            }
            String than = String.join("|", phan[0], phan[1], phan[2], phan[3], phan[4]);
            if (!checksum(than).equals(phan[5])) {
                return Optional.empty();
            }
            Optional<SapXepTin> sapXep = SapXepTin.tuGiaTri(phan[1]);
            if (sapXep.isEmpty() || !UUID_CHUAN.matcher(phan[3]).matches() || !DAU_VAN_TAY.matcher(phan[4]).matches()) {
                return Optional.empty();
            }
            boolean khoaHopLe = sapXep.get().laTheoThoiGian()
                    ? KHOA_THOI_GIAN.matcher(phan[2]).matches()
                    : KHOA_SO.matcher(phan[2]).matches();
            if (!khoaHopLe) {
                return Optional.empty();
            }
            return Optional.of(new ConTroTimKiem(sapXep.get(), phan[2], UUID.fromString(phan[3]), phan[4]));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static String checksum(String than) {
        return BoLocTimKiem.bamRutGon("checksum|" + than).substring(0, 8);
    }
}
