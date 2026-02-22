package com.studencollabfin.server.service;

import com.studencollabfin.server.model.User;
import com.studencollabfin.server.model.Achievement;
import com.studencollabfin.server.repository.UserRepository;
import com.studencollabfin.server.repository.AchievementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

@Service
public class UserService implements UserDetailsService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AchievementRepository achievementRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private HardModeBadgeService hardModeBadgeService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                new ArrayList<>());
    }

    public User authenticate(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        return user;
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    @SuppressWarnings("null")
    public User findById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
    }

    @SuppressWarnings("null")
    public User getUserById(String userId) {
        // Return null instead of throwing exception to allow graceful fallback in
        // controller
        return userRepository.findById(userId).orElse(null);
    }

    // XP constants
    private static final int XP_PER_POST = 10;
    private static final int XP_PER_COMMENT = 5;
    private static final int XP_PER_EVENT_CREATED = 20;
    private static final int XP_PER_EVENT_ATTENDED = 15;
    private static final int XP_PER_PROJECT_COMPLETED = 50;
    private static final int XP_PER_COLLAB_POD_CREATED = 25;

    @SuppressWarnings("null")
    public void awardXP(String userId, int xpAmount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        int newXP = user.getXp() + xpAmount;
        int currentLevel = user.getLevel();

        // Check if user should level up (100 XP per level)
        while (newXP >= 100) {
            currentLevel++;
            newXP -= 100;
        }

        user.setXp(newXP);
        user.setLevel(currentLevel);
        user.setTotalXp(user.getTotalXp() + xpAmount);
        userRepository.save(user);

        // Check for achievements
        checkAndAwardAchievements(user);
    }

    @SuppressWarnings("null")
    public User updateUserProfile(String userId, User profileData) {
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // ✅ FIX: Only update fields that are actually provided (not null)
        // fullName and collegeName are immutable after initial setup
        if (profileData.getFullName() != null) {
            existingUser.setFullName(profileData.getFullName());
        }
        if (profileData.getCollegeName() != null) {
            existingUser.setCollegeName(profileData.getCollegeName());
        }
        
        // Update editable profile fields
        if (profileData.getYearOfStudy() != null) {
            existingUser.setYearOfStudy(profileData.getYearOfStudy());
        }
        if (profileData.getDepartment() != null) {
            existingUser.setDepartment(profileData.getDepartment());
        }
        if (profileData.getSkills() != null) {
            existingUser.setSkills(profileData.getSkills());
        }
        if (profileData.getRolesOpenTo() != null) {
            existingUser.setRolesOpenTo(profileData.getRolesOpenTo());
        }
        if (profileData.getGoals() != null) {
            existingUser.setGoals(profileData.getGoals());
        }
        if (profileData.getExcitingTags() != null) {
            existingUser.setExcitingTags(profileData.getExcitingTags());
        }
        if (profileData.getLinkedinUrl() != null) {
            existingUser.setLinkedinUrl(profileData.getLinkedinUrl());
        }
        if (profileData.getGithubUrl() != null) {
            existingUser.setGithubUrl(profileData.getGithubUrl());
        }
        if (profileData.getPortfolioUrl() != null) {
            existingUser.setPortfolioUrl(profileData.getPortfolioUrl());
        }
        if (profileData.getProfilePicUrl() != null) {
            existingUser.setProfilePicUrl(profileData.getProfilePicUrl());
        }

        existingUser.setProfileCompleted(true);

        return userRepository.save(existingUser);
    }

    public List<Achievement> getUserAchievements(String userId) {
        return achievementRepository.findByUserId(userId);
    }

    public void checkAndAwardAchievements(User user) {
        List<Achievement> possibleAchievements = achievementRepository.findByUserIdAndIsUnlockedTrue(user.getId());

        for (Achievement achievement : possibleAchievements) {
            if (!achievement.isUnlocked() && checkAchievementRequirements(user, achievement)) {
                achievement.setUnlocked(true);
                achievement.setUnlockedAt(LocalDateTime.now());
                achievementRepository.save(achievement);

                // Award XP for unlocking achievement
                awardXP(user.getId(), achievement.getXpValue());
            }
        }
    }

    private boolean checkAchievementRequirements(User user, Achievement achievement) {
        // Implement achievement requirement checking logic here
        return false; // Placeholder
    }

    // XP award methods for different actions
    public void awardPostXP(String userId) {
        awardXP(userId, XP_PER_POST);
    }

    public void awardCommentXP(String userId) {
        awardXP(userId, XP_PER_COMMENT);
    }

    public void awardEventCreationXP(String userId) {
        awardXP(userId, XP_PER_EVENT_CREATED);
    }

    public void awardEventAttendanceXP(String userId) {
        awardXP(userId, XP_PER_EVENT_ATTENDED);
    }

    public void awardProjectCompletionXP(String userId) {
        awardXP(userId, XP_PER_PROJECT_COMPLETED);
    }

    public void awardCollabPodCreationXP(String userId) {
        awardXP(userId, XP_PER_COLLAB_POD_CREATED);
    }

    public User findOrCreateUserByOauth(String oauthId, String name, String pictureUrl, String email) {
        // Find an existing user by their unique LinkedIn ID
        return userRepository.findByOauthId(oauthId)
                .map(existingUser -> {
                    // If user exists, update their name and picture from LinkedIn, as it might have
                    // changed
                    existingUser.setFullName(name);
                    existingUser.setProfilePicUrl(pictureUrl);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    // If user does not exist, check if an account with that email already exists
                    Optional<User> userByEmail = userRepository.findByEmail(email);
                    if (userByEmail.isPresent()) {
                        // An account with this email exists. Link it to the LinkedIn account.
                        User existingUser = userByEmail.get();
                        existingUser.setOauthId(oauthId);
                        existingUser.setFullName(name);
                        existingUser.setProfilePicUrl(pictureUrl);
                        return userRepository.save(existingUser);
                    } else {
                        // No account exists, create a brand new one
                        User newUser = new User();
                        newUser.setOauthId(oauthId);
                        newUser.setFullName(name);
                        newUser.setProfilePicUrl(pictureUrl);
                        newUser.setEmail(email);

                        // ✅ CRITICAL FIX: Extract and set collegeName from email domain
                        String collegeName = deriveCollegeFromEmail(email);
                        newUser.setCollegeName(collegeName);

                        // 📊 XP SYSTEM: Start all new users at Level 0
                        newUser.setLevel(0);
                        newUser.setXp(0);
                        newUser.setTotalXp(0);
                        newUser.setXpMultiplier(1.0);
                        newUser.setProfileCompleted(false);

                        // Set creation timestamps
                        LocalDateTime now = LocalDateTime.now();
                        newUser.setCreatedAt(now);
                        newUser.setJoinedDate(now);

                        User savedUser = userRepository.save(newUser);

                        // ✅ Initialize hard-mode badges for new user
                        if (hardModeBadgeService != null) {
                            hardModeBadgeService.initializeHardModeBadgesForUser(savedUser.getId());
                        }

                        return savedUser;
                    }
                });
    }

    public boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    public User register(String email, String password) {
        // Check if email already exists
        if (emailExists(email)) {
            throw new RuntimeException("Email already registered");
        }

        // Create new user
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPassword(passwordEncoder.encode(password));

        // ✅ CRITICAL FIX: Extract and set collegeName from email domain
        String collegeName = deriveCollegeFromEmail(email);
        newUser.setCollegeName(collegeName);

        // 📊 XP SYSTEM: Start all new users at Level 0
        newUser.setLevel(0);
        newUser.setXp(0);
        newUser.setTotalXp(0);
        newUser.setXpMultiplier(1.0);
        newUser.setProfileCompleted(false);

        // Set creation timestamps
        LocalDateTime now = LocalDateTime.now();
        newUser.setCreatedAt(now);
        newUser.setJoinedDate(now);

        User savedUser = userRepository.save(newUser);

        // ✅ Initialize hard-mode badges for new user
        if (hardModeBadgeService != null) {
            hardModeBadgeService.initializeHardModeBadgesForUser(savedUser.getId());
        }

        return savedUser;
    }

    /**
     * ✅ HELPER: Derive college name from email domain
     * Maps known domains to college names
     */
    private String deriveCollegeFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "Unknown College";
        }

        String domain = email.toLowerCase().substring(email.lastIndexOf("@") + 1);

        // College mappings based on email domain
        if (domain.contains("sinhgad")) {
            return "Sinhgad College of Engineering";
        } else if (domain.contains("iit")) {
            return "IIT";
        } else if (domain.contains("mit")) {
            return "MIT";
        } else if (domain.contains("stanford")) {
            return "Stanford";
        } else if (domain.contains("symbiosis")) {
            return "SYMBIOSIS";
        } else if (domain.contains("manipal")) {
            return "Manipal";
        } else if (domain.contains("vit")) {
            return "VIT";
        } else if (domain.contains("bits")) {
            return "BITS Pilani";
        }

        // Default: Use domain name as college name
        String domainPrefix = domain.split("\\.")[0].toUpperCase();
        return domainPrefix.isEmpty() ? "Unknown College" : domainPrefix;
    }

}
