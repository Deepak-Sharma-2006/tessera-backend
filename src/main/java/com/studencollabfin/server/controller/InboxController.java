package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.Inbox;
import com.studencollabfin.server.repository.InboxRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ✅ INBOX FEATURE: REST API for inbox notifications
 * 
 * Provides endpoints for:
 * - Retrieving all inbox items for the current user
 * - Filtering by notification type
 * - Marking items as read
 */
@RestController
@RequestMapping("/api/inbox")
public class InboxController {

    @Autowired
    private InboxRepository inboxRepository;

    /**
     * Get all inbox items for the current user, sorted by newest first
     * 
     * @param userId The ID of the current user
     * @return List of Inbox items sorted by timestamp (descending)
     */
    @GetMapping("/my")
    public ResponseEntity<List<Inbox>> getMyInbox(
            @RequestParam String userId) {
        System.out.println("📬 InboxController.getMyInbox called for user: " + userId);

        try {
            // Fetch all inbox items for user, sorted by newest first
            Sort sort = Sort.by(Sort.Direction.DESC, "timestamp");
            List<Inbox> inboxItems = inboxRepository.findByUserId(userId, sort);

            System.out.println("✅ Found " + inboxItems.size() + " inbox items for user " + userId);
            return ResponseEntity.ok(inboxItems);

        } catch (Exception e) {
            System.err.println("❌ Error fetching inbox: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Get unread inbox items for the current user
     * 
     * @param userId The ID of the current user
     * @return List of unread Inbox items
     */
    @GetMapping("/my/unread")
    public ResponseEntity<List<Inbox>> getUnreadInbox(
            @RequestParam String userId) {
        System.out.println("📬 InboxController.getUnreadInbox called for user: " + userId);

        try {
            List<Inbox> unreadItems = inboxRepository.findByUserIdAndReadFalse(userId);

            System.out.println("✅ Found " + unreadItems.size() + " unread inbox items for user " + userId);
            return ResponseEntity.ok(unreadItems);

        } catch (Exception e) {
            System.err.println("❌ Error fetching unread inbox: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Mark an inbox item as read
     * 
     * @param id The ID of the inbox item
     * @return The updated Inbox item
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Inbox> markAsRead(
            @PathVariable String id) {
        System.out.println("📬 InboxController.markAsRead called for inbox item: " + id);

        try {
            Inbox inbox = inboxRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Inbox item not found: " + id));

            inbox.setRead(true);
            Inbox updated = inboxRepository.save(inbox);

            System.out.println("✅ Marked inbox item " + id + " as read");
            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            System.err.println("❌ Error marking inbox as read: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Delete an inbox item
     * 
     * @param id The ID of the inbox item
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteInboxItem(
            @PathVariable String id) {
        System.out.println("📬 InboxController.deleteInboxItem called for inbox item: " + id);

        try {
            inboxRepository.deleteById(id);

            System.out.println("✅ Deleted inbox item " + id);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            System.err.println("❌ Error deleting inbox item: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error deleting inbox item");
        }
    }

    /**
     * ✅ Delete multiple inbox items in bulk
     * 
     * @param body Request body containing list of item IDs: { "ids": ["id1", "id2"]
     *             }
     * @return Count of deleted items
     */
    @DeleteMapping("/bulk")
    public ResponseEntity<?> deleteBulkInboxItems(
            @RequestBody Map<String, List<String>> body) {
        System.out.println("📬 InboxController.deleteBulkInboxItems called");

        try {
            List<String> ids = body.get("ids");

            if (ids == null || ids.isEmpty()) {
                return ResponseEntity.badRequest().body("No IDs provided");
            }

            // Delete all items with the provided IDs
            for (String id : ids) {
                inboxRepository.deleteById(id);
            }

            System.out.println("✅ Deleted " + ids.size() + " inbox items");
            return ResponseEntity.ok(Map.of("deleted", ids.size()));

        } catch (Exception e) {
            System.err.println("❌ Error deleting bulk inbox items: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error deleting inbox items");
        }
    }

    /**
     * ✅ Clear all inbox items of a specific type for the current user
     * 
     * @param userId The ID of the current user
     * @param type   The notification type to clear (e.g., APPLICATION_REJECTION,
     *               POD_BAN)
     * @return Count of deleted items
     */
    @DeleteMapping("/clear-type")
    public ResponseEntity<?> clearInboxByType(
            @RequestParam String userId,
            @RequestParam String type) {
        System.out.println("📬 InboxController.clearInboxByType called for user: " + userId + ", type: " + type);

        try {
            // Find all items of this type for the user
            List<Inbox> items = inboxRepository.findByUserIdAndType(userId, type);

            if (items.isEmpty()) {
                System.out.println("  ℹ️ No items of type " + type + " found for user " + userId);
                return ResponseEntity.ok(Map.of("deleted", 0));
            }

            // Delete all found items
            inboxRepository.deleteAll(items);

            System.out.println("✅ Deleted " + items.size() + " inbox items of type " + type);
            return ResponseEntity.ok(Map.of("deleted", items.size(), "type", type));

        } catch (Exception e) {
            System.err.println("❌ Error clearing inbox by type: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error clearing inbox items");
        }
    }

    /**
     * ✅ Clear ALL inbox items for the current user
     * 
     * @param userId The ID of the current user
     * @return Count of deleted items
     */
    @DeleteMapping("/clear-all")
    public ResponseEntity<?> clearAllInbox(
            @RequestParam String userId) {
        System.out.println("📬 InboxController.clearAllInbox called for user: " + userId);

        try {
            // Find all items for this user
            List<Inbox> items = inboxRepository.findByUserId(userId);

            if (items.isEmpty()) {
                System.out.println("  ℹ️ No items found for user " + userId);
                return ResponseEntity.ok(Map.of("deleted", 0));
            }

            // Delete all found items
            inboxRepository.deleteAll(items);

            System.out.println("✅ Cleared all " + items.size() + " inbox items for user " + userId);
            return ResponseEntity.ok(Map.of("deleted", items.size()));

        } catch (Exception e) {
            System.err.println("❌ Error clearing all inbox items: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error clearing inbox items");
        }
    }
}
