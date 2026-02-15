package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.User;
import com.studencollabfin.server.model.Post;
import com.studencollabfin.server.model.Comment;
import com.studencollabfin.server.model.SystemSettings;
import com.studencollabfin.server.repository.*;
import com.studencollabfin.server.service.AchievementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = { "http://localhost:5173", "http://localhost:5174" }, allowCredentials = "true")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final AchievementService achievementService;
    private final AchievementRepository achievementRepository;
    private final InboxRepository inboxRepository;
    private final CollabPodRepository collabPodRepository;
    private final PodCooldownRepository podCooldownRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final EventRepository eventRepository;
    private final ApplicationRepository applicationRepository;
    private final ReportRepository reportRepository;
    private final BuddyBeaconRepository buddyBeaconRepository;
    private final EventReminderRepository eventReminderRepository;
    private final SystemSettingsRepository systemSettingsRepository;

    public AdminController(UserRepository userRepository, PostRepository postRepository,
            CommentRepository commentRepository, AchievementService achievementService,
            AchievementRepository achievementRepository, InboxRepository inboxRepository,
            CollabPodRepository collabPodRepository, PodCooldownRepository podCooldownRepository,
            ConversationRepository conversationRepository, MessageRepository messageRepository,
            EventRepository eventRepository, ApplicationRepository applicationRepository,
            ReportRepository reportRepository, BuddyBeaconRepository buddyBeaconRepository,
            EventReminderRepository eventReminderRepository, SystemSettingsRepository systemSettingsRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.achievementService = achievementService;
        this.achievementRepository = achievementRepository;
        this.inboxRepository = inboxRepository;
        this.collabPodRepository = collabPodRepository;
        this.podCooldownRepository = podCooldownRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.eventRepository = eventRepository;
        this.applicationRepository = applicationRepository;
        this.reportRepository = reportRepository;
        this.buddyBeaconRepository = buddyBeaconRepository;
        this.eventReminderRepository = eventReminderRepository;
        this.systemSettingsRepository = systemSettingsRepository;
    }

    /**
     * Get admin dashboard statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        try {
            // Total users
            long totalUsers = userRepository.count();

            // College count - distinct colleges
            List<User> allUsers = userRepository.findAll();
            long collegeCount = allUsers.stream()
                    .map(u -> u.getCollegeName())
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();

            // Active reports (users with isBanned = true)
            long activeReports = allUsers.stream()
                    .filter(u -> u.isBanned())
                    .count();

            // Total replies/comments
            long totalReplies = commentRepository.count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalUsers", totalUsers);
            stats.put("collegeCount", collegeCount);
            stats.put("activeReports", activeReports);
            stats.put("totalReplies", totalReplies);

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            System.err.println("[AdminController] Error fetching stats: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error fetching stats: " + e.getMessage());
        }
    }

    /**
     * Get all users
     */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        try {
            List<User> users = userRepository.findAll();

            // Convert to DTOs to avoid exposing sensitive data
            List<Map<String, Object>> userDtos = users.stream().map(user -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("_id", user.getId());
                dto.put("email", user.getEmail());
                dto.put("fullName", user.getFullName());
                dto.put("collegeName", user.getCollegeName());
                dto.put("createdAt", user.getCreatedAt());
                dto.put("isBanned", user.isBanned());
                dto.put("role", user.getRole());
                return dto;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(userDtos);
        } catch (Exception e) {
            System.err.println("[AdminController] Error fetching users: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error fetching users: " + e.getMessage());
        }
    }

    /**
     * Ban a user
     */
    @PatchMapping("/users/{id}/ban")
    public ResponseEntity<?> banUser(@PathVariable String id, @RequestBody Map<String, String> request) {
        try {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            // Ban the user
            user.setBanned(true);

            // Add Spam Alert badge if not present
            if (user.getBadges() == null) {
                user.setBadges(new ArrayList<>());
            }
            if (!user.getBadges().contains("Spam Alert")) {
                user.getBadges().add("Spam Alert");
            }

            userRepository.save(user);

            System.out.println("[AdminController] ✅ User banned: " + user.getEmail());

            return ResponseEntity.ok(Map.of(
                    "message", "User banned successfully",
                    "userId", user.getId(),
                    "email", user.getEmail()));
        } catch (Exception e) {
            System.err.println("[AdminController] Error banning user: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error banning user: " + e.getMessage());
        }
    }

    /**
     * ✅ COMPREHENSIVE USER DELETION
     * Deletes user and ALL associated data from database:
     * - User profile
     * - Achievements
     * - Inbox items
     * - Pod cooldowns
     * - Event reminders
     * - Conversations (where user is participant)
     * - Messages (sent/received)
     * - Events (created by user)
     * - Buddy Beacons
     * - Reports (made by user)
     * - Applications (from user)
     * - Posts/Comments anonymized (preserved for record)
     * - Collab Pods (deleted if user is creator, member removed if participant)
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        try {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            String userEmail = user.getEmail();
            int deletedAchievements = 0;
            int deletedInboxItems = 0;
            int deletedPodCooldowns = 0;
            int deletedEventReminders = 0;
            int anonymizedPosts = 0;
            int anonymizedComments = 0;
            int deletedConversations = 0;
            int deletedMessages = 0;
            int deletedBuddyBeacons = 0;
            int deletedReports = 0;
            int deletedApplications = 0;
            int deletedEvents = 0;
            int deletedPods = 0;

            // ============================================================
            // STEP 1: DELETE ACHIEVEMENTS
            // ============================================================
            var achievements = achievementRepository.findByUserId(id);
            achievementRepository.deleteAll(achievements);
            deletedAchievements = achievements.size();
            System.out.println("  ✓ Deleted " + deletedAchievements + " achievements");

            // ============================================================
            // STEP 2: DELETE INBOX ITEMS
            // ============================================================
            var inboxItems = inboxRepository.findByUserId(id);
            inboxRepository.deleteAll(inboxItems);
            deletedInboxItems = inboxItems.size();
            System.out.println("  ✓ Deleted " + deletedInboxItems + " inbox items");

            // ============================================================
            // STEP 3: DELETE POD COOLDOWNS
            // ============================================================
            var cooldowns = podCooldownRepository.findByUserId(id);
            podCooldownRepository.deleteAll(cooldowns);
            deletedPodCooldowns = cooldowns.size();
            System.out.println("  ✓ Deleted " + deletedPodCooldowns + " pod cooldowns");

            // ============================================================
            // STEP 4: DELETE EVENT REMINDERS
            // ============================================================
            var reminders = eventReminderRepository.findAll();
            var userReminders = reminders.stream()
                    .filter(r -> id.equals(r.getUserId()))
                    .toList();
            eventReminderRepository.deleteAll(userReminders);
            deletedEventReminders = userReminders.size();
            System.out.println("  ✓ Deleted " + deletedEventReminders + " event reminders");

            // ============================================================
            // STEP 5: DELETE/ANONYMIZE CONVERSATIONS & MESSAGES
            // ============================================================
            var conversations = conversationRepository.findByParticipantIdsContaining(id);
            for (var conv : conversations) {
                // Delete all messages in conversation
                messageRepository.deleteByConversationId(conv.getId());
                deletedMessages += conv.getParticipantIds().size();
            }
            conversationRepository.deleteAll(conversations);
            deletedConversations = conversations.size();
            System.out.println("  ✓ Deleted " + deletedConversations + " conversations");
            System.out.println("  ✓ Deleted " + deletedMessages + " messages");

            // ============================================================
            // STEP 6: DELETE BUDDY BEACONS
            // ============================================================
            var beacons = buddyBeaconRepository.findAll();
            var userBeacons = beacons.stream()
                    .filter(b -> id.equals(b.getAuthorId()))
                    .toList();
            buddyBeaconRepository.deleteAll(userBeacons);
            deletedBuddyBeacons = userBeacons.size();
            System.out.println("  ✓ Deleted " + deletedBuddyBeacons + " buddy beacons");

            // ============================================================
            // STEP 7: DELETE REPORTS (made by user)
            // ============================================================
            var reports = reportRepository.findAll();
            var userReports = reports.stream()
                    .filter(r -> id.equals(r.getReporterId()))
                    .toList();
            reportRepository.deleteAll(userReports);
            deletedReports = userReports.size();
            System.out.println("  ✓ Deleted " + deletedReports + " reports");

            // ============================================================
            // STEP 8: DELETE APPLICATIONS (from user)
            // ============================================================
            var applications = applicationRepository.findAll();
            var userApplications = applications.stream()
                    .filter(a -> id.equals(a.getApplicantId()))
                    .toList();
            applicationRepository.deleteAll(userApplications);
            deletedApplications = userApplications.size();
            System.out.println("  ✓ Deleted " + deletedApplications + " applications");

            // ============================================================
            // STEP 9: DELETE/CLEAN EVENTS (created by user)
            // ============================================================
            var events = eventRepository.findAll();
            var userEvents = events.stream()
                    .filter(e -> id.equals(e.getOrganizer()))
                    .toList();
            eventRepository.deleteAll(userEvents);
            deletedEvents = userEvents.size();
            System.out.println("  ✓ Deleted " + deletedEvents + " events");

            // ============================================================
            // STEP 10: DELETE COLLAB PODS (created by user)
            // ============================================================
            var createdPods = collabPodRepository.findByCreatorId(id);
            collabPodRepository.deleteAll(createdPods);
            deletedPods = createdPods.size();
            System.out.println("  ✓ Deleted " + deletedPods + " collab pods");

            // ============================================================
            // STEP 11: ANONYMIZE POSTS (preserve records)
            // ============================================================
            List<Post> userPosts = postRepository.findByAuthorId(id);
            for (Post post : userPosts) {
                post.setAuthorId("deleted-user");
                postRepository.save(post);
            }
            anonymizedPosts = userPosts.size();
            System.out.println("  ✓ Anonymized " + anonymizedPosts + " posts");

            // ============================================================
            // STEP 12: ANONYMIZE COMMENTS (preserve records)
            // ============================================================
            List<Comment> userComments = commentRepository.findByAuthorId(id);
            for (Comment comment : userComments) {
                comment.setAuthorId("deleted-user");
                comment.setAuthorName("Deleted User");
                commentRepository.save(comment);
            }
            anonymizedComments = userComments.size();
            System.out.println("  ✓ Anonymized " + anonymizedComments + " comments");

            // ============================================================
            // STEP 13: DELETE USER
            // ============================================================
            userRepository.deleteById(id);
            System.out.println("[AdminController] ✅ USER COMPLETELY DELETED: " + userEmail);

            // Build response with deletion stats
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "User and all associated data deleted successfully");
            response.put("userId", id);
            response.put("email", userEmail);

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("achievements", deletedAchievements);
            stats.put("inboxItems", deletedInboxItems);
            stats.put("podCooldowns", deletedPodCooldowns);
            stats.put("eventReminders", deletedEventReminders);
            stats.put("conversations", deletedConversations);
            stats.put("messages", deletedMessages);
            stats.put("buddyBeacons", deletedBuddyBeacons);
            stats.put("reports", deletedReports);
            stats.put("applications", deletedApplications);
            stats.put("events", deletedEvents);
            stats.put("collabPods", deletedPods);
            stats.put("postsAnonymized", anonymizedPosts);
            stats.put("commentsAnonymized", anonymizedComments);
            stats.put("totalDataItemsRemoved", deletedAchievements + deletedInboxItems + deletedPodCooldowns
                    + deletedEventReminders + deletedConversations + deletedMessages + deletedBuddyBeacons
                    + deletedReports + deletedApplications + deletedEvents + deletedPods
                    + anonymizedPosts + anonymizedComments);

            response.put("deletionStats", stats);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("[AdminController] ❌ Error deleting user: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Error deleting user: " + e.getMessage(),
                    "status", "FAILED"));
        }
    }

    /**
     * Promote a user to COLLEGE_HEAD role
     * ✅ CRITICAL: ONLY adds Campus Catalyst badge
     * ✅ Does NOT modify isDev, so Founding Dev will NEVER be added
     * ✅ Completely separate from Founding Dev badge logic
     */
    @PatchMapping("/users/{id}/promote")
    public ResponseEntity<?> promoteUser(@PathVariable String id) {
        try {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            System.out.println("[AdminController] 📝 Promoting user: " + user.getEmail());
            System.out.println("[AdminController]    Before: role=" + user.getRole() + ", isDev=" + user.isDev());
            System.out.println("[AdminController]    Current badges: " + user.getBadges());

            // ✅ CRITICAL: Set role to COLLEGE_HEAD
            user.setRole("COLLEGE_HEAD");

            // ✅ CRITICAL: DO NOT modify isDev - it stays as is
            // This ensures Founding Dev logic is NEVER triggered by promotion
            System.out.println("[AdminController]    After role change: isDev=" + user.isDev() + " (UNCHANGED)");

            // ✅ CRITICAL: Manually add ONLY Campus Catalyst badge
            // Do not call syncUserBadges to avoid any cross-contamination with isDev logic
            if (user.getBadges() == null) {
                user.setBadges(new ArrayList<>());
            }

            // Remove any accidentally present Founding Dev badge (safety check)
            // This should NEVER happen, but just in case
            if (user.getBadges().contains("Founding Dev")) {
                System.out.println(
                        "[AdminController] ⚠️ WARNING: User had Founding Dev badge pre-promotion. Keeping it as is (not our logic to remove)");
            }

            // Add Campus Catalyst if not present
            if (!user.getBadges().contains("Campus Catalyst")) {
                user.getBadges().add("Campus Catalyst");
                System.out.println("[AdminController]    ✅ ADDED: Campus Catalyst badge");
            } else {
                System.out.println("[AdminController]    ℹ️ Already has Campus Catalyst badge");
            }

            userRepository.save(user);

            System.out.println("[AdminController] ✅ Promotion complete:");
            System.out.println("[AdminController]    After: role=" + user.getRole() + ", isDev=" + user.isDev());
            System.out.println("[AdminController]    Final badges: " + user.getBadges());

            return ResponseEntity.ok(Map.of(
                    "message", "User promoted to College Head",
                    "userId", user.getId(),
                    "email", user.getEmail(),
                    "role", "COLLEGE_HEAD",
                    "badges", user.getBadges(),
                    "newBadge", "Campus Catalyst",
                    "isDev", user.isDev()));
        } catch (Exception e) {
            System.err.println("[AdminController] Error promoting user: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error promoting user: " + e.getMessage());
        }
    }

    /**
     * Get badge distribution analytics
     */
    @GetMapping("/badges/stats")
    public ResponseEntity<?> getBadgeStats() {
        try {
            List<User> allUsers = userRepository.findAll();
            Map<String, Integer> badgeStats = new HashMap<>();

            // Aggregate badge counts
            for (User user : allUsers) {
                if (user.getBadges() != null) {
                    for (String badge : user.getBadges()) {
                        badgeStats.put(badge, badgeStats.getOrDefault(badge, 0) + 1);
                    }
                }
            }

            return ResponseEntity.ok(badgeStats);
        } catch (Exception e) {
            System.err.println("[AdminController] Error fetching badge stats: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error fetching badge stats: " + e.getMessage());
        }
    }

    /**
     * Get maintenance mode status
     */
    @GetMapping("/system/maintenance")
    public ResponseEntity<?> getMaintenanceStatus() {
        try {
            List<SystemSettings> settings = systemSettingsRepository.findAll();
            boolean maintenanceMode = false;

            if (!settings.isEmpty()) {
                maintenanceMode = settings.get(0).isMaintenanceMode();
            }

            return ResponseEntity.ok(Map.of("maintenanceMode", maintenanceMode));
        } catch (Exception e) {
            System.err.println("[AdminController] Error fetching maintenance status: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error fetching maintenance status");
        }
    }

    /**
     * Toggle maintenance mode
     */
    @PostMapping("/system/maintenance/toggle")
    public ResponseEntity<?> toggleMaintenance(@RequestBody Map<String, Boolean> request) {
        try {
            boolean maintenanceMode = request.getOrDefault("enabled", false);

            // Get or create system settings
            List<SystemSettings> settings = systemSettingsRepository.findAll();
            SystemSettings systemSettings;

            if (settings.isEmpty()) {
                systemSettings = new SystemSettings();
            } else {
                systemSettings = settings.get(0);
            }

            // Update and save
            systemSettings.setMaintenanceMode(maintenanceMode);
            systemSettingsRepository.save(systemSettings);

            System.out.println("[AdminController] ✅ Maintenance mode set to: " + maintenanceMode);

            return ResponseEntity.ok(Map.of(
                    "message", "Maintenance mode " + (maintenanceMode ? "enabled" : "disabled"),
                    "maintenanceMode", maintenanceMode));
        } catch (Exception e) {
            System.err.println("[AdminController] Error toggling maintenance: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error toggling maintenance mode");
        }
    }

    /**
     * ✅ CLEANUP ENDPOINT: Fix already promoted users
     * Removes incorrectly assigned "Founding Dev" badge from COLLEGE_HEAD users
     * where isDev=false
     * Ensures promoted users have ONLY "Campus Catalyst" badge (not both)
     */
    @PostMapping("/cleanup/fix-promoted-users")
    public ResponseEntity<?> cleanupPromotedUsers() {
        try {
            List<User> allUsers = userRepository.findAll();
            int fixed = 0;
            List<String> fixedEmails = new ArrayList<>();

            System.out.println("\n[AdminController] 🔧 CLEANUP: Fixing promoted users...");

            for (User user : allUsers) {
                // Only check COLLEGE_HEAD users
                if (!"COLLEGE_HEAD".equals(user.getRole())) {
                    continue;
                }

                String userEmail = user.getEmail();
                boolean hasCampusCatalyst = user.getBadges() != null && user.getBadges().contains("Campus Catalyst");
                boolean hasFoundingDev = user.getBadges() != null && user.getBadges().contains("Founding Dev");
                boolean isDev = user.isDev();

                // PROBLEM: User is COLLEGE_HEAD but isDev=false, and they have Founding Dev
                // badge
                // This shouldn't happen after promotion
                if (!isDev && hasFoundingDev) {
                    System.out.println("[AdminController] ⚠️ FOUND ISSUE: " + userEmail);
                    System.out.println("         Role: COLLEGE_HEAD, isDev: false, but has Founding Dev badge");

                    // Remove only Founding Dev badge
                    user.getBadges().remove("Founding Dev");

                    // Ensure they have Campus Catalyst
                    if (!hasCampusCatalyst) {
                        user.getBadges().add("Campus Catalyst");
                        System.out.println("         ACTION: Removed 'Founding Dev', added 'Campus Catalyst'");
                    } else {
                        System.out.println("         ACTION: Removed 'Founding Dev' (already has Campus Catalyst)");
                    }

                    userRepository.save(user);
                    fixed++;
                    fixedEmails.add(userEmail);
                }
            }

            System.out.println("[AdminController] ✅ CLEANUP COMPLETE: Fixed " + fixed + " users");
            System.out.println("[AdminController]    Fixed users: " + fixedEmails);

            return ResponseEntity.ok(Map.of(
                    "message", "Cleanup completed",
                    "usersFixed", fixed,
                    "fixedEmails", fixedEmails));
        } catch (Exception e) {
            System.err.println("[AdminController] Error during cleanup: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error during cleanup: " + e.getMessage());
        }
    }
}
