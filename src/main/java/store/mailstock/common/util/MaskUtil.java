package store.mailstock.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Privacy masking for values shown to the public before purchase.
 */
public final class MaskUtil {

    private MaskUtil() {}

    /**
     * Mask an email address using a stable hash so sequential emails can't be guessed.
     * Instead of "jin***17@gmail.com" revealing the prefix, we show "j***a4b9@gmail.com"
     * where "a4b9" is a short hash of the full email. Each email gets a unique, unpredictable
     * mask that stays consistent but reveals no sequence information.
     * Non-email strings are masked generically (first char kept, rest hidden).
     */
    public static String maskEmail(String value) {
        if (value == null || value.isBlank()) return value;
        int at = value.indexOf('@');
        if (at <= 0) return maskGeneric(value);

        String local = value.substring(0, at);
        String domain = value.substring(at); // includes '@'

        // Use first character of local part + 4-char hash of full email + domain
        String first = local.substring(0, 1);
        String hash = shortHash(value.toLowerCase().trim());
        return first + "***" + hash + domain;
    }

    /**
     * Generate a short, stable 4-character hex hash from a string.
     * Uses SHA-256 and takes the first 4 hex chars for brevity.
     */
    private static String shortHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            // Take first 2 bytes (4 hex chars) for a compact hash
            return String.format("%02x%02x", digest[0], digest[1]);
        } catch (Exception e) {
            // Fallback to simple hashCode if SHA fails
            return String.format("%04x", input.hashCode() & 0xFFFF).substring(0, 4);
        }
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
