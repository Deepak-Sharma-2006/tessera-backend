package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.Report;
import com.studencollabfin.server.model.User;
import com.studencollabfin.server.dto.ReportRequest;
import com.studencollabfin.server.repository.ReportRepository;
import com.studencollabfin.server.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/reports")
@CrossOrigin(origins = "*")
public class UserReportController {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Submit a report against a user
     * Implements meritocracy system:
     * - Increments reportCount
     * - Adds "Spam Alert" badge and locks it for 24 hours
     * - Bans user if reportCount >= 3
     */
    @PostMapping
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> reportUser(@RequestBody ReportRequest reportRequest) {
        try {
            // Get reporter ID from request (passed by frontend)
            String reporterId = reportRequest.getReporterId();
            if (reporterId == null || reporterId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Must be logged in to report"));
            }

            // Validate reported user exists
            User reportedUser = userRepository.findById(reportRequest.getReportedUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Prevent self-reporting
            if (reportRequest.getReportedUserId().equals(reporterId)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Cannot report yourself"));
            }

            // ✅ CREATE AND SAVE REPORT RECORD TO MONGODB
            Report report = new Report();
            report.setReportedUserId(reportRequest.getReportedUserId());
            report.setReporterId(reporterId);
            report.setReason(reportRequest.getReason());
            report.setDetails(reportRequest.getDetails());
            report.setCreatedAt(LocalDateTime.now());
            report.setResolved(false);
            Report savedReport = reportRepository.save(report);
            System.out.println("✅ MONGODB: Report saved with ID " + savedReport.getId());

            // ✅ INCREMENT REPORT COUNT
            reportedUser.setReportCount(reportedUser.getReportCount() + 1);
            System.out.println("📊 REPORT SYSTEM: User " + reportedUser.getId() +
                    " report count incremented to " + reportedUser.getReportCount());

            // ✅ ADD SPAM ALERT BADGE
            String spamBadgeName = "Spam Alert";
            if (!reportedUser.getBadges().contains(spamBadgeName)) {
                reportedUser.getBadges().add(spamBadgeName);
                System.out.println("🚨 REPORT SYSTEM: 'Spam Alert' badge added to " + reportedUser.getId());
            }

            // ✅ SET PENALTY EXPIRY (24 HOURS FROM NOW)
            LocalDateTime penaltyExpiry = LocalDateTime.now().plusHours(24);
            reportedUser.setPenaltyExpiry(penaltyExpiry);
            System.out.println("⏰ REPORT SYSTEM: Penalty expires at " + penaltyExpiry);

            // ✅ Force Spam Alert badge to featured badges (public profile)
            if (!reportedUser.getDisplayedBadges().contains(spamBadgeName)) {
                reportedUser.getDisplayedBadges().add(spamBadgeName);
                System.out.println(
                        "🏆 REPORT SYSTEM: Spam Alert badge forced to public profile for " + reportedUser.getId());
            }

            // ✅ EVOLVING BAN LOGIC: Ban if reportCount >= 3
            if (reportedUser.getReportCount() >= 3) {
                reportedUser.setBanned(true);
                System.out.println("🚫 BAN SYSTEM: User " + reportedUser.getId() +
                        " BANNED! (Report count: " + reportedUser.getReportCount() + ")");
            }

            // Save updated user
            userRepository.save(reportedUser);

            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Report submitted successfully");
            response.put("reportCount", reportedUser.getReportCount());
            response.put("banned", reportedUser.isBanned());
            response.put("penaltyExpiry", penaltyExpiry);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ REPORT SYSTEM ERROR: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get all reports for a specific user (admin only)
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserReports(@PathVariable String userId) {
        try {
            List<Report> reports = reportRepository.findByReportedUserId(userId);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Check if user is banned or has active penalty
     */
    @GetMapping("/{userId}/status")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getUserReportStatus(@PathVariable String userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Map<String, Object> status = new HashMap<>();
            status.put("userId", userId);
            status.put("reportCount", user.getReportCount());
            status.put("isBanned", user.isBanned());
            status.put("penaltyExpiry", user.getPenaltyExpiry());
            status.put("isPenaltyActive", user.getPenaltyExpiry() != null &&
                    LocalDateTime.now().isBefore(user.getPenaltyExpiry()));

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Clear penalty after 24 hours (can be called by scheduled task or manual admin
     * action)
     */
    @PostMapping("/{userId}/clear-penalty")
    public ResponseEntity<?> clearPenalty(@PathVariable String userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Only clear if penalty has expired
            if (user.getPenaltyExpiry() != null &&
                    LocalDateTime.now().isAfter(user.getPenaltyExpiry())) {

                user.getPenaltyExpiry();
                user.getDisplayedBadges().remove("Spam Alert");
                userRepository.save(user);

                System.out.println("✅ PENALTY CLEARED: Spam Alert badge removed for " + userId);
                return ResponseEntity.ok(Map.of("message", "Penalty cleared"));
            }

            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Penalty is still active"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
