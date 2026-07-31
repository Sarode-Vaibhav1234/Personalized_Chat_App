package com.stealthchat.backend.controller;

import com.stealthchat.backend.service.BlobStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/blob")
public class BlobController {

    private final BlobStorageService blobStorageService;

    public BlobController(BlobStorageService blobStorageService) {
        this.blobStorageService = blobStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadBlob(@RequestParam("file") MultipartFile file) {
        try {
            byte[] data = file.getBytes();
            if (data.length > 5 * 1024 * 1024) { // 5MB limit on encrypted blob size
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(Collections.singletonMap("error", "File too large"));
            }

            String blobId = blobStorageService.saveBlob(data);
            return ResponseEntity.ok(Collections.singletonMap("blobId", blobId));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to save blob"));
        }
    }

    @GetMapping(value = "/{blobId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> downloadBlob(@PathVariable String blobId) {
        try {
            byte[] data = blobStorageService.getBlob(blobId);
            if (data == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(data);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
