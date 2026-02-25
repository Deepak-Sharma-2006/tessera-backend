package com.studencollabfin.server.controller;

import com.studencollabfin.server.service.AchievementService;
import com.studencollabfin.server.service.FirebaseStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class FileUploadController {

    @Autowired
    private FirebaseStorageService firebaseStorageService;
    @Autowired
    private AchievementService achievementService;

    /**
     * POST /api/uploads/pod-files
     * Accepts a multipart file (image or document) and uploads it to Firebase
     * Storage.
     * Returns the absolute public URL and attachment type.
     * 
     * ✅ HYBRID MIGRATION: Uses Firebase Storage for all new uploads
     * ✅ PROXY UPLOAD: Handles both images and documents (PDFs, etc.)
     * ✅ MIME TYPE PRESERVATION: PDFs render correctly in browsers
     */
    @PostMapping("/pod-files")
    public ResponseEntity<Map<String, Object>> uploadPodFile(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        Map<String, Object> response = new HashMap<>();

        System.out.println("========== FIREBASE FILE UPLOAD START ==========");
        System.out.println("File object received: " + (file != null ? "YES" : "NO"));
        if (file != null) {
            System.out.println("File name: " + file.getOriginalFilename());
            System.out.println("File size: " + file.getSize() + " bytes");
            System.out.println("Content type: " + file.getContentType());
        }

        try {
            // Validate file
            if (file == null || file.isEmpty()) {
                System.err.println("❌ File is empty or null");
                response.put("error", "File is empty or null");
                response.put("receivedFile", file != null);
                return ResponseEntity.badRequest().body(response);
            }

            // ✅ FIREBASE MIGRATION: Upload to Firebase Storage
            String publicUrl = firebaseStorageService.upload(file);
            System.out.println("✅ File uploaded to Firebase Storage");
            System.out.println("✅ Public URL: " + publicUrl);

            // Determine attachment type based on MIME type
            String attachmentType = determineAttachmentType(file.getContentType());
            System.out.println("Attachment type: " + attachmentType);

            // Hard-mode progress: upload events (Resource Titan path)
            if (userId != null && !userId.isEmpty()) {
                achievementService.checkHardMode(userId, "resource-upload", null);
            }

            // Build response
            response.put("url", publicUrl);
            response.put("type", attachmentType);
            response.put("fileName", file.getOriginalFilename());

            System.out.println("========== FIREBASE FILE UPLOAD SUCCESS ==========\n");

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            System.err.println("❌ IOException: " + e.getMessage());
            e.printStackTrace();
            response.put("error", "File upload failed: " + e.getMessage());
            response.put("errorType", "IOException");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } catch (Exception e) {
            System.err.println("❌ Unexpected exception: " + e.getMessage());
            e.printStackTrace();
            response.put("error", "Unexpected error: " + e.getMessage());
            response.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Determines the attachment type based on MIME type
     */
    private String determineAttachmentType(String contentType) {
        if (contentType != null && contentType.startsWith("image/")) {
            return "IMAGE";
        }
        return "FILE";
    }
}
