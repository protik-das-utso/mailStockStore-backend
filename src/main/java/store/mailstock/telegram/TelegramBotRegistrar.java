package store.mailstock.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.time.Instant;

import store.mailstock.setting.entity.Setting;
import store.mailstock.setting.repo.SettingRepository;

/**
 * Owns the {@link MailStockBot} long-polling session and lets the admin turn it on/off at runtime.
 *
 * The desired on/off state is persisted in the {@code telegram.enabled} setting (falling back to the
 * {@code app.telegram.enabled} env flag on first boot), so a toggle survives restarts. A watchdog
 * re-attempts the connection whenever the bot should be running but isn't — this self-heals the
 * "backend up but bot disconnected" case caused by a transient Telegram getUpdates conflict (e.g.
 * another instance briefly polling the same token) or a network blip.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramBotRegistrar {

    static final String ENABLED_KEY = "telegram.enabled";

    private final TelegramProperties props;
    private final TelegramLinkService links;
    private final BotApiClient api;
    private final SettingRepository settings;
    private final store.mailstock.media.MediaService media;

    private volatile MailStockBot bot;
    private volatile BotSession session;
    /** What the admin wants (persisted). Whether it's actually connected is {@link #isRunning()}. */
    private volatile boolean desiredEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        desiredEnabled = readDesired();
        if (desiredEnabled) start();
        else log.info("[TELEGRAM] bot disabled at startup (desiredEnabled=false)");
    }

    /** Whether the poller is live and receiving updates right now. */
    public boolean isRunning() {
        BotSession s = session;
        return s != null && s.isRunning();
    }

    public boolean isDesiredEnabled() { return desiredEnabled; }
    public boolean isTokenPresent() { return props.getBotToken() != null && !props.getBotToken().isBlank(); }
    public String getBotUsername() { return props.getBotUsername(); }

    /** Admin toggle: persist the desired state and start/stop the bot accordingly. */
    public synchronized boolean setEnabled(boolean enabled) {
        settings.save(Setting.builder().key(ENABLED_KEY).value(String.valueOf(enabled)).updatedAt(Instant.now()).build());
        desiredEnabled = enabled;
        if (enabled) return start();
        stop();
        return false;
    }

    /** (Re)start the poller. Idempotent — returns true if the bot ends up running. */
    public synchronized boolean start() {
        if (isRunning()) return true;
        if (!isTokenPresent()) {
            log.warn("[TELEGRAM] cannot start — no bot token configured (TELEGRAM_BOT_TOKEN).");
            return false;
        }
        stop(); // clear any dead session before re-registering
        try {
            MailStockBot b = new MailStockBot(props.getBotToken(), props.getBotUsername(), links, api, settings, media);
            BotSession s = new TelegramBotsApi(DefaultBotSession.class).registerBot(b);
            this.bot = b;
            this.session = s;
            log.info("[TELEGRAM] bot @{} connected (long polling)", props.getBotUsername());
            return true;
        } catch (Exception e) {
            // Most commonly a 409 Conflict — another process is polling getUpdates for this token.
            log.error("[TELEGRAM] failed to connect bot (will retry): {}", e.getMessage());
            this.bot = null;
            this.session = null;
            return false;
        }
    }

    /** Stop the poller if running. */
    public synchronized void stop() {
        BotSession s = session;
        if (s != null) {
            try { s.stop(); } catch (Exception e) { log.warn("[TELEGRAM] error stopping session: {}", e.toString()); }
        }
        session = null;
        bot = null;
    }

    /**
     * Release the Telegram long-poll the instant this instance shuts down (e.g. a Render redeploy sends
     * SIGTERM). Without this the dying container keeps polling getUpdates until the JVM is killed, so the
     * NEW container's poll collides with it → the "[409] Conflict: terminated by other getUpdates" error.
     * Stopping here hands the token over cleanly so the replacement connects without a fight.
     */
    @PreDestroy
    public void onShutdown() {
        log.info("[TELEGRAM] shutting down — releasing bot poll");
        stop();
    }

    /**
     * Self-heal + cross-instance sync, every minute. Re-reads the persisted on/off state from the DB
     * so a toggle made on ANY backend instance propagates to all of them, then converges this instance:
     *  - should be ON but isn't connected  → (re)connect
     *  - should be OFF but is still running → stop the poller
     * Without the OFF branch, a second instance (Railway replica / leftover deploy) would keep polling
     * after an admin turned the bot off elsewhere, so the bot appears "still running".
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public synchronized void watchdog() {
        desiredEnabled = readDesired(); // pick up a toggle made on another instance
        if (desiredEnabled && !isRunning() && isTokenPresent()) {
            log.info("[TELEGRAM] watchdog: bot should be running but isn't — reconnecting…");
            start();
        } else if (!desiredEnabled && isRunning()) {
            log.info("[TELEGRAM] watchdog: bot disabled in settings — stopping this instance's poller");
            stop();
        }
    }

    private boolean readDesired() {
        return settings.findById(ENABLED_KEY)
                .map(s -> "true".equalsIgnoreCase(s.getValue()))
                .orElse(props.isEnabled());
    }

    /** The live bot instance, or null when disabled/not yet started. */
    public MailStockBot getBot() { return bot; }
}
