package store.mailstock.audit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import store.mailstock.audit.entity.AuditLog;
import store.mailstock.audit.repo.AuditLogRepository;
import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.dto.PageResponse;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository repo;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<AuditLog>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long actorId) {
        var pg = PageRequest.of(page, size);
        if (action != null) return ApiResponse.ok(PageResponse.of(repo.findByActionOrderByIdDesc(action, pg)));
        if (actorId != null) return ApiResponse.ok(PageResponse.of(repo.findByActorIdOrderByIdDesc(actorId, pg)));
        return ApiResponse.ok(PageResponse.of(repo.findAll(pg)));
    }
}
