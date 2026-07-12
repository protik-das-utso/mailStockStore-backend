package store.mailstock.recovery.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.recovery.dto.RecoveryDtos.LinkView;
import store.mailstock.recovery.dto.RecoveryDtos.SellerMailboxView;
import store.mailstock.recovery.service.RecoveryAdminService;

/**
 * Seller self-service: mint the public recovery-code link for the account they're submitting. Any
 * logged-in user may call it (SecurityConfig requires authentication for non-public routes); the
 * returned link itself is public. The mailbox is chosen server-side — sellers never see mailbox
 * credentials or ids.
 */
@RestController
@RequestMapping("/api/recovery")
@RequiredArgsConstructor
public class RecoverySellerController {

    private final RecoveryAdminService service;

    /** The recovery emails a seller may add to their accounts (active mailboxes; email + label only). */
    @GetMapping("/mailboxes")
    public ApiResponse<List<SellerMailboxView>> mailboxes() {
        return ApiResponse.ok(service.listActiveForSellers());
    }

    // accountEmail is OPTIONAL: while a seller is *creating* an account, it doesn't exist yet — the
    // recovery code is the step that finalizes it. When blank, the link returns the mailbox's latest
    // Google verification code regardless of account.
    public record SelfLinkRequest(
            @Email @Size(max = 255) String accountEmail,
            @NotBlank @Email @Size(max = 255) String recoveryEmail) {}

    @PostMapping("/my-link")
    public ApiResponse<LinkView> myLink(@Valid @RequestBody SelfLinkRequest r) {
        return ApiResponse.ok(service.selfServiceLink(r.accountEmail(), r.recoveryEmail().trim()));
    }
}
