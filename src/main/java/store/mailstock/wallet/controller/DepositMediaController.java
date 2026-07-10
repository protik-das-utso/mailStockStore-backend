package store.mailstock.wallet.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.exception.ApiException;
import store.mailstock.setting.entity.Setting;
import store.mailstock.setting.repo.SettingRepository;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The Binance Pay QR shown to buyers on the deposit page. Admins replace it via an upload in the
 * dashboard; the uploaded image is stored under {@code app.uploads-dir}. Until one is uploaded, the
 * image bundled in the jar (resources/images) is served as the default.
 */
@RestController
@RequiredArgsConstructor
public class DepositMediaController {

    private static final String QR_FILE = "deposit-qr";
    private static final String QR_TYPE_KEY = "deposit.qr_type";
    private static final String BUNDLED_FALLBACK = "images/binance qr.jpg";

    private final SettingRepository settings;

    @Value("${app.uploads-dir:./data/uploads}")
    private String uploadsDir;

    /** Public: the current deposit QR (uploaded one if present, else the bundled default). */
    @GetMapping(value = "/api/public/deposit-qr")
    public ResponseEntity<byte[]> depositQr() throws Exception {
        Path uploaded = Path.of(uploadsDir, QR_FILE);
        if (Files.exists(uploaded)) {
            String type = settings.findById(QR_TYPE_KEY).map(Setting::getValue).orElse(MediaType.IMAGE_PNG_VALUE);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache())
                    .contentType(MediaType.parseMediaType(type))
                    .body(Files.readAllBytes(uploaded));
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
        Path dir = Path.of(uploadsDir);
        Files.createDirectories(dir);
        Files.write(dir.resolve(QR_FILE), file.getBytes());
        Setting s = settings.findById(QR_TYPE_KEY).orElseGet(() -> Setting.builder().key(QR_TYPE_KEY).build());
        s.setValue(type);
        settings.save(s);
        return ApiResponse.ok("Deposit QR updated");
    }
}
