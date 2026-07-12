package store.mailstock.recovery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import store.mailstock.common.exception.ApiException;
import store.mailstock.recovery.dto.RecoveryCodeEntry;
import store.mailstock.recovery.entity.RecoveryLink;
import store.mailstock.recovery.entity.RecoveryMailbox;
import store.mailstock.recovery.repo.RecoveryLinkRepository;
import store.mailstock.recovery.repo.RecoveryMailboxRepository;

/**
 * Turns a public link token into a list of recent recovery verification codes, read live from the
 * mailbox. Only trusts genuine Google recovery emails ({@code noreply@google.com} + the "Verify
 * recovery email" subject), extracts the 6-digit code with anchors tied to Google's wording (so footer
 * numbers like the year/ZIP can't leak) and the target account, which is masked before it leaves the
 * server.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecoveryCodeService {

    // Keep codes visible for 3 hours so a page refresh still shows the recent history, newest first.
    private static final Duration WINDOW = Duration.ofHours(3);
    private static final int SCAN_MESSAGES = 80;
    private static final int MAX_ENTRIES = 40;

    // Exact sender: forwarding mail comes from "forwarding-noreply@google.com" (which contains this
    // substring) and security alerts from "no-reply@accounts.google.com" — an equals check keeps only
    // the real recovery-code emails. Their subject is "Use 123456 to set up recovery email".
    private static final String GOOGLE_SENDER = "noreply@google.com";
    private static final String RECOVERY_SUBJECT = "recovery email";

    // Anchored on Google's phrasing first, checked against subject + body, e.g.:
    //   subject: "Use 160752 to set up recovery email"
    //   body:    "finish setting up b@ttigerbd.com as a recovery email: 160752"
    // The last pattern is a safe fallback: we already verified sender=noreply@google.com AND the
    // recovery-email subject, and the only 6-digit run in that mail is the code itself (footer numbers
    // are 4-5 digits: 2026, 1600, 94043), so a bare \d{6} can't grab the wrong thing.
    private static final List<Pattern> CODE_PATTERNS = List.of(
            Pattern.compile("Use\\s+(\\d{6})\\s+to set up recovery email", Pattern.CASE_INSENSITIVE),
            Pattern.compile("as a recovery email:?\\s*(\\d{6})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Use this code[^\\d]{0,60}(\\d{6})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(\\d{6})\\b")
    );

    // "aliakber9786@gmail.com wants to use your email address as their recovery email".
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile(
            "([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\s+wants to use your email address",
            Pattern.CASE_INSENSITIVE);

    private final RecoveryLinkRepository links;
    private final RecoveryMailboxRepository mailboxes;
    private final CryptoService crypto;
    private final PopMailReader reader;

    /** Public entry point: resolve a link token to the recent codes in its mailbox (masked accounts). */
    @Transactional(readOnly = true)
    public List<RecoveryCodeEntry> codesForToken(String token) {
        RecoveryLink link = links.findByToken(token)
                .orElseThrow(() -> ApiException.notFound("This link is invalid."));
        if (!link.isUsable())
            throw new ApiException(org.springframework.http.HttpStatus.GONE, "This link has expired.");

        RecoveryMailbox mb = mailboxes.findById(link.getMailboxId())
                .orElseThrow(() -> ApiException.notFound("Mailbox not configured."));
        if (!Boolean.TRUE.equals(mb.getActive()))
            throw ApiException.badRequest("This mailbox is disabled.");

        try {
            // If a link is scoped to one account, still filter to it; blank = show all (the shared case).
            return recentCodes(mb, link.getAccountEmail());
        } catch (RuntimeException e) {
            // POP3/connection failure — don't leak a stack/500 to a public page.
            log.warn("[RECOVERY] mailbox read failed for token {}: {}", token, e.getMessage());
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Couldn't reach the mailbox right now. Please try again in a moment.");
        }
    }

    /** Reads the mailbox and returns recent Google verification codes (newest first), accounts masked. */
    public List<RecoveryCodeEntry> recentCodes(RecoveryMailbox mb, String accountFilter) {
        String password = crypto.decrypt(mb.getPasswordEnc());
        List<PopMailReader.Msg> recent = reader.fetchRecent(mb, password, SCAN_MESSAGES);
        Instant cutoff = Instant.now().minus(WINDOW);
        String filter = accountFilter == null ? "" : accountFilter.trim().toLowerCase();

        List<RecoveryCodeEntry> out = new ArrayList<>();
        for (PopMailReader.Msg m : recent) { // already newest-first
            if (m.receivedAt().isBefore(cutoff)) break;
            if (!isGoogleVerify(m)) continue;
            String code = codeOf(m);
            if (code == null) continue;
            String account = extractAccount(m.body());
            // Account-scoped links (rare) filter to their own account; blank filter shows everything.
            if (!filter.isEmpty() && (account == null || !account.toLowerCase().contains(filter))) continue;
            out.add(new RecoveryCodeEntry(maskEmail(account), code, m.receivedAt()));
            if (out.size() >= MAX_ENTRIES) break;
        }
        return out;
    }

    private static boolean isGoogleVerify(PopMailReader.Msg m) {
        String from = m.from() == null ? "" : m.from().trim().toLowerCase();
        String subj = m.subject() == null ? "" : m.subject().toLowerCase();
        return from.equals(GOOGLE_SENDER) && subj.contains(RECOVERY_SUBJECT);
    }

    /** Pull the 6-digit code, checking the subject first ("Use 775525 to set up recovery email"). */
    private static String codeOf(PopMailReader.Msg m) {
        String subj = m.subject() == null ? "" : m.subject();
        String body = m.body() == null ? "" : m.body();
        return extractCode(subj + "\n" + body);
    }

    private static String extractCode(String text) {
        if (text == null) return null;
        for (Pattern p : CODE_PATTERNS) {
            Matcher matcher = p.matcher(text);
            if (matcher.find()) return matcher.group(1);
        }
        return null;
    }

    private static String extractAccount(String body) {
        if (body == null) return null;
        Matcher m = ACCOUNT_PATTERN.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    /** Mask the local part: first 3 + *** + last 2 (e.g. aliakber9786@gmail.com -> ali***86@gmail.com). */
    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "unknown";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at); // includes '@'
        String masked = local.length() <= 5
                ? local.charAt(0) + "***"
                : local.substring(0, 3) + "***" + local.substring(local.length() - 2);
        return masked + domain;
    }

    /**
     * Admin "test connection": verifies POP3 login and reports how many messages are visible and how
     * many parse as recovery codes — so an empty {@code /r} page can be diagnosed (0 messages = POP3
     * returns nothing; messages but 0 codes = wrong mailbox or unrecognized format). No code/content
     * is returned, only counts and the newest recognized code's time.
     */
    public MailboxDiag diagnose(RecoveryMailbox mb, String plainPassword) {
        List<PopMailReader.Msg> recent = reader.fetchRecent(mb, plainPassword, SCAN_MESSAGES);
        int recognized = 0;
        Instant newest = null;
        for (PopMailReader.Msg m : recent) {
            if (isGoogleVerify(m) && codeOf(m) != null) {
                recognized++;
                if (newest == null) newest = m.receivedAt(); // recent is newest-first
            }
        }
        return new MailboxDiag(PopMailReader.protocolName(mb), recent.size(), recognized, newest);
    }

    /** Test result: protocol used, messages visible, how many parse as codes, newest code time. */
    public record MailboxDiag(String protocol, int messages, int recognizedCodes, Instant newestCodeAt) {}
}
