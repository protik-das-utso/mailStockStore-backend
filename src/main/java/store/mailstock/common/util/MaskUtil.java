package store.mailstock.common.util;

/**
 * Privacy masking for values shown to the public before purchase.
 */
public final class MaskUtil {

    private MaskUtil() {}

    /**
     * Mask an email address, keeping the first 3 and last 2 characters of the local
     * part (before '@') and the full domain. Examples:
     *   utso2305101197@diu.edu.bd -> uts***97@diu.edu.bd
     *   kevin@gmail.com           -> k***@gmail.com   (short local part)
     * Non-email strings are masked generically (first 3 kept, rest hidden).
     */
    public static String maskEmail(String value) {
        if (value == null || value.isBlank()) return value;
        int at = value.indexOf('@');
        if (at <= 0) return maskGeneric(value);

        String local = value.substring(0, at);
        String domain = value.substring(at); // includes '@'

        if (local.length() <= 5) {
            // Too short to keep 3 + 2 without revealing most of it: keep first char only.
            String first = local.substring(0, 1);
            return first + "***" + domain;
        }
        String head = local.substring(0, 3);
        String tail = local.substring(local.length() - 2);
        return head + "***" + tail + domain;
    }

    private static String maskGeneric(String value) {
        if (value.length() <= 3) return "***";
        return value.substring(0, 3) + "***";
    }

    /**
     * Mask an opaque identifier (transaction id, order id, txid) for logging: hide all but
     * the last 4 characters. Short/blank values become "***". Never logs the full id.
     *   0x9fA3...c71d3b -> ***3b (last 4 kept)
     */
    public static String maskId(String value) {
        if (value == null || value.isBlank()) return "***";
        if (value.length() <= 4) return "***";
        return "***" + value.substring(value.length() - 4);
    }
}
