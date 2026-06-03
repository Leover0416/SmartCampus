package com.simon.campus.service.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChatImageStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void savesImageAndReturnsPublicUrl() throws Exception {
        ChatImageStorageService service = new ChatImageStorageService();
        service.setUploadDirForTest(tempDir.toString());

        ChatImageStorageService.StoredImage image = service.save(new byte[]{1, 2, 3}, "image/png", "demo.png");

        assertThat(image.url()).startsWith("/api/v1/chat/images/");
        assertThat(image.originalName()).isEqualTo("demo.png");
        assertThat(Files.readAllBytes(tempDir.resolve(image.fileName()))).containsExactly(1, 2, 3);
    }
}
