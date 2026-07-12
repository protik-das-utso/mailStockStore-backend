package store.mailstock.wallet.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.exception.ApiException;
import store.mailstock.media.MediaService;

import java.io.InputStream;

/**
 * The Binance Pay QR shown to buyers on the deposit page. Admins replace it via an upload in the
 * dashboard; the image is stored in the database (see {@link MediaService}) so it survives redeploys —
 * it used to live on the container filesystem, which is wiped on every deploy. Until one is uploaded,
 * the image bundled in the jar (resources/images) is served as the default.
 */
@RestController
@RequiredArgsConstructor
public class DepositMediaController {

    private static final String QR_NAME = "deposit-qr";
    private static final String BUNDLED_FALLBACK = "images/binance qr.jpg";

    private final MediaService media;

    /** Public: the current deposit QR (uploaded one if present, else the bundled default). */
    @GetMapping(value = "/api/public/deposit-qr")
    public ResponseEntity<byte[]> depositQr() throws Exception {
        var uploaded = media.find(QR_NAME);
        if (uploaded.isPresent()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache())
                    .contentType(MediaType.parseMediaType(uploaded.get().getContentType()))
                    .body(uploaded.get().getData());
        }
        ClassPathResource res = new ClassPathResource(BUNDLED_FALLBACK);
        if (!res.exists()) return ResponseEntity.notFound().build();
        try (InputStream in = res.getInputStream()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache())
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(in.readAllBytes());
        }
    }

    /** Admin: replace the deposit QR image. */
    @PostMapping("/api/settings/admin/deposit-qr")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> uploadQr(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("No file uploaded");
        String type = file.getContentType();
        if (type == null || !type.startsWith("image/")) throw ApiException.badRequest("Please upload an image file");
        media.save(QR_NAME, type, file.getBytes());
        return ApiResponse.ok("Deposit QR updated");
    }
}
