package store.mailstock.setting.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.exception.ApiException;
import store.mailstock.setting.entity.Setting;
import store.mailstock.setting.repo.SettingRepository;

/**
 * Admin-managed branding images (logo, hero) that the public site renders — editable without a
 * redeploy, the same idea as the deposit QR. Files are stored under {@code app.uploads-dir} as
 * {@code site-<name>}; the content type is remembered in the {@code media.<name>.type} setting.
 * Only whitelisted names are accepted. When nothing is uploaded the endpoint 404s and the frontend
 * falls back to its bundled default asset.
 */
@RestController
@RequiredArgsConstructor
public class SiteMediaController {

    private static final Set<String> ALLOWED = Set.of("logo", "hero");

    private final SettingRepository settings;

    @Value("${app.uploads-dir:./data/uploads}")
    private String uploadsDir;

    private static String typeKey(String name) { return "media." + name + ".type"; }

    /** Public: serve a branding image if one has been uploaded, else 404 (frontend uses its default). */
    @GetMapping("/api/public/media/{name}")
    public ResponseEntity<byte[]> media(@PathVariable String name) throws Exception {
        if (!ALLOWED.contains(name)) return ResponseEntity.notFound().build();
        Path f = Path.of(uploadsDir, "site-" + name);
        if (!Files.exists(f)) return ResponseEntity.notFound().build();
        String type = settings.findById(typeKey(name)).map(Setting::getValue).orElse(MediaType.IMAGE_PNG_VALUE);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(type))
                .body(Files.readAllBytes(f));
    }

    /** Admin: upload/replace a branding image (logo or hero). */
    @PostMapping("/api/settings/admin/media/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> upload(@PathVariable String name, @RequestParam("file") MultipartFile file) throws Exception {
        if (!ALLOWED.contains(name)) throw ApiException.badRequest("Unknown media name");
        if (file == null || file.isEmpty()) throw ApiException.badRequest("No file uploaded");
        String type = file.getContentType();
        if (type == null || !type.startsWith("image/")) throw ApiException.badRequest("Please upload an image file");
        Path dir = Path.of(uploadsDir);
        Files.createDirectories(dir);
        Files.write(dir.resolve("site-" + name), file.getBytes());
        Setting s = settings.findById(typeKey(name)).orElseGet(() -> Setting.builder().key(typeKey(name)).build());
        s.setValue(type);
        settings.save(s);
        return ApiResponse.ok(name + " image updated");
    }
}
