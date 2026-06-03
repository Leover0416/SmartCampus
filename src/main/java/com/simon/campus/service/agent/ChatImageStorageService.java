package com.simon.campus.service.agent;

import com.simon.campus.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Service
public class ChatImageStorageService {

    @Value("${chat.image-upload-dir:uploads/chat-images}")
    private String uploadDir;

    public StoredImage save(byte[] bytes, String mimeType, String originalName) throws Exception {
        String ext = extensionFor(mimeType, originalName);
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path dir = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(dir)) {
            throw new BizException("非法图片路径");
        }
        Files.write(target, bytes);
        return new StoredImage(fileName, "/api/v1/chat/images/" + fileName, originalName);
    }

    public ImageResource load(String fileName) throws Exception {
        String safeName = Path.of(fileName).getFileName().toString();
        Path dir = Path.of(uploadDir).toAbsolutePath().normalize();
        Path target = dir.resolve(safeName).normalize();
        if (!target.startsWith(dir) || !Files.exists(target)) {
            throw new BizException(404, "图片不存在");
        }
        return new ImageResource(Files.readAllBytes(target), contentTypeFor(safeName));
    }

    void setUploadDirForTest(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    private String extensionFor(String mimeType, String originalName) {
        String lowerName = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return ".jpg";
        if (lowerName.endsWith(".png")) return ".png";
        if (lowerName.endsWith(".gif")) return ".gif";
        if (lowerName.endsWith(".webp")) return ".webp";
        if ("image/jpeg".equalsIgnoreCase(mimeType)) return ".jpg";
        if ("image/gif".equalsIgnoreCase(mimeType)) return ".gif";
        if ("image/webp".equalsIgnoreCase(mimeType)) return ".webp";
        return ".png";
    }

    private String contentTypeFor(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/png";
    }

    public record StoredImage(String fileName, String url, String originalName) {}
    public record ImageResource(byte[] bytes, String contentType) {}
}
