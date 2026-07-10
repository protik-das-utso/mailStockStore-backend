package store.mailstock.announcement.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import store.mailstock.announcement.entity.Announcement;
import store.mailstock.auth.entity.Role;
import store.mailstock.auth.repo.UserRepository;
import store.mailstock.common.dto.ApiResponse;
import store.mailstock.announcement.repo.AnnouncementRepository;
import store.mailstock.telegram.TelegramNotifier;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class AnnouncementController {

    private final AnnouncementRepository repo;
    private final TelegramNotifier telegram;
    private final UserRepository users;

    @GetMapping("/public/announcements")
    public ApiResponse<List<Announcement>> publicActive() {
        return ApiResponse.ok(repo.findAll().stream().filter(Announcement::isActive).toList());
    }

    @GetMapping("/announcements/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Announcement>> all() { return ApiResponse.ok(repo.findAll()); }

    @PostMapping("/announcements/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Announcement> create(@RequestBody Announcement a) {
        a.setId(null);
        Announcement saved = repo.save(a);
        // Push the announcement straight to linked Telegram chats (in addition to the website section).
        if (saved.isActive()) telegram.broadcast(saved.getTitle(), saved.getBody(), audienceUserIds(saved.getAudience()));
        return ApiResponse.ok(saved);
    }

    /** Resolve which users to Telegram-broadcast to for an announcement audience (null = everyone). */
    private java.util.Set<Long> audienceUserIds(Announcement.Audience audience) {
        Role role = switch (audience == null ? Announcement.Audience.ALL : audience) {
            case SELLERS -> Role.SELLER;
            case BUYERS -> Role.BUYER;
            case ALL -> null;
        };
        if (role == null) return null;
        return users.findAll().stream().filter(u -> u.hasRole(role))
                .map(store.mailstock.auth.entity.User::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    @PutMapping("/announcements/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Announcement> update(@PathVariable Long id, @RequestBody Announcement a) {
        a.setId(id); return ApiResponse.ok(repo.save(a));
    }

    @DeleteMapping("/announcements/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) { repo.deleteById(id); return ApiResponse.ok(null); }
}
