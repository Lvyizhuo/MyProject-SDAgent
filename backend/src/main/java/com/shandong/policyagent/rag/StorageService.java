package com.shandong.policyagent.rag;

import com.shandong.policyagent.config.MinioConfig;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
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
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String safeFolderPath = folderPath.startsWith("/") ? folderPath.substring(1) : folderPath;
        safeFolderPath = safeFolderPath.replace("/", "_");
        String storagePath = String.format("%s/%s_%s_%s",
                safeFolderPath, timestamp, uuid, file.getOriginalFilename());

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

    public InputStream getFile(String storagePath) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(storagePath)
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
                            .object(storagePath)
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
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(storagePath)
                            .build()
            );
            log.info("Deleted file from MinIO: {}", storagePath);
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}", storagePath, e);
            throw new RuntimeException("Failed to delete file", e);
        }
    }
}
