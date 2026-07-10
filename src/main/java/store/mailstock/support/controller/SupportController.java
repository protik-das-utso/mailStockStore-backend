package store.mailstock.support.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.dto.PageResponse;
import store.mailstock.common.util.SecurityUtils;
import store.mailstock.support.dto.AdminTicketUpdateRequest;
import store.mailstock.support.dto.TicketCreateRequest;
import store.mailstock.support.dto.TicketMessageResponse;
import store.mailstock.support.dto.TicketReplyRequest;
import store.mailstock.support.dto.TicketResponse;
import store.mailstock.support.entity.SupportTicket;
import store.mailstock.support.service.SupportService;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService svc;

    @PostMapping
    public ApiResponse<TicketResponse> create(@Valid @RequestBody TicketCreateRequest req) {
        return ApiResponse.ok(svc.create(SecurityUtils.currentUserId(), req));
    }

    @GetMapping("/me")
    public ApiResponse<PageResponse<TicketResponse>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(svc.mine(SecurityUtils.currentUserId(), PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    public ApiResponse<TicketResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(svc.get(SecurityUtils.currentUser(), id));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<TicketMessageResponse>> messages(@PathVariable Long id) {
        return ApiResponse.ok(svc.messages(SecurityUtils.currentUser(), id));
    }

    @PostMapping("/{id}/reply")
    public ApiResponse<TicketMessageResponse> reply(@PathVariable Long id, @Valid @RequestBody TicketReplyRequest req) {
        return ApiResponse.ok(svc.reply(SecurityUtils.currentUser(), id, req));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<TicketResponse> close(@PathVariable Long id) {
        return ApiResponse.ok(svc.close(SecurityUtils.currentUser(), id));
    }

    @PostMapping("/{id}/reopen")
    public ApiResponse<TicketResponse> reopen(@PathVariable Long id) {
        return ApiResponse.ok(svc.reopen(SecurityUtils.currentUser(), id));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<TicketResponse>> adminList(
            @RequestParam(required = false) SupportTicket.Status status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(svc.adminList(status, q, PageRequest.of(page, size))));
    }

    /** Admin: set status and/or priority (resolve / close / reopen / reprioritize). */
    @PatchMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TicketResponse> adminUpdate(@PathVariable Long id, @RequestBody AdminTicketUpdateRequest req) {
        return ApiResponse.ok(svc.adminUpdate(SecurityUtils.currentUser(), id, req));
    }
}
