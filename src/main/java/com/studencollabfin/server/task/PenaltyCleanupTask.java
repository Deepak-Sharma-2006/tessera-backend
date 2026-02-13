package com.studencollabfin.server.task;

import com.studencollabfin.server.model.User;
import com.studencollabfin.server.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
@EnableScheduling
public class PenaltyCleanupTask {

    @Autowired
    private UserRepository userRepository;

    /**
     * ✅ SCHEDULED CLEANUP: Runs every minute to check for expired penalties
     * Automatically removes "Spam Alert" badge after 24 hours
     */
    @Scheduled(fixedRate = 60000) // 60 seconds = 1 minute
    public void cleanupExpiredPenalties() {
        try {
            LocalDateTime now = LocalDateTime.now();

            // Find all users with active penalty that has expired
            List<User> allUsers = userRepository.findAll();

            for (User user : allUsers) {
                // Check if user has penalty expiry and it has passed
                if (user.getPenaltyExpiry() != null && now.isAfter(user.getPenaltyExpiry())) {
                    System.out.println("⏰ PENALTY CLEANUP: Processing expired penalty for user " + user.getId());

                    // Remove Spam Alert badge from displayed badges
                    if (user.getDisplayedBadges() != null) {
                        user.getDisplayedBadges().remove("Spam Alert");
                    }

                    // Clear penalty expiry
                    user.setPenaltyExpiry(null);

                    // Save updated user
                    userRepository.save(user);

                    System.out.println("✅ PENALTY EXPIRED: 'Spam Alert' badge removed for " + user.getId());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ PENALTY CLEANUP ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✅ CHECK BAN STATUS: Runs every 5 minutes to validate banned users
     * Can be used to enforce logout or access restrictions
     */
    @Scheduled(fixedRate = 300000) // 300 seconds = 5 minutes
    public void checkBanStatus() {
        try {
            List<User> bannedUsers = userRepository.findAll().stream()
                    .filter(User::isBanned)
                    .toList();

            if (!bannedUsers.isEmpty()) {
                System.out.println("🚫 BAN CHECK: " + bannedUsers.size() + " banned users found");
                for (User user : bannedUsers) {
                    System.out.println("   - User " + user.getId() + " (Reports: " + user.getReportCount() + ")");
                }
            }
        } catch (Exception e) {
            System.err.println("❌ BAN CHECK ERROR: " + e.getMessage());
        }
    }
}
