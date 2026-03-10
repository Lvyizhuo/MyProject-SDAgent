package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.MinioConfig;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    public void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioConfig.getBucketName()).build()
            );
            if (!found) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(minioConfig.getBucketName()).build()
                );
                log.info("Created MinIO bucket: {}", minioConfig.getBucketName());
            }
        } catch (Exception e) {
            log.error("Failed to ensure MinIO bucket exists", e);
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    public String storeFile(MultipartFile file, String folderPath) {
        ensureBucketExists();
        String storagePath = buildStoragePath(folderPath, file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(storagePath)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("Stored file to MinIO: {}", storagePath);
            return storagePath;
        } catch (Exception e) {
            log.error("Failed to store file to MinIO", e);
            throw new RuntimeException("Failed to store file", e);
        }
    }

    public String storeBytes(byte[] bytes, String folderPath, String fileName, String contentType) {
        ensureBucketExists();
        String storagePath = buildStoragePath(folderPath, fileName);
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(storagePath)
                            .stream(inputStream, bytes.length, -1)
                            .contentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
                            .build()
            );
            log.info("Stored bytes to MinIO: {}", storagePath);
            return storagePath;
        } catch (Exception e) {
            log.error("Failed to store bytes to MinIO", e);
            throw new RuntimeException("Failed to store content", e);
        }
    }

    public String storeText(String text, String folderPath, String fileName) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return storeBytes(bytes, folderPath, fileName, "text/plain; charset=UTF-8");
    }

    public InputStream getFile(String storagePath) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(normalizeStoragePath(storagePath))
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to get file from MinIO: {}", storagePath, e);
            throw new RuntimeException("Failed to get file", e);
        }
    }

    public String getPresignedUrl(String storagePath, int expirationMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(normalizeStoragePath(storagePath))
                            .method(Method.GET)
                            .expiry(expirationMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate presigned URL: {}", storagePath, e);
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }

    public void deleteFile(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            log.info("Skip MinIO deletion because storage path is empty");
            return;
        }
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(normalizeStoragePath(storagePath))
                            .build()
            );
            log.info("Deleted file from MinIO: {}", storagePath);
        } catch (ErrorResponseException e) {
            String errorCode = e.errorResponse() == null ? null : e.errorResponse().code();
            if ("NoSuchKey".equals(errorCode) || "NoSuchObject".equals(errorCode)) {
                log.warn("Skip MinIO deletion because object is already missing: {}", storagePath);
                return;
            }
            log.error("Failed to delete file from MinIO: {}", storagePath, e);
            throw new RuntimeException("Failed to delete file", e);
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}", storagePath, e);
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    private String buildStoragePath(String folderPath, String fileName) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String safeFolderPath = folderPath == null || folderPath.isBlank() ? "root" : folderPath;
        safeFolderPath = safeFolderPath.startsWith("/") ? safeFolderPath.substring(1) : safeFolderPath;
        safeFolderPath = safeFolderPath.replace("/", "_");
        if (safeFolderPath.isBlank()) {
            safeFolderPath = "root";
        }
        String safeFileName = fileName == null || fileName.isBlank() ? "document.bin" : fileName;
        return String.format("%s/%s_%s_%s", safeFolderPath, timestamp, uuid, safeFileName);
    }

    private String normalizeStoragePath(String storagePath) {
        if (storagePath == null) {
            return null;
        }
        String normalized = storagePath.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
