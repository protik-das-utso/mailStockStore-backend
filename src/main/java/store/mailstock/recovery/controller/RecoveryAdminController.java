package store.mailstock.recovery.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.recovery.dto.RecoveryDtos.LinkRequest;
import store.mailstock.recovery.dto.RecoveryDtos.LinkView;
import store.mailstock.recovery.dto.RecoveryDtos.MailboxRequest;
import store.mailstock.recovery.dto.RecoveryDtos.MailboxView;
import store.mailstock.recovery.service.RecoveryAdminService;
import store.mailstock.recovery.service.RecoveryCodeService;

/** Admin: manage recovery mailboxes (POP3 config) and the public per-account links they back. */
@RestController
@RequestMapping("/api/admin/recovery")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RecoveryAdminController {

    private final RecoveryAdminService service;

    @GetMapping("/mailboxes")
    public ApiResponse<List<MailboxView>> listMailboxes() {
        return ApiResponse.ok(service.listMailboxes());
    }

    @PostMapping("/mailboxes")
    public ApiResponse<MailboxView> createMailbox(@Valid @RequestBody MailboxRequest r) {
        return ApiResponse.ok("Mailbox created", service.createMailbox(r));
    }

    @PutMapping("/mailboxes/{id}")
    public ApiResponse<MailboxView> updateMailbox(@PathVariable Long id, @Valid @RequestBody MailboxRequest r) {
        return ApiResponse.ok("Mailbox updated", service.updateMailbox(id, r));
    }

    @DeleteMapping("/mailboxes/{id}")
    public ApiResponse<Void> deleteMailbox(@PathVariable Long id) {
        service.deleteMailbox(id);
        return ApiResponse.ok("Mailbox deleted", null);
    }

    /** Verify POP3 login works and report message/code counts. Body may carry a just-typed password. */
    @PostMapping("/mailboxes/{id}/test")
    public ApiResponse<RecoveryCodeService.MailboxDiag> testMailbox(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        var diag = service.testMailbox(id, body == null ? null : body.get("password"));
        return ApiResponse.ok("Connection OK", diag);
    }

    @GetMapping("/links")
    public ApiResponse<List<LinkView>> listLinks() {
        return ApiResponse.ok(service.listLinks());
    }

    @PostMapping("/links")
    public ApiResponse<LinkView> createLink(@Valid @RequestBody LinkRequest r) {
        return ApiResponse.ok("Link created", service.createLink(r));
    }

    @PostMapping("/links/{id}/revoke")
    public ApiResponse<Void> revokeLink(@PathVariable Long id) {
        service.revokeLink(id);
        return ApiResponse.ok("Link revoked", null);
    }
}
