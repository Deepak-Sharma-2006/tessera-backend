package com.studencollabfin.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class FileUploadController {

    /**
     * POST /api/uploads/pod-files
     * Accepts a multipart file (image or document) and saves it to the upload
     * directory.
     * Returns the file URL and attachment type.
     */
    @PostMapping("/pod-files")
    public ResponseEntity<Map<String, Object>> uploadPodFile(
            @RequestParam(value = "file", required = false) MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        System.out.println("========== FILE UPLOAD START ==========");
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

            // Get or create uploads directory in project root
            String projectRoot = System.getProperty("user.dir");
            String uploadDirPath = projectRoot + File.separator + "uploads";
            File uploadDir = new File(uploadDirPath);

            System.out.println("Project root: " + projectRoot);
            System.out.println("Upload dir: " + uploadDirPath);

            // Create directory if it doesn't exist
            if (!uploadDir.exists()) {
                System.out.println("📁 Creating upload directory...");
                boolean created = uploadDir.mkdirs();
                if (!created) {
                    System.err.println("❌ Failed to create directory!");
                    response.put("error", "Failed to create upload directory");
                    response.put("path", uploadDirPath);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
                System.out.println("✅ Directory created successfully");
            } else {
                System.out.println("✅ Directory already exists");
            }

            // Verify directory is writable
            if (!uploadDir.canWrite()) {
                System.err.println("❌ Upload directory is not writable!");
                response.put("error", "Upload directory is not writable");
                response.put("path", uploadDirPath);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            System.out.println("✅ Directory is writable");

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                originalFilename = "file";
            }

            String fileExtension = getFileExtension(originalFilename);
            String uniqueFilename = UUID.randomUUID().toString() + "." + fileExtension;
            System.out.println("Generated filename: " + uniqueFilename);

            // Create file path and save
            File uploadedFile = new File(uploadDir, uniqueFilename);
            System.out.println("Full file path: " + uploadedFile.getAbsolutePath());

            // Save file using Files.copy
            Path filePath = uploadedFile.toPath();
            long bytesCopied = Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("✅ File copied: " + bytesCopied + " bytes");

            // Verify file exists and has content
            if (!uploadedFile.exists()) {
                System.err.println("❌ File was not saved!");
                response.put("error", "File was not saved to disk");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            long savedSize = uploadedFile.length();
            System.out.println("✅ File verified - Size on disk: " + savedSize + " bytes");

            if (savedSize == 0) {
                System.err.println("❌ File is empty after save!");
                response.put("error", "File appears to be empty after save");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            // Determine attachment type based on MIME type
            String attachmentType = determineAttachmentType(file.getContentType());
            System.out.println("Attachment type: " + attachmentType);

            // Build response
            String fileUrl = "/uploads/" + uniqueFilename;
            response.put("url", fileUrl);
            response.put("type", attachmentType);
            response.put("fileName", originalFilename);

            System.out.println("✅ Response URL: " + fileUrl);
            System.out.println("========== FILE UPLOAD SUCCESS ==========\n");

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

    /**
     * Extracts file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        }
        return "bin";
    }
}
