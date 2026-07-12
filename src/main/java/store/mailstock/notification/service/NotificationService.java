package store.mailstock.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import store.mailstock.auth.entity.Role;
import store.mailstock.auth.repo.UserRepository;
import store.mailstock.notification.entity.Notification;
import store.mailstock.notification.repo.NotificationRepository;
import store.mailstock.telegram.TelegramNotifier;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repo;
    private final UserRepository users;
    private final TelegramNotifier telegram;
    private final store.mailstock.email.EmailService email;
    private final store.mailstock.email.EmailPreferenceService emailPrefs;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public Notification notify(Long userId, String type, String title, String body) {
        return notify(userId, type, title, body, null, null);
    }

    @Transactional
    public Notification notify(Long userId, String type, String title, String body, String link) {
        return notify(userId, type, title, body, link, null);
    }

    /**
     * Single delivery path for a user-facing event: in-app notification, Telegram mirror, and — only
     * if the admin has switched that event's email on — an email. {@code preview} is an optional
     * snippet (e.g. the reply text) shown in the email body.
     */
    @Transactional
    public Notification notify(Long userId, String type, String title, String body, String link, String preview) {
        Notification n = repo.save(Notification.builder()
                .userId(userId).type(type).title(title).body(body).link(link).build());
        telegram.push(userId, title, body); // best-effort mirror to Telegram if linked
        maybeEmail(userId, type, title, body, link, preview);
        return n;
    }

    /** Emails the user for this event when the admin has that toggle on. Best-effort, never throws. */
    private void maybeEmail(Long userId, String type, String title, String body, String link, String preview) {
        if (!emailPrefs.enabled(type)) return;
        users.findById(userId).ifPresent(u -> {
            if (u.getEmail() == null || u.getEmail().isBlank()) return;
            java.util.Map<String, Object> model = new java.util.HashMap<>();
            model.put("heading", title);
            model.put("message", body == null ? "" : body);
            if (preview != null && !preview.isBlank())
                model.put("preview", preview.length() > 160 ? preview.substring(0, 160) + "…" : preview);
            model.put("link", link == null || link.isBlank() ? frontendUrl : frontendUrl + link);
            email.sendGeneric(u.getEmail(), title, "notification", model); // @Async, best-effort
        });
    }

    @Transactional
    public void notifyAdmins(String type, String title, String body) {
        notifyAdmins(type, title, body, null, null);
    }

    @Transactional
    public void notifyAdmins(String type, String title, String body, String link, String preview) {
        List<Long> adminIds = users.findAll().stream()
                .filter(u -> u.hasRole(Role.ADMIN)).map(u -> u.getId()).toList();
        for (Long id : adminIds) notify(id, type, title, body, link, preview);
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(Long userId, Pageable p) {
        return repo.findByUserIdOrderByIdDesc(userId, p);
    }

    @Transactional(readOnly = true)
    public long unread(Long userId) { return repo.countByUserIdAndReadAtIsNull(userId); }

    @Transactional
    public void markRead(Long userId, Long id) {
        repo.findById(id).filter(n -> n.getUserId().equals(userId)).ifPresent(n -> {
            n.setReadAt(Instant.now()); repo.save(n);
        });
    }

    @Transactional
    public void markAllRead(Long userId) {
        var all = repo.findByUserIdOrderByIdDesc(userId, Pageable.unpaged()).getContent();
        for (Notification n : all) if (n.getReadAt() == null) { n.setReadAt(Instant.now()); repo.save(n); }
    }
}
