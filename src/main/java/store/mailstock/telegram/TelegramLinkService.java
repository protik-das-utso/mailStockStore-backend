package store.mailstock.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import store.mailstock.auth.entity.User;
import store.mailstock.auth.repo.UserRepository;
import store.mailstock.common.exception.ApiException;

/**
 * Binds a Telegram chat to a MailStock account via a one-time code: {@link #generateLinkCode} on
 * the website + {@link #consumeCode} in the bot. No passwords or JWTs are stored; only the
 * chat↔user binding is persisted.
 */
@Service
@RequiredArgsConstructor
public class TelegramLinkService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // no ambiguous chars
    private static final Duration CODE_TTL = Duration.ofMinutes(10);

    private final TelegramLinkRepository links;
    private final TelegramLinkCodeRepository codes;
    private final UserRepository users;

    /** Website-side: mint a short one-time code the user pastes into the bot. Persisted to the DB
     *  so it survives a restart and works even if a different node runs the bot poller. */
    @Transactional
    public String generateLinkCode(Long userId) {
        codes.deleteExpired(Instant.now());
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(ALPHABET[RNG.nextInt(ALPHABET.length)]);
        String code = sb.toString();
        codes.save(TelegramLinkCode.builder()
                .code(code).userId(userId).expiresAt(Instant.now().plus(CODE_TTL)).build());
        return code;
    }

    /** Bot-side: redeem a code and bind the chat. Returns the linked user. */
    @Transactional
    public User consumeCode(String code, Long chatId) {
        if (code == null || code.isBlank())
            throw ApiException.badRequest("No code provided. Send it as `/link YOURCODE`.");
        TelegramLinkCode e = codes.findById(code.trim().toUpperCase()).orElse(null);
        if (e == null)
            throw ApiException.badRequest("Invalid code. Generate one on the website → Profile → Connect Telegram.");
        codes.delete(e); // single-use: consume it regardless of expiry outcome below
        if (e.getExpiresAt().isBefore(Instant.now()))
            throw ApiException.badRequest("This code has expired (codes last 10 min). Generate a fresh one on the website.");
        User u = users.findById(e.getUserId())
                .orElseThrow(() -> ApiException.notFound("Account not found for that code."));
        bind(chatId, u.getId());
        return u;
    }

    /** Resolve the account bound to a chat, if any. */
    @Transactional(readOnly = true)
    public Optional<User> resolveUser(Long chatId) {
        return links.findByChatId(chatId).flatMap(l -> users.findById(l.getUserId()));
    }

    @Transactional(readOnly = true)
    public boolean isLinked(Long userId) {
        return links.existsByUserId(userId);
    }

    @Transactional
    public void unlink(Long chatId) {
        links.deleteByChatId(chatId);
    }

    @Transactional
    public void unlinkUser(Long userId) {
        links.findByUserId(userId).ifPresent(links::delete);
    }

    /** Enforce one chat ↔ one user: drop any prior binding on either side, then save. */
    private void bind(Long chatId, Long userId) {
        links.findByUserId(userId).ifPresent(links::delete);
        links.deleteByChatId(chatId);
        links.flush();
        links.save(TelegramLink.builder().chatId(chatId).userId(userId).build());
    }
}
