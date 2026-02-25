package com.studencollabfin.server.service;

import com.studencollabfin.server.model.TeamFindingPost;
import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.repository.PostRepository;
import com.studencollabfin.server.repository.MessageRepository;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.firebase.cloud.StorageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CleanupService {

    private final PostRepository postRepository;
    private final MessageRepository messageRepository;

    /**
     * ✅ Scheduled task that runs every hour to delete TeamFindingPost older than 24
     * hours.
     * This ensures that posts automatically expire after their 24-hour lifecycle.
     */
    @Scheduled(fixedDelay = 3600000) // Run every 1 hour (3600000 ms)
    public void deleteExpiredTeamFindingPosts() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);

            // Get all posts and filter for TeamFindingPost instances
            List<TeamFindingPost> expiredPosts = postRepository.findAll().stream()
                    .filter(p -> p instanceof TeamFindingPost)
                    .map(p -> (TeamFindingPost) p)
                    .filter(p -> {
                        LocalDateTime createdAt = p.getCreatedAt();
                        return createdAt != null && createdAt.isBefore(cutoffTime);
                    })
                    .toList();

            // Delete expired posts
            for (TeamFindingPost post : expiredPosts) {
                try {
                    String postId = post.getId();
                    if (postId != null) {
                        postRepository.deleteById(postId);
                        System.out.println("✅ Deleted expired TeamFindingPost: " + postId + " (created: "
                                + post.getCreatedAt() + ")");
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error deleting post " + post.getId() + ": " + e.getMessage());
                }
            }

            if (expiredPosts.size() > 0) {
                System.out.println("🧹 Cleanup completed: Deleted " + expiredPosts.size() + " expired posts.");
            }
        } catch (Exception e) {
            System.err.println("❌ Error in cleanup service: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✅ Scheduled task that runs every 24 hours to delete messages older than 3
     * days
     * and their associated Firebase Storage files.
     * 
     * This keeps Firebase Storage costs at zero by automatically cleaning up old
     * attachments.
     * 
     * Logic:
     * 1. Find all messages older than 72 hours (3 days)
     * 2. For each message with attachmentUrl or attachmentUrls:
     * - Extract the blob name from the Firebase Storage URL
     * - Delete the file from Firebase Storage
     * - Handle missing files gracefully (already deleted)
     * 3. Delete the message document from MongoDB
     * 4. Log statistics
     */
    @Scheduled(fixedDelay = 86400000) // Run every 24 hours (86400000 ms)
    public void deleteOldMessagesAndAttachments() {
        try {
            System.out.println("🧹 [CLEANUP] Starting scheduled cleanup of old messages and attachments...");

            // Calculate cutoff time: 3 days ago (72 hours)
            Date cutoffTime = Date.from(
                    LocalDateTime.now()
                            .minusDays(3)
                            .atZone(ZoneId.systemDefault())
                            .toInstant());

            System.out
                    .println("🕒 [CLEANUP] Cutoff time: " + cutoffTime + " (messages older than this will be deleted)");

            // Find all messages in MongoDB
            List<Message> allMessages = messageRepository.findAll();

            // Filter messages older than 3 days
            List<Message> oldMessages = allMessages.stream()
                    .filter(m -> m.getSentAt() != null && m.getSentAt().before(cutoffTime))
                    .toList();

            System.out.println("📊 [CLEANUP] Found " + oldMessages.size() + " messages older than 3 days");

            int deletedMessages = 0;
            int deletedFiles = 0;
            int skippedFiles = 0;

            // Process each old message
            for (Message message : oldMessages) {
                try {
                    boolean fileDeleted = false;

                    // Handle single attachment (attachmentUrl)
                    if (message.getAttachmentUrl() != null && !message.getAttachmentUrl().isEmpty()) {
                        boolean result = deleteFirebaseFile(message.getAttachmentUrl());
                        if (result) {
                            deletedFiles++;
                            fileDeleted = true;
                        } else {
                            skippedFiles++;
                        }
                    }

                    // Handle multiple attachments (attachmentUrls)
                    if (message.getAttachmentUrls() != null && !message.getAttachmentUrls().isEmpty()) {
                        for (String url : message.getAttachmentUrls()) {
                            if (url != null && !url.isEmpty()) {
                                boolean result = deleteFirebaseFile(url);
                                if (result) {
                                    deletedFiles++;
                                    fileDeleted = true;
                                } else {
                                    skippedFiles++;
                                }
                            }
                        }
                    }

                    // Delete the message document from MongoDB
                    messageRepository.deleteById(message.getId());
                    deletedMessages++;

                    // Log details for messages with attachments
                    if (fileDeleted) {
                        System.out.println("✅ [CLEANUP] Deleted message " + message.getId() +
                                " with attachment(s) (sent: " + message.getSentAt() + ")");
                    }

                } catch (Exception e) {
                    System.err
                            .println("❌ [CLEANUP] Error processing message " + message.getId() + ": " + e.getMessage());
                }
            }

            // Log final statistics
            System.out.println("🧹 [CLEANUP] Completed successfully:");
            System.out.println("   📨 Deleted messages: " + deletedMessages);
            System.out.println("   🗑️  Deleted files from Firebase Storage: " + deletedFiles);
            System.out.println("   ⏭️  Skipped files (already missing or error): " + skippedFiles);

        } catch (Exception e) {
            System.err.println("❌ [CLEANUP] Error in message cleanup service: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✅ Helper method to delete a file from Firebase Storage given its public URL.
     * 
     * Handles URL decoding and extracts the blob name from the Firebase Storage
     * URL.
     * Format: https://storage.googleapis.com/{bucket-name}/{blob-path}
     * 
     * @param fileUrl The public Firebase Storage URL
     * @return true if file was deleted, false if file was already missing or error
     *         occurred
     */
    private boolean deleteFirebaseFile(String fileUrl) {
        try {
            // Extract blob name from URL
            // Expected format:
            // https://storage.googleapis.com/tessera-76c5f.firebasestorage.app/uploads/uuid.ext
            String blobName = extractBlobNameFromUrl(fileUrl);

            if (blobName == null || blobName.isEmpty()) {
                System.err.println("⚠️ [CLEANUP] Could not extract blob name from URL: " + fileUrl);
                return false;
            }

            // Get Firebase Storage bucket
            Bucket bucket = StorageClient.getInstance().bucket();

            // Get the blob (file) reference
            Blob blob = bucket.get(blobName);

            if (blob == null) {
                // File doesn't exist (already deleted or never uploaded)
                System.out.println("⏭️ [CLEANUP] File already missing, skipping: " + blobName);
                return false;
            }

            // Delete the file
            boolean deleted = blob.delete();

            if (deleted) {
                System.out.println("🗑️ [CLEANUP] Successfully deleted Firebase Storage file: " + blobName);
                return true;
            } else {
                System.err.println("⚠️ [CLEANUP] Failed to delete file (may be already deleted): " + blobName);
                return false;
            }

        } catch (Exception e) {
            // Gracefully handle errors (file already deleted, permission issues, etc.)
            System.err.println("⚠️ [CLEANUP] Error deleting Firebase file from URL " + fileUrl + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * ✅ Helper method to extract the blob name (file path) from a Firebase Storage
     * URL.
     * 
     * Handles URL decoding to properly extract the file path.
     * 
     * @param fileUrl The public Firebase Storage URL
     * @return The blob name (e.g., "uploads/uuid.ext") or null if extraction fails
     */
    private String extractBlobNameFromUrl(String fileUrl) {
        try {
            // Expected format: https://storage.googleapis.com/{bucket-name}/{blob-path}
            // Example:
            // https://storage.googleapis.com/tessera-76c5f.firebasestorage.app/uploads/abc-123.jpg

            if (fileUrl == null || fileUrl.isEmpty()) {
                return null;
            }

            // Find the bucket name portion
            String storagePrefix = "https://storage.googleapis.com/";

            if (!fileUrl.startsWith(storagePrefix)) {
                System.err.println("⚠️ [CLEANUP] URL does not match expected Firebase Storage format: " + fileUrl);
                return null;
            }

            // Remove the prefix
            String afterPrefix = fileUrl.substring(storagePrefix.length());

            // Find the first '/' which separates bucket name from blob path
            int firstSlash = afterPrefix.indexOf('/');

            if (firstSlash == -1) {
                System.err.println("⚠️ [CLEANUP] Could not find blob path separator in URL: " + fileUrl);
                return null;
            }

            // Extract blob name (everything after bucket name)
            String blobName = afterPrefix.substring(firstSlash + 1);

            // Decode URL encoding (e.g., %20 -> space, %2F -> /)
            try {
                blobName = URLDecoder.decode(blobName, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                // UTF-8 is always supported, but handle gracefully
                System.err.println("⚠️ [CLEANUP] Error decoding URL: " + e.getMessage());
            }

            System.out.println("🔍 [CLEANUP] Extracted blob name: " + blobName + " from URL: " + fileUrl);

            return blobName;

        } catch (Exception e) {
            System.err.println("⚠️ [CLEANUP] Error extracting blob name from URL: " + e.getMessage());
            return null;
        }
    }
}
