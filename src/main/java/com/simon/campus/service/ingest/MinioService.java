package com.simon.campus.service.ingest;

import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.local-data-dir:}")
    private String localDataDir;

    public String upload(MultipartFile file, String docId) throws Exception {
        String ext = "";
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String objectKey = "docs/" + docId + ext;

        minioClient.putObject(PutObjectArgs.builder()
            .bucket(bucketName)
            .object(objectKey)
            .stream(file.getInputStream(), file.getSize(), -1)
            .contentType(file.getContentType())
            .build());

        return objectKey;
    }

    public InputStream download(String objectKey) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
            .bucket(bucketName)
            .object(objectKey)
            .build());
    }

    public void delete(String objectKey) {
        try {
            deleteStrict(objectKey);
        } catch (Exception e) {
            log.warn("Failed to delete MinIO object {}: {}", objectKey, e.getMessage());
        }
    }

    public void deleteStrict(String objectKey) throws Exception {
        if (objectKey == null || objectKey.isBlank()) return;
        minioClient.removeObject(RemoveObjectArgs.builder()
            .bucket(bucketName)
            .object(objectKey)
            .build());
        log.info("[DELETE_FLOW] step=minio_delete bucket={} object={}", bucketName, objectKey);
        deleteLocalObjectIfConfigured(objectKey);
    }

    private void deleteLocalObjectIfConfigured(String objectKey) throws Exception {
        if (localDataDir == null || localDataDir.isBlank()) return;

        Path base = Path.of(localDataDir).toAbsolutePath().normalize();
        Path target = base.resolve(bucketName).resolve(objectKey).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("非法本地文件路径: " + objectKey);
        }

        boolean deleted = Files.deleteIfExists(target);
        log.info("[DELETE_FLOW] step=local_file_delete path={} deleted={}", target, deleted);
    }
}
