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

    @Transactional
    public Notification notify(Long userId, String type, String title, String body) {
        Notification n = repo.save(Notification.builder()
                .userId(userId).type(type).title(title).body(body).build());
        telegram.push(userId, title, body); // best-effort mirror to Telegram if linked
        return n;
    }

    @Transactional
    public Notification notify(Long userId, String type, String title, String body, String link) {
        Notification n = repo.save(Notification.builder()
                .userId(userId).type(type).title(title).body(body).link(link).build());
        telegram.push(userId, title, body);
        return n;
    }

    @Transactional
    public void notifyAdmins(String type, String title, String body) {
        List<Long> adminIds = users.findAll().stream()
                .filter(u -> u.hasRole(Role.ADMIN)).map(u -> u.getId()).toList();
        for (Long id : adminIds) notify(id, type, title, body);
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
