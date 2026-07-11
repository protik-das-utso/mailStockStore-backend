package store.mailstock.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.util.SecurityUtils;

/**
 * Website-facing endpoints for connecting a MailStock account to the Telegram bot,
 * plus admin controls to turn the bot on/off at runtime.
 * All require authentication (they live outside /api/auth, so anyRequest().authenticated() applies).
 */
@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
public class TelegramController {

    private final TelegramLinkService linkService;
    private final TelegramProperties props;
    private final TelegramBotRegistrar registrar;

    public record LinkCodeResponse(String code, String botUsername, String deepLink) {}
    public record StatusResponse(boolean linked, boolean botEnabled, String botUsername) {}
    /** Admin view of the bot: whether it's wanted on, whether it's actually connected, token presence. */
    public record BotStateResponse(boolean desiredEnabled, boolean running, boolean tokenPresent, String botUsername) {}
    public record ToggleRequest(boolean enabled) {}

    /** Generate a one-time code to paste into the bot (or open the deep link). */
    @PostMapping("/link-code")
    public ApiResponse<LinkCodeResponse> linkCode() {
        String code = linkService.generateLinkCode(SecurityUtils.currentUserId());
        String user = props.getBotUsername();
        String deepLink = (user == null || user.isBlank()) ? null : "https://t.me/" + user + "?start=" + code;
        return ApiResponse.ok(new LinkCodeResponse(code, user, deepLink));
    }

    /** botEnabled reflects the REAL poller state so the website never claims the bot is up when it isn't. */
    @GetMapping("/status")
    public ApiResponse<StatusResponse> status() {
        boolean linked = linkService.isLinked(SecurityUtils.currentUserId());
        return ApiResponse.ok(new StatusResponse(linked, registrar.isRunning(), props.getBotUsername()));
    }

    @PostMapping("/unlink")
    public ApiResponse<Void> unlink() {
        linkService.unlinkUser(SecurityUtils.currentUserId());
        return ApiResponse.ok("Telegram disconnected.", null);
    }

    // ---- Admin controls (turn the bot on/off at runtime) ----

    @GetMapping("/admin/state")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BotStateResponse> adminState() {
        return ApiResponse.ok(new BotStateResponse(
                registrar.isDesiredEnabled(), registrar.isRunning(), registrar.isTokenPresent(), registrar.getBotUsername()));
    }

    @PostMapping("/admin/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BotStateResponse> adminToggle(@RequestBody ToggleRequest req) {
        boolean running = registrar.setEnabled(req.enabled());
        String msg = req.enabled()
                ? (running ? "Bot enabled and connected."
                           : "Bot enabled, but it couldn't connect yet (check the token, or another instance may be polling). It will keep retrying.")
                : "Bot disabled.";
        return ApiResponse.ok(msg, new BotStateResponse(
                registrar.isDesiredEnabled(), registrar.isRunning(), registrar.isTokenPresent(), registrar.getBotUsername()));
    }
}
