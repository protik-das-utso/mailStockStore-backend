package store.mailstock.recovery.dto;

import java.time.Instant;

/**
 * One recent recovery verification code shown on the public page. The account email is masked (e.g.
 * {@code ali***86@gmail.com}) so a shared mailbox link never reveals full addresses — each person
 * recognizes their own row by the mask + timestamp.
 */
public record RecoveryCodeEntry(String maskedAccount, String code, Instant receivedAt) {}
