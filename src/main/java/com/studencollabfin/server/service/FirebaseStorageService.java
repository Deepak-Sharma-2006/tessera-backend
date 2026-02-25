package com.studencollabfin.server.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.UUID;

/**
 * FirebaseStorageService - Handles file uploads to Firebase Storage
 * 
 * Features:
 * - Generic file uploads (images, PDFs, documents)
 * - Base64 string uploads (legacy web profile pictures)
 * - Returns absolute public URLs
 * - Preserves MIME types for proper browser rendering (e.g., PDFs)
 */
@Service
public class FirebaseStorageService {

    private Bucket bucket;

    /**
     * Initialize Firebase App and Storage
     * Requires google-services.json or service account credentials in project root
     */
    @PostConstruct
    public void initialize() {
        try {
            System.out.println("🔥 [FIREBASE] Initializing Firebase Storage Service...");

            // Check if Firebase is already initialized
            if (FirebaseApp.getApps().isEmpty()) {
                // Try to load service account from project root
                String projectRoot = System.getProperty("user.dir");
                String serviceAccountPath = projectRoot + "/google-services.json";

                System.out.println("🔥 [FIREBASE] Loading credentials from: " + serviceAccountPath);

                FileInputStream serviceAccount = new FileInputStream("/etc/secrets/google-services.json");

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .setStorageBucket("tessera-76c5f.firebasestorage.app") // Replace with your actual bucket name
                        .build();

                FirebaseApp.initializeApp(options);
                System.out.println("✅ [FIREBASE] Firebase App initialized successfully");
            } else {
                System.out.println("✅ [FIREBASE] Firebase App already initialized");
            }

            // Get storage bucket
            bucket = StorageClient.getInstance().bucket();
            System.out.println("✅ [FIREBASE] Storage bucket connected: " + bucket.getName());

        } catch (IOException e) {
            System.err.println("❌ [FIREBASE] Failed to initialize Firebase: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Firebase initialization failed", e);
        }
    }

    /**
     * Upload a MultipartFile (generic file upload for images, PDFs, documents)
     * 
     * @param file The multipart file from form data
     * @return Absolute public URL of the uploaded file
     */
    public String upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty");
        }

        System.out.println("🔥 [FIREBASE] Starting file upload...");
        System.out.println("    File name: " + file.getOriginalFilename());
        System.out.println("    File size: " + file.getSize() + " bytes");
        System.out.println("    Content type: " + file.getContentType());

        // Generate unique filename with original extension
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String uniqueFilename = "uploads/" + UUID.randomUUID().toString() + "." + extension;

        System.out.println("    Generated path: " + uniqueFilename);

        // Upload to Firebase Storage with correct MIME type
        // CRITICAL: Pass contentType as second argument so PDFs render correctly in
        // browsers
        Blob blob = bucket.create(
                uniqueFilename,
                file.getInputStream(),
                file.getContentType() // CRITICAL: Preserves MIME type for browser rendering
        );

        // Make the file publicly accessible
        blob.createAcl(com.google.cloud.storage.Acl.of(
                com.google.cloud.storage.Acl.User.ofAllUsers(),
                com.google.cloud.storage.Acl.Role.READER));

        // Generate public URL
        String publicUrl = String.format(
                "https://storage.googleapis.com/%s/%s",
                bucket.getName(),
                uniqueFilename);

        System.out.println("✅ [FIREBASE] File uploaded successfully");
        System.out.println("    Public URL: " + publicUrl);

        return publicUrl;
    }

    /**
     * Upload a Base64-encoded string (legacy web profile pictures)
     * 
     * Expected format: "data:image/png;base64,iVBORw0KGgo..."
     * 
     * @param base64String The base64-encoded image string with data URI prefix
     * @return Absolute public URL of the uploaded file
     */
    public String uploadBase64(String base64String) throws IOException {
        if (base64String == null || base64String.isEmpty()) {
            throw new IllegalArgumentException("Base64 string cannot be null or empty");
        }

        System.out.println("🔥 [FIREBASE] Starting Base64 upload...");

        // Parse the Base64 string to extract MIME type and actual data
        // Format: data:image/png;base64,iVBORw0KGgo...
        String mimeType = "image/png"; // Default
        String base64Data = base64String;

        if (base64String.startsWith("data:")) {
            // Extract MIME type from prefix
            int commaIndex = base64String.indexOf(',');
            if (commaIndex > 0) {
                String prefix = base64String.substring(0, commaIndex);
                base64Data = base64String.substring(commaIndex + 1);

                // Extract MIME type (e.g., "data:image/png;base64" -> "image/png")
                if (prefix.contains(":") && prefix.contains(";")) {
                    mimeType = prefix.substring(prefix.indexOf(':') + 1, prefix.indexOf(';'));
                }
            }
        }

        System.out.println("    MIME type: " + mimeType);
        System.out.println("    Data length: " + base64Data.length() + " chars");

        // Decode Base64 to bytes
        byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
        System.out.println("    Decoded size: " + decodedBytes.length + " bytes");

        // Generate unique filename
        String extension = getExtensionFromMimeType(mimeType);
        String uniqueFilename = "profile-pics/" + UUID.randomUUID().toString() + "." + extension;

        System.out.println("    Generated path: " + uniqueFilename);

        // Upload to Firebase Storage
        InputStream inputStream = new ByteArrayInputStream(decodedBytes);
        Blob blob = bucket.create(
                uniqueFilename,
                inputStream,
                mimeType // Preserve MIME type
        );

        // Make the file publicly accessible
        blob.createAcl(com.google.cloud.storage.Acl.of(
                com.google.cloud.storage.Acl.User.ofAllUsers(),
                com.google.cloud.storage.Acl.Role.READER));

        // Generate public URL
        String publicUrl = String.format(
                "https://storage.googleapis.com/%s/%s",
                bucket.getName(),
                uniqueFilename);

        System.out.println("✅ [FIREBASE] Base64 uploaded successfully");
        System.out.println("    Public URL: " + publicUrl);

        return publicUrl;
    }

    /**
     * Extract file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        }
        return "bin"; // Default for files without extension
    }

    /**
     * Get file extension from MIME type
     */
    private String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null)
            return "bin";

        switch (mimeType) {
            case "image/png":
                return "png";
            case "image/jpeg":
            case "image/jpg":
                return "jpg";
            case "image/gif":
                return "gif";
            case "image/webp":
                return "webp";
            case "application/pdf":
                return "pdf";
            default:
                return "bin";
        }
    }
}
