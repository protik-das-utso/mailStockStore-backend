package store.mailstock.recovery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

import store.mailstock.common.exception.ApiException;
import store.mailstock.recovery.dto.RecoveryDtos.LinkRequest;
import store.mailstock.recovery.dto.RecoveryDtos.LinkView;
import store.mailstock.recovery.dto.RecoveryDtos.MailboxRequest;
import store.mailstock.recovery.dto.RecoveryDtos.MailboxView;
import store.mailstock.recovery.dto.RecoveryDtos.SellerMailboxView;
import store.mailstock.recovery.entity.RecoveryLink;
import store.mailstock.recovery.entity.RecoveryMailbox;
import store.mailstock.recovery.repo.RecoveryLinkRepository;
import store.mailstock.recovery.repo.RecoveryMailboxRepository;

/** Admin-only management of recovery mailboxes and the public links they back. */
@Service
@RequiredArgsConstructor
public class RecoveryAdminService {

    private static final int DEFAULT_LINK_TTL_DAYS = 7;
    private final SecureRandom random = new SecureRandom();

    private final RecoveryMailboxRepository mailboxes;
    private final RecoveryLinkRepository links;
    private final CryptoService crypto;
    private final RecoveryCodeService codeService;

    @Value("${app.frontend-url}") private String frontendUrl;

    // ---- mailboxes ----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MailboxView> listMailboxes() {
        return mailboxes.findAllByOrderByCreatedAtDesc().stream().map(MailboxView::of).toList();
    }

    /** Active mailboxes offered to sellers (email + label only) — the recovery emails they can add. */
    @Transactional(readOnly = true)
    public List<SellerMailboxView> listActiveForSellers() {
        return mailboxes.findAllByOrderByCreatedAtDesc().stream()
                .filter(m -> Boolean.TRUE.equals(m.getActive()))
                .map(SellerMailboxView::of).toList();
    }

    @Transactional
    public MailboxView createMailbox(MailboxRequest r) {
        if (r.password() == null || r.password().isBlank())
            throw ApiException.badRequest("Password is required for a new mailbox.");
        RecoveryMailbox m = RecoveryMailbox.builder()
                .label(r.label()).email(r.email()).host(r.host())
                .port(r.port() == null ? 995 : r.port())
                .ssl(r.ssl() == null || r.ssl())
                .username(r.username())
                .passwordEnc(crypto.encrypt(r.password()))
                .active(r.active() == null || r.active())
                .build();
        return MailboxView.of(mailboxes.save(m));
    }

    @Transactional
    public MailboxView updateMailbox(Long id, MailboxRequest r) {
        RecoveryMailbox m = mailboxes.findById(id)
                .orElseThrow(() -> ApiException.notFound("Mailbox not found."));
        m.setLabel(r.label());
        m.setEmail(r.email());
        m.setHost(r.host());
        m.setPort(r.port() == null ? 995 : r.port());
        m.setSsl(r.ssl() == null || r.ssl());
        m.setUsername(r.username());
        if (r.active() != null) m.setActive(r.active());
        // Blank password on update = keep the stored one; only re-encrypt when a new value is supplied.
        if (r.password() != null && !r.password().isBlank()) m.setPasswordEnc(crypto.encrypt(r.password()));
        m.setUpdatedAt(Instant.now());
        return MailboxView.of(mailboxes.save(m));
    }

    @Transactional
    public void deleteMailbox(Long id) {
        if (!mailboxes.existsById(id)) throw ApiException.notFound("Mailbox not found.");
        mailboxes.deleteById(id); // links cascade (FK ON DELETE CASCADE)
    }

    /** Verify the stored (or a just-typed) password logs in over POP3, and report what it can read. */
    @Transactional(readOnly = true)
    public RecoveryCodeService.MailboxDiag testMailbox(Long id, String plainOverride) {
        RecoveryMailbox m = mailboxes.findById(id)
                .orElseThrow(() -> ApiException.notFound("Mailbox not found."));
        String password = (plainOverride != null && !plainOverride.isBlank())
                ? plainOverride : crypto.decrypt(m.getPasswordEnc());
        return codeService.diagnose(m, password);
    }

    // ---- links --------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<LinkView> listLinks() {
        return links.findAllByOrderByCreatedAtDesc().stream().map(l -> LinkView.of(l, urlFor(l))).toList();
    }

    @Transactional
    public LinkView createLink(LinkRequest r) {
        if (!mailboxes.existsById(r.mailboxId()))
            throw ApiException.notFound("Mailbox not found.");
        int ttl = r.expiresInDays() == null ? DEFAULT_LINK_TTL_DAYS : r.expiresInDays();
        RecoveryLink l = RecoveryLink.builder()
                .token(newToken())
                .mailboxId(r.mailboxId())
                .accountEmail(r.accountEmail())
                .inventoryId(r.inventoryId())
                .orderItemId(r.orderItemId())
                .expiresAt(ttl <= 0 ? null : Instant.now().plus(ttl, ChronoUnit.DAYS))
                .build();
        return LinkView.of(links.save(l), urlFor(l));
    }

    /**
     * Seller/self-service: mint (or reuse) a public link for the account being submitted, against the
     * mailbox that matches the recovery email shown on the page. No mailbox id or credentials are ever
     * exposed to the seller — they only get back the public URL. Reuses an existing usable link for the
     * same account+mailbox so repeated clicks don't pile up rows.
     */
    @Transactional
    public LinkView selfServiceLink(String accountEmail, String recoveryEmail) {
        // Blank account = mailbox-scoped link (returns the latest code regardless of account) — needed
        // because a seller creating an account has no finalized address yet when they need the code.
        String acct = (accountEmail == null) ? "" : accountEmail.trim();

        RecoveryMailbox mb = mailboxes.findFirstByEmailIgnoreCaseAndActiveTrue(recoveryEmail)
                .or(mailboxes::findFirstByActiveTrueOrderByCreatedAtAsc)
                .orElseThrow(() -> ApiException.badRequest(
                        "No recovery mailbox is configured yet. Ask an admin to add it in the admin panel."));

        RecoveryLink reusable = links
                .findFirstByAccountEmailIgnoreCaseAndMailboxIdOrderByCreatedAtDesc(acct, mb.getId())
                .filter(RecoveryLink::isUsable)
                .orElse(null);
        if (reusable != null) return LinkView.of(reusable, urlFor(reusable));

        RecoveryLink l = RecoveryLink.builder()
                .token(newToken())
                .mailboxId(mb.getId())
                .accountEmail(acct)
                .expiresAt(Instant.now().plus(DEFAULT_LINK_TTL_DAYS, ChronoUnit.DAYS))
                .build();
        return LinkView.of(links.save(l), urlFor(l));
    }

    @Transactional
    public void revokeLink(Long id) {
        RecoveryLink l = links.findById(id).orElseThrow(() -> ApiException.notFound("Link not found."));
        l.setRevoked(true);
        links.save(l);
    }

    public String urlFor(RecoveryLink l) {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        // Query-param form: the frontend is a Next static export (`output: export`) and can't serve a
        // runtime-dynamic /r/<token> path segment. Trailing slash matches next.config trailingSlash.
        return base + "/r/?token=" + l.getToken();
    }

    private String newToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
