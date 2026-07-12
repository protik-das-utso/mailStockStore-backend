package store.mailstock.media;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import store.mailstock.setting.entity.Setting;
import store.mailstock.setting.repo.SettingRepository;

/**
 * Database-backed store for admin-uploaded images (deposit QR, logo, hero). Replaces the old
 * filesystem storage, which was wiped on every redeploy because the container disk is ephemeral.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

    private final MediaAssetRepository repo;
    private final SettingRepository settings;

    @Value("${app.uploads-dir:./data/uploads}")
    private String uploadsDir;

    @Transactional
    public void save(String name, String contentType, byte[] data) {
        MediaAsset a = repo.findById(name).orElseGet(() -> MediaAsset.builder().name(name).build());
        a.setContentType(contentType);
        a.setData(data);
        a.setUpdatedAt(Instant.now());
        repo.save(a);
    }

    @Transactional(readOnly = true)
    public Optional<MediaAsset> find(String name) {
        return repo.findById(name);
    }

    /**
     * One-time lift of any images still sitting on the local disk (from before this change) into the
     * database, so an existing deposit QR / logo / hero isn't lost on the first redeploy after upgrade.
     * Best-effort: a missing file or unreadable disk is simply skipped. Runs only for assets not yet
     * in the DB, so it never overwrites a newer upload.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void importFromDiskOnce() {
        // disk file name -> (media name, settings key holding its content type)
        Map<String, String[]> legacy = Map.of(
                "deposit-qr", new String[]{"deposit-qr", "deposit.qr_type"},
                "site-logo",  new String[]{"logo", "media.logo.type"},
                "site-hero",  new String[]{"hero", "media.hero.type"});
        for (var e : legacy.entrySet()) {
            String file = e.getKey();
            String mediaName = e.getValue()[0];
            String typeKey = e.getValue()[1];
            try {
                if (repo.existsById(mediaName)) continue;          // DB already has it — leave as is
                Path p = Path.of(uploadsDir, file);
                if (!Files.exists(p)) continue;
                String type = settings.findById(typeKey).map(Setting::getValue).orElse(MediaType.IMAGE_PNG_VALUE);
                save(mediaName, type, Files.readAllBytes(p));
                log.info("[MEDIA] imported '{}' from disk into the database ({} bytes)", mediaName, Files.size(p));
            } catch (Exception ex) {
                log.warn("[MEDIA] could not import '{}' from disk: {}", mediaName, ex.toString());
            }
        }
    }
}
