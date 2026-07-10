package store.mailstock.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.dto.PageResponse;
import store.mailstock.common.util.SecurityUtils;
import store.mailstock.notification.entity.Notification;
import store.mailstock.notification.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService svc;

    @GetMapping
    public ApiResponse<PageResponse<Notification>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(svc.list(SecurityUtils.currentUserId(), PageRequest.of(page, size))));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unread() { return ApiResponse.ok(svc.unread(SecurityUtils.currentUserId())); }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id) {
        svc.markRead(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> readAll() {
        svc.markAllRead(SecurityUtils.currentUserId());
        return ApiResponse.ok(null);
    }
}
