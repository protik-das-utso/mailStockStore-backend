package store.mailstock.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import store.mailstock.common.exception.ApiException;
import store.mailstock.setting.entity.Setting;
import store.mailstock.setting.repo.SettingRepository;

/**
 * Admin-controlled on/off switch for each outgoing email ({@link EmailEvent}), stored in settings so
 * it can be changed from the admin panel with no redeploy. An event the admin has never touched falls
 * back to {@link EmailEvent#defaultOn}.
 */
@Service
@RequiredArgsConstructor
public class EmailPreferenceService {

    private final SettingRepository settings;

    /** Whether the email for this notification type should be sent. Unknown types never email. */
    @Transactional(readOnly = true)
    public boolean enabled(String notificationType) {
        EmailEvent ev = EmailEvent.byType(notificationType);
        if (ev == null) return false;
        return settings.findById(ev.settingKey())
                .map(s -> "true".equalsIgnoreCase(s.getValue() == null ? "" : s.getValue().trim()))
                .orElse(ev.defaultOn);
    }

    /** Every toggle with its current state — powers the admin panel. */
    @Transactional(readOnly = true)
    public List<EmailToggle> list() {
        return java.util.Arrays.stream(EmailEvent.values())
                .map(ev -> new EmailToggle(ev.name(), ev.settingKey(), ev.label, ev.description,
                        ev.audience.name(), enabled(ev.name()), ev.defaultOn))
                .toList();
    }

    /** Flip one toggle. {@code event} is the EmailEvent name, e.g. SUPPORT_REPLY. */
    @Transactional
    public EmailToggle set(String event, boolean on) {
        EmailEvent ev = EmailEvent.byType(event);
        if (ev == null) throw ApiException.notFound("Unknown email event: " + event);
        settings.save(Setting.builder()
                .key(ev.settingKey()).value(String.valueOf(on)).updatedAt(Instant.now()).build());
        return new EmailToggle(ev.name(), ev.settingKey(), ev.label, ev.description,
                ev.audience.name(), on, ev.defaultOn);
    }

    public record EmailToggle(String event, String key, String label, String description,
                              String audience, boolean enabled, boolean defaultOn) {}
}
