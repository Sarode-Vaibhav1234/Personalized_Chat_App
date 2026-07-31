package com.stealthchat.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class BlobStorageService {

    private static final Logger logger = LoggerFactory.getLogger(BlobStorageService.class);
    private final Path storageDir;

    public BlobStorageService() {
        this.storageDir = Paths.get(System.getProperty("java.io.tmpdir"), "stealthchat_blobs");
        try {
            Files.createDirectories(this.storageDir);
            logger.info("Blob storage directory initialized at {}", this.storageDir.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Could not create blob storage directory", e);
        }
    }

    public String saveBlob(byte[] data) throws IOException {
        String blobId = UUID.randomUUID().toString();
        Path file = storageDir.resolve(blobId);
        Files.write(file, data);
        logger.info("Saved blob: {} ({} bytes)", blobId, data.length);
        return blobId;
    }

    public byte[] getBlob(String blobId) throws IOException {
        Path file = storageDir.resolve(blobId);
        if (!Files.exists(file)) {
            return null;
        }
        
        byte[] data = Files.readAllBytes(file);
        
        // Burn-after-reading: immediately delete the blob after successful download
        try {
            Files.delete(file);
            logger.info("Burn-after-reading: deleted blob {}", blobId);
        } catch (IOException e) {
            logger.error("Failed to delete blob after reading: {}", blobId, e);
        }
        
        return data;
    }

    // Cron job to clean up blobs older than 24h (in case they were never downloaded)
    @Scheduled(fixedRate = 3600000) // Run every hour
    public void cleanupOldBlobs() {
        logger.info("Running scheduled cleanup of old blobs...");
        try {
            Files.list(storageDir).forEach(path -> {
                try {
                    BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
                    Instant fileTime = attr.creationTime().toInstant();
                    if (fileTime.isBefore(Instant.now().minus(24, ChronoUnit.HOURS))) {
                        Files.delete(path);
                        logger.info("Deleted expired blob: {}", path.getFileName());
                    }
                } catch (IOException e) {
                    logger.error("Error checking/deleting file {}", path, e);
                }
            });
        } catch (IOException e) {
            logger.error("Error listing blob directory for cleanup", e);
        }
    }
}
