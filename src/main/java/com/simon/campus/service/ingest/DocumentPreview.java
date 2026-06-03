package com.simon.campus.service.ingest;

import org.springframework.http.MediaType;
import org.springframework.web.util.UriUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public record DocumentPreview(
    InputStream stream,
    MediaType mediaType,
    String contentDisposition
) {

    public static DocumentPreview of(String objectKey, String fileName, String contentType, InputStream stream) {
        return new DocumentPreview(
            stream,
            resolveMediaType(fileName, contentType),
            "inline; filename*=UTF-8''" + UriUtils.encode(fileName != null ? fileName : objectKey, StandardCharsets.UTF_8)
        );
    }

    public static DocumentPreview download(String objectKey, String fileName, String contentType, InputStream stream) {
        return new DocumentPreview(
            stream,
            resolveMediaType(fileName, contentType),
            "attachment; filename*=UTF-8''" + UriUtils.encode(fileName != null ? fileName : objectKey, StandardCharsets.UTF_8)
        );
    }

    private static MediaType resolveMediaType(String fileName, String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
                // Fall through to extension-based detection.
            }
        }
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".txt")) return new MediaType("text", "plain", StandardCharsets.UTF_8);
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
