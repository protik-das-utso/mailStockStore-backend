package store.mailstock.setting.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.exception.ApiException;
import store.mailstock.media.MediaService;

/**
 * Admin-managed branding images (logo, hero) that the public site renders — editable without a
 * redeploy. Stored in the database (see {@link MediaService}) so they survive redeploys; the old
 * filesystem storage was wiped every deploy. Only whitelisted names are accepted. When nothing is
 * uploaded the endpoint 404s and the frontend falls back to its bundled default asset.
 */
@RestController
@RequiredArgsConstructor
public class SiteMediaController {

    private static final Set<String> ALLOWED = Set.of("logo", "hero");

    private final MediaService media;

    /** Public: serve a branding image if one has been uploaded, else 404 (frontend uses its default). */
    @GetMapping("/api/public/media/{name}")
    public ResponseEntity<byte[]> media(@PathVariable String name) {
        if (!ALLOWED.contains(name)) return ResponseEntity.notFound().build();
        return media.find(name)
                .map(a -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noCache())
                        .contentType(MediaType.parseMediaType(a.getContentType()))
                        .body(a.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Admin: upload/replace a branding image (logo or hero). */
    @PostMapping("/api/settings/admin/media/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> upload(@PathVariable String name, @RequestParam("file") MultipartFile file) throws Exception {
        if (!ALLOWED.contains(name)) throw ApiException.badRequest("Unknown media name");
        if (file == null || file.isEmpty()) throw ApiException.badRequest("No file uploaded");
        String type = file.getContentType();
        if (type == null || !type.startsWith("image/")) throw ApiException.badRequest("Please upload an image file");
        media.save(name, type, file.getBytes());
        return ApiResponse.ok(name + " image updated");
    }
}
