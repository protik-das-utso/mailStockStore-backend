package store.mailstock.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.util.SecurityUtils;

/**
 * Website-facing endpoints for connecting a MailStock account to the Telegram bot.
 * All require authentication (they live outside /api/auth, so anyRequest().authenticated() applies).
 */
@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
public class TelegramController {

    private final TelegramLinkService linkService;
    private final TelegramProperties props;

    public record LinkCodeResponse(String code, String botUsername, String deepLink) {}
    public record StatusResponse(boolean linked, boolean botEnabled, String botUsername) {}

    /** Generate a one-time code to paste into the bot (or open the deep link). */
    @PostMapping("/link-code")
    public ApiResponse<LinkCodeResponse> linkCode() {
        String code = linkService.generateLinkCode(SecurityUtils.currentUserId());
        String user = props.getBotUsername();
        String deepLink = (user == null || user.isBlank()) ? null : "https://t.me/" + user + "?start=" + code;
        return ApiResponse.ok(new LinkCodeResponse(code, user, deepLink));
    }

    @GetMapping("/status")
    public ApiResponse<StatusResponse> status() {
        boolean linked = linkService.isLinked(SecurityUtils.currentUserId());
        return ApiResponse.ok(new StatusResponse(linked, props.isConfigured(), props.getBotUsername()));
    }

    @PostMapping("/unlink")
    public ApiResponse<Void> unlink() {
        linkService.unlinkUser(SecurityUtils.currentUserId());
        return ApiResponse.ok("Telegram disconnected.", null);
    }
}
