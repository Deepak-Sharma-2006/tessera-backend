package com.studencollabfin.server.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
public class FileUploadController {

    @Value("${file.upload.dir:uploads/}")
    private String uploadDir;

    /**
     * POST /api/uploads/pod-files
     * Accepts a multipart file (image or document) and saves it to the upload
     * directory.
     * Returns the file URL and attachment type.
     */
    @PostMapping("/pod-files")
    public ResponseEntity<Map<String, Object>> uploadPodFile(@RequestParam("file") MultipartFile file) {
        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }

            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            String uniqueFilename = UUID.randomUUID().toString() + "." + fileExtension;

            // Save file
            Path filePath = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), filePath);

            // Determine attachment type
            String attachmentType = determineAttachmentType(file.getContentType());

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("url", "/uploads/" + uniqueFilename);
            response.put("type", attachmentType);
            response.put("fileName", originalFilename);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "File upload failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Determines the attachment type based on MIME type.
     */
    private String determineAttachmentType(String contentType) {
        if (contentType != null && contentType.startsWith("image/")) {
            return "IMAGE";
        }
        return "FILE";
    }

    /**
     * Extracts file extension from filename.
     */
    private String getFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        }
        return "bin";
    }
}
