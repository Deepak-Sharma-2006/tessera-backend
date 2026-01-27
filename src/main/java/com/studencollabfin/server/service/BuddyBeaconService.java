package com.studencollabfin.server.service;

import com.studencollabfin.server.model.*;
import com.studencollabfin.server.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BuddyBeaconService {
    @Autowired
    private BuddyBeaconRepository beaconRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UserRepository userRepository;

    // --- Beacon Post Logic ---
    public BuddyBeacon createBeaconPost(String userId, BuddyBeacon beaconPost) {
        beaconPost.setAuthorId(userId);
        beaconPost.setCreatedAt(LocalDateTime.now());
        beaconPost.setStatus("OPEN");
        if (beaconPost.getCurrentTeamMemberIds() == null) {
            beaconPost.setCurrentTeamMemberIds(new ArrayList<>());
        }
        beaconPost.getCurrentTeamMemberIds().add(userId);
        return beaconRepository.save(beaconPost);
    }

    /**
     * Aggregates all BuddyBeacon and TeamFindingPost posts for the global feed.
     * TeamFindingPosts are included if ACTIVE or CLOSED (<24h old).
     */
    public List<Map<String, Object>> getAllBeaconPosts(String currentUserId) {
        List<Map<String, Object>> feed = new ArrayList<>();
        // Add BuddyBeacon posts (legacy)
        List<BuddyBeacon> beacons = beaconRepository.findAll();
        for (BuddyBeacon beacon : beacons) {
            Map<String, Object> postMap = new HashMap<>();
            postMap.put("type", "BuddyBeacon");
            postMap.put("post", beacon);
            long hoursElapsed = beacon.getCreatedAt() == null ? 0
                    : java.time.Duration.between(beacon.getCreatedAt(), LocalDateTime.now()).toHours();
            postMap.put("hoursElapsed", hoursElapsed);
            postMap.put("status", hoursElapsed < 20 ? "ACTIVE" : (hoursElapsed < 24 ? "REVIEW" : "EXPIRED"));
            boolean hasApplied = applicationRepository.findByBeaconId(beacon.getId()).stream()
                    .anyMatch(a -> a.getApplicantId().equals(currentUserId));
            postMap.put("hasApplied", hasApplied);
            postMap.put("hostId", beacon.getAuthorId());
            feed.add(postMap);
        }
        // Add TeamFindingPosts (universal visibility)
        List<TeamFindingPost> teamPosts = postRepository.findAll().stream()
                .filter(p -> p instanceof TeamFindingPost)
                .map(p -> (TeamFindingPost) p)
                .filter(p -> {
                    PostState state = p.computePostState();
                    return state == PostState.ACTIVE || state == PostState.CLOSED;
                })
                .toList();
        for (TeamFindingPost post : teamPosts) {
            Map<String, Object> postMap = new HashMap<>();
            postMap.put("type", "TeamFindingPost");
            postMap.put("post", post);
            long hoursElapsed = post.getCreatedAt() == null ? 0
                    : java.time.Duration.between(post.getCreatedAt(), LocalDateTime.now()).toHours();
            postMap.put("hoursElapsed", hoursElapsed);
            String status = post.computePostState().name();
            postMap.put("status", status);
            boolean hasApplied = applicationRepository.findByBeaconId(post.getId()).stream()
                    .anyMatch(a -> a.getApplicantId().equals(currentUserId));
            postMap.put("hasApplied", hasApplied);
            postMap.put("hostId", post.getAuthorId());
            feed.add(postMap);
        }
        return feed;
    }

    /**
     * Returns all posts the current user has applied to, with status and post
     * details.
     */
    public List<Map<String, Object>> getAppliedPosts(String applicantId) {
        List<Application> applications = applicationRepository.findByApplicantId(applicantId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Application app : applications) {
            Map<String, Object> map = new HashMap<>();
            map.put("applicationId", app.getId());
            map.put("applicationStatus", app.getStatus());
            map.put("beaconId", app.getBeaconId());
            // Try BuddyBeacon
            if (app.getBeaconId() != null) {
                @SuppressWarnings("null")
                var beaconOpt = beaconRepository.findById((String) app.getBeaconId());
                beaconOpt.ifPresent(beacon -> {
                    map.put("postType", "BuddyBeacon");
                    map.put("post", beacon);
                });
                // Try TeamFindingPost
                @SuppressWarnings("null")
                var postOpt = postRepository.findById((String) app.getBeaconId());
                postOpt.ifPresent(post -> {
                    if (post instanceof TeamFindingPost tfp) {
                        map.put("postType", "TeamFindingPost");
                        map.put("post", tfp);
                    }
                });
            }
            result.add(map);
        }
        return result;
    }

    /**
     * Returns posts created by the authenticated user, with nested applicants and
     * their public profiles.
     */
    public List<Map<String, Object>> getMyPosts(String userId) {
        List<Map<String, Object>> result = new ArrayList<>();

        // Handle null or empty userId - return empty list
        if (userId == null || userId.trim().isEmpty()) {
            return result;
        }

        // BuddyBeacon posts
        List<BuddyBeacon> myBeacons = beaconRepository.findAll().stream()
                .filter(b -> userId.equals(b.getAuthorId()))
                .toList();
        for (BuddyBeacon beacon : myBeacons) {
            Map<String, Object> postMap = new HashMap<>();
            postMap.put("post", beacon);
            List<Application> apps = applicationRepository.findByBeaconId(beacon.getId());
            List<Map<String, Object>> applicants = new ArrayList<>();
            for (Application app : apps) {
                Map<String, Object> applicant = new HashMap<>();
                applicant.put("application", app);
                if (app.getApplicantId() != null) {
                    @SuppressWarnings("null")
                    var userOpt = userRepository.findById((String) app.getApplicantId());
                    userOpt.ifPresent(u -> applicant.put("profile", u));
                }
                applicants.add(applicant);
            }
            postMap.put("applicants", applicants);
            result.add(postMap);
        }
        // TeamFindingPosts
        List<TeamFindingPost> myTeamPosts = postRepository.findAll().stream()
                .filter(p -> p instanceof TeamFindingPost && userId.equals(p.getAuthorId()))
                .map(p -> (TeamFindingPost) p)
                .toList();
        for (TeamFindingPost post : myTeamPosts) {
            Map<String, Object> postMap = new HashMap<>();
            postMap.put("post", post);
            List<Application> apps = applicationRepository.findByBeaconId(post.getId());
            List<Map<String, Object>> applicants = new ArrayList<>();
            for (Application app : apps) {
                Map<String, Object> applicant = new HashMap<>();
                applicant.put("application", app);
                if (app.getApplicantId() != null) {
                    @SuppressWarnings("null")
                    var userOpt = userRepository.findById((String) app.getApplicantId());
                    userOpt.ifPresent(u -> applicant.put("profile", u));
                }
                applicants.add(applicant);
            }
            postMap.put("applicants", applicants);
            result.add(postMap);
        }
        return result;
    }

    /**
     * Application logic: Only allow if post is ACTIVE (<20h).
     */
    @SuppressWarnings("null")
    public Application applyToBeaconPost(String beaconId, String applicantId, Application application) {
        System.out.println("Received beaconId: " + beaconId); // Debugging beaconId
        // Try BuddyBeacon first
        Optional<BuddyBeacon> beaconOpt = beaconRepository.findById((String) beaconId);
        if (beaconOpt.isPresent()) {
            BuddyBeacon beacon = beaconOpt.get();
            if (beacon.getCreatedAt() != null) {
                long hours = java.time.Duration.between(beacon.getCreatedAt(), LocalDateTime.now()).toHours();
                if (hours >= 20)
                    throw new RuntimeException("Applications are closed for this post");
            }
            application.setBeaconId(beaconId);
            application.setApplicantId(applicantId);
            application.setCreatedAt(LocalDateTime.now());
            application.setStatus(Application.Status.PENDING);
            return applicationRepository.save(application);
        }
        // Try TeamFindingPost
        Optional<Post> postOpt = postRepository.findById((String) beaconId);
        if (postOpt.isPresent() && postOpt.get() instanceof TeamFindingPost teamPost) {
            if (teamPost.computePostState() != PostState.ACTIVE) {
                throw new RuntimeException("Applications are closed for this post");
            }
            application.setBeaconId(beaconId);
            application.setApplicantId(applicantId);
            application.setCreatedAt(LocalDateTime.now());
            application.setStatus(Application.Status.PENDING);
            return applicationRepository.save(application);
        }
        throw new RuntimeException("Post not found");
    }

    /**
     * Accept an application (host only, up to capacity, invite to Collab Pod).
     */
    public Application acceptApplication(String postId, String applicationId, String userId) {
        // Try BuddyBeacon
        if (postId != null) {
            Optional<BuddyBeacon> beaconOpt = beaconRepository.findById(postId);
            if (beaconOpt.isPresent()) {
                BuddyBeacon beacon = beaconOpt.get();
                if (!userId.equals(beacon.getAuthorId()))
                    throw new RuntimeException("Not authorized");
                if (applicationId != null) {
                    Application app = applicationRepository.findById(applicationId).orElseThrow();
                    if (app.getStatus() != Application.Status.PENDING)
                        throw new RuntimeException("Already processed");
                    List<String> members = beacon.getCurrentTeamMemberIds();
                    if (members.size() >= beacon.getMaxTeamSize())
                        throw new RuntimeException("Team is full");
                    app.setStatus(Application.Status.ACCEPTED);
                    members.add(app.getApplicantId());
                    beacon.setCurrentTeamMemberIds(members);
                    beaconRepository.save(beacon);
                    // TODO: Send Collab Pod invitation logic here
                    return applicationRepository.save(app);
                }
            }
        }
        // Try TeamFindingPost
        if (postId != null) {
            Optional<Post> postOpt = postRepository.findById(postId);
            if (postOpt.isPresent() && postOpt.get() instanceof TeamFindingPost teamPost) {
                if (!userId.equals(teamPost.getAuthorId()))
                    throw new RuntimeException("Not authorized");
                if (applicationId != null) {
                    Application app = applicationRepository.findById(applicationId).orElseThrow();
                    if (app.getStatus() != Application.Status.PENDING)
                        throw new RuntimeException("Already processed");
                    List<String> members = teamPost.getCurrentTeamMembers();
                    if (members == null)
                        members = new ArrayList<>();
                    if (members.size() >= teamPost.getMaxTeamSize())
                        throw new RuntimeException("Team is full");
                    app.setStatus(Application.Status.ACCEPTED);
                    members.add(app.getApplicantId());
                    teamPost.setCurrentTeamMembers(members);
                    postRepository.save(teamPost);
                    // TODO: Send Collab Pod invitation logic here
                    return applicationRepository.save(app);
                }
            }
        }
        throw new RuntimeException("Post not found");
    }

    /**
     * Reject an application (host only, with reason and optional note).
     */
    public Application rejectApplication(String postId, String applicationId, String userId, RejectionReason reason,
            String note) {
        // Try BuddyBeacon
        if (postId != null) {
            Optional<BuddyBeacon> beaconOpt = beaconRepository.findById(postId);
            if (beaconOpt.isPresent()) {
                BuddyBeacon beacon = beaconOpt.get();
                if (!userId.equals(beacon.getAuthorId()))
                    throw new RuntimeException("Not authorized");
                if (applicationId != null) {
                    Application app = applicationRepository.findById(applicationId).orElseThrow();
                    if (app.getStatus() != Application.Status.PENDING)
                        throw new RuntimeException("Already processed");
                    app.setStatus(Application.Status.REJECTED);
                    app.setRejectionReason(reason);
                    app.setRejectionNote(note);
                    return applicationRepository.save(app);
                }
            }
        }
        // Try TeamFindingPost
        if (postId != null) {
            Optional<Post> postOpt = postRepository.findById(postId);
            if (postOpt.isPresent() && postOpt.get() instanceof TeamFindingPost teamPost) {
                if (!userId.equals(teamPost.getAuthorId()))
                    throw new RuntimeException("Not authorized");
                if (applicationId != null) {
                    Application app = applicationRepository.findById(applicationId).orElseThrow();
                    if (app.getStatus() != Application.Status.PENDING)
                        throw new RuntimeException("Already processed");
                    app.setStatus(Application.Status.REJECTED);
                    app.setRejectionReason(reason);
                    app.setRejectionNote(note);
                    return applicationRepository.save(app);
                }
            }
        }
        throw new RuntimeException("Post not found");
    }

    /**
     * Delete a post (host only, only if EXPIRED or CLOSED).
     */
    public void deleteMyPost(String postId, String userId) {
        // Try BuddyBeacon
        if (postId != null) {
            Optional<BuddyBeacon> beaconOpt = beaconRepository.findById(postId);
            if (beaconOpt.isPresent()) {
                BuddyBeacon beacon = beaconOpt.get();
                if (!userId.equals(beacon.getAuthorId()))
                    throw new RuntimeException("Not authorized");
                // Allow delete if EXPIRED (older than 24h)
                if (beacon.getCreatedAt() != null) {
                    long hours = java.time.Duration.between(beacon.getCreatedAt(), LocalDateTime.now()).toHours();
                    if (hours < 24)
                        throw new RuntimeException("Cannot delete active post");
                }
                beaconRepository.deleteById(postId);
                return;
            }
        }
        // Try TeamFindingPost
        if (postId != null) {
            Optional<Post> postOpt = postRepository.findById(postId);
            if (postOpt.isPresent() && postOpt.get() instanceof TeamFindingPost teamPost) {
                if (!userId.equals(teamPost.getAuthorId()))
                    throw new RuntimeException("Not authorized");
                if (teamPost.computePostState() == PostState.ACTIVE)
                    throw new RuntimeException("Cannot delete active post");
                postRepository.deleteById(postId);
                return;
            }
        }
        throw new RuntimeException("Post not found");
    }
}
