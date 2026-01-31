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
    @Autowired
    private InboxRepository inboxRepository;
    @Autowired
    private CollabPodRepository collabPodRepository;
    @Autowired
    private EventService eventService;

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
     * Aggregates all BuddyBeacon and TeamFindingPost posts for the current user's
     * campus feed.
     * ✅ Campus Isolation: Only returns posts from the current user's college.
     * TeamFindingPosts are included if ACTIVE or CLOSED (<24h old).
     */
    public List<Map<String, Object>> getAllBeaconPosts(String currentUserId, String userCollege) {
        List<Map<String, Object>> feed = new ArrayList<>();

        // Validate user college
        if (userCollege == null || userCollege.trim().isEmpty()) {
            System.out.println("⚠️ User college is null/empty, returning empty feed for Buddy Beacon");
            return feed;
        }

        // Add BuddyBeacon posts (legacy) - filter by college
        List<BuddyBeacon> beacons = beaconRepository.findAll().stream()
                .filter(b -> {
                    // For legacy BuddyBeacons, fetch author college if not stored
                    if (b.getAuthorId() != null) {
                        Optional<User> authorOpt = userRepository.findById(b.getAuthorId());
                        if (authorOpt.isPresent()) {
                            String authorCollege = authorOpt.get().getCollegeName();
                            return userCollege.equals(authorCollege);
                        }
                    }
                    return false;
                })
                .toList();

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

        // Add TeamFindingPosts (campus-filtered visibility)
        List<TeamFindingPost> teamPosts = postRepository.findAll().stream()
                .filter(p -> p instanceof TeamFindingPost)
                .map(p -> (TeamFindingPost) p)
                .filter(p -> {
                    // ✅ Campus Isolation: Only include posts from same college
                    if (p.getCollege() == null || !p.getCollege().equals(userCollege)) {
                        return false;
                    }
                    PostState state = p.computePostState();
                    return state == PostState.ACTIVE || state == PostState.CLOSED;
                })
                .toList();

        System.out.println("✅ Filtered TeamFindingPosts for college: " + userCollege + ", count: " + teamPosts.size());

        for (TeamFindingPost post : teamPosts) {
            Map<String, Object> postMap = new HashMap<>();
            postMap.put("type", "TeamFindingPost");

            // ✅ FIX #3: Populate author information for display
            if (post.getAuthorId() != null) {
                Optional<User> authorOpt = userRepository.findById(post.getAuthorId());
                if (authorOpt.isPresent()) {
                    User author = authorOpt.get();
                    post.setAuthorName(author.getFullName());
                }
            }
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
     * details. Excludes posts where the user is the author.
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
                    // ✅ FIX #4: Exclude posts where user is the author
                    if (!beacon.getAuthorId().equals(applicantId)) {
                        map.put("postType", "BuddyBeacon");
                        map.put("post", beacon);
                    }
                });
                // Try TeamFindingPost
                @SuppressWarnings("null")
                var postOpt = postRepository.findById((String) app.getBeaconId());
                postOpt.ifPresent(post -> {
                    if (post instanceof TeamFindingPost tfp) {
                        // ✅ FIX #4: Exclude posts where user is the author
                        if (!tfp.getAuthorId().equals(applicantId)) {
                            map.put("postType", "TeamFindingPost");
                            map.put("post", tfp);
                        }
                    }
                });
            }
            // Only add to result if post data was populated (not author's own post)
            if (map.containsKey("post")) {
                result.add(map);
            }
        }
        return result;
    }

    /**
     * ✅ NEW: Check if a user is available to join a team for a specific event.
     * Availability check:
     * 1. Is user in a Pod for this event? -> NOT available
     * 2. Is user CONFIRMED in any TeamFindingPost for this event? -> NOT available
     * 3. Otherwise -> AVAILABLE
     */
    private boolean isUserAvailable(String eventId, String userId) {
        // Check 1: User in a Pod for this event
        boolean inPod = collabPodRepository.existsByEventIdAndMemberIdsContains(eventId, userId);
        if (inPod) {
            return false;
        }

        // Check 2: User CONFIRMED in any TeamFindingPost for this event
        List<TeamFindingPost> eventPosts = postRepository.findByEventId(eventId);
        boolean inConfirmedPost = eventPosts.stream()
                .anyMatch(p -> {
                    List<String> members = p.getCurrentTeamMembers();
                    return members != null && members.contains(userId);
                });

        return !inConfirmedPost;
    }

    /**
     * Returns posts created by the authenticated user, with applicants and their
     * profiles.
     * ✅ NEW: Each applicant includes "isAvailable" field for UX optimization.
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
                applicant.put("_id", app.getId());
                applicant.put("applicationId", app.getId());
                applicant.put("status", app.getStatus() != null ? app.getStatus().toString() : "PENDING");
                if (app.getApplicantId() != null) {
                    @SuppressWarnings("null")
                    var userOpt = userRepository.findById((String) app.getApplicantId());
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        applicant.put("applicantId", app.getApplicantId());
                        applicant.put("profile", user);
                        // ✅ NEW: Add isAvailable field (BuddyBeacon doesn't have eventId, so always
                        // available)
                        applicant.put("isAvailable", true);
                    }
                }
                applicants.add(applicant);
            }
            // Add applicants to beacon for display
            beacon.setApplicantObjects(applicants);
            postMap.put("post", beacon);
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
                applicant.put("_id", app.getId());
                applicant.put("applicationId", app.getId());
                applicant.put("status", app.getStatus() != null ? app.getStatus().toString() : "PENDING");
                if (app.getApplicantId() != null) {
                    @SuppressWarnings("null")
                    var userOpt = userRepository.findById((String) app.getApplicantId());
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        applicant.put("applicantId", app.getApplicantId());
                        applicant.put("profile", user);
                        // ✅ NEW: Check if user is available for this event
                        boolean available = isUserAvailable(post.getEventId(), app.getApplicantId());
                        applicant.put("isAvailable", available);
                    }
                }
                applicants.add(applicant);
            }
            post.setApplicants(applicants);
            postMap.put("post", post);
            postMap.put("applicants", applicants);
            result.add(postMap);
        }
        return result;
    }

    /**
     * Application logic: Only allow if post is ACTIVE (<20h), applicant is not the
     * creator,
     * and applicant is not already in a team/pod for this event (double booking
     * prevention).
     */
    @SuppressWarnings("null")
    public Application applyToBeaconPost(String beaconId, String applicantId, Application application) {
        System.out.println("Received beaconId: " + beaconId); // Debugging beaconId

        // Try BuddyBeacon first
        Optional<BuddyBeacon> beaconOpt = beaconRepository.findById((String) beaconId);
        if (beaconOpt.isPresent()) {
            BuddyBeacon beacon = beaconOpt.get();
            // ✅ FIX #3: Prevent creator from applying to their own post
            if (beacon.getAuthorId().equals(applicantId)) {
                throw new RuntimeException("Cannot apply to your own team post");
            }
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
            // ✅ FIX #3: Prevent creator from applying to their own post
            if (teamPost.getAuthorId().equals(applicantId)) {
                throw new RuntimeException("Cannot apply to your own team post");
            }
            if (teamPost.computePostState() != PostState.ACTIVE) {
                throw new RuntimeException("Applications are closed for this post");
            }

            // ✅ DOUBLE BOOKING PREVENTION (Part 2: Filter)
            // Check 1: Self-join prevention - if post is linkedPodId, verify not already in
            // team
            if (teamPost.getLinkedPodId() != null) {
                if (teamPost.getCurrentTeamMembers() != null
                        && teamPost.getCurrentTeamMembers().contains(applicantId)) {
                    throw new RuntimeException("You are already a member of this team.");
                }
            }

            // Check 2: Duplicate application prevention
            if (teamPost.getApplicants() != null &&
                    teamPost.getApplicants().stream().anyMatch(app -> app.containsKey("applicantId") &&
                            app.get("applicantId").equals(applicantId))) {
                throw new RuntimeException("You have already applied to this team.");
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

                    // ✅ FEATURE: Create inbox notification for the applicant
                    Inbox inboxMessage = new Inbox();
                    inboxMessage.setUserId(app.getApplicantId());
                    inboxMessage.setType(Inbox.NotificationType.APPLICATION_FEEDBACK);
                    inboxMessage.setTitle("Application Accepted!");
                    inboxMessage.setMessage("Congratulations! You've been accepted to '" + beacon.getTitle() + "'!");
                    inboxMessage.setApplicationId(applicationId);
                    inboxMessage.setPostId(postId);
                    inboxMessage.setPostTitle(beacon.getTitle());
                    inboxMessage.setSenderId(userId);
                    inboxMessage.setApplicationStatus("ACCEPTED");
                    inboxRepository.save(inboxMessage);

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

                    // ✅ DOUBLE BOOKING PREVENTION (Part 1: The Gatekeeper)
                    String applicantId = app.getApplicantId();
                    String eventId = teamPost.getEventId();

                    // Check 1: User already in a POD for this event
                    boolean inPod = collabPodRepository.existsByEventIdAndMemberIdsContains(eventId, applicantId);
                    if (inPod) {
                        throw new IllegalStateException(
                                "User is already in a team (Pod) for this event. Cannot join another team.");
                    }

                    // Check 2: User CONFIRMED in any OTHER Post for this event
                    List<TeamFindingPost> otherPosts = postRepository.findByEventId(eventId);
                    boolean inOtherPost = otherPosts.stream()
                            .filter(p -> !p.getId().equals(postId)) // Exclude current post
                            .anyMatch(p -> {
                                List<String> members = p.getCurrentTeamMembers();
                                return members != null && members.contains(applicantId);
                            });

                    if (inOtherPost) {
                        throw new IllegalStateException(
                                "User has already been accepted by another leader for this event. Cannot join multiple teams.");
                    }

                    List<String> members = teamPost.getCurrentTeamMembers();
                    if (members == null)
                        members = new ArrayList<>();
                    if (members.size() >= teamPost.getMaxTeamSize())
                        throw new RuntimeException("Team is full");
                    app.setStatus(Application.Status.ACCEPTED);
                    members.add(app.getApplicantId());
                    teamPost.setCurrentTeamMembers(members);
                    postRepository.save(teamPost);

                    // ✅ FEATURE: Create inbox notification for the applicant
                    Inbox inboxMessage = new Inbox();
                    inboxMessage.setUserId(app.getApplicantId());
                    inboxMessage.setType(Inbox.NotificationType.APPLICATION_FEEDBACK);
                    inboxMessage.setTitle("Application Accepted!");
                    inboxMessage.setMessage("Congratulations! You've been accepted to '" + teamPost.getTitle() + "'!");
                    inboxMessage.setApplicationId(applicationId);
                    inboxMessage.setPostId(postId);
                    inboxMessage.setPostTitle(teamPost.getTitle());
                    inboxMessage.setSenderId(userId);
                    inboxMessage.setApplicationStatus("ACCEPTED");
                    inboxRepository.save(inboxMessage);

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

                    // ✅ FEATURE: Create inbox notification for the applicant
                    Inbox inboxMessage = new Inbox();
                    inboxMessage.setUserId(app.getApplicantId());
                    inboxMessage.setType(Inbox.NotificationType.APPLICATION_FEEDBACK);
                    inboxMessage.setTitle("Application Rejected");
                    inboxMessage.setMessage("Your application to '" + beacon.getTitle() + "' has been rejected.");
                    inboxMessage.setApplicationId(applicationId);
                    inboxMessage.setPostId(postId);
                    inboxMessage.setPostTitle(beacon.getTitle());
                    inboxMessage.setSenderId(userId);
                    inboxMessage.setApplicationStatus("REJECTED");
                    inboxMessage.setRejectionReason(reason != null ? reason.toString() : "");
                    inboxMessage.setRejectionNote(note);
                    inboxRepository.save(inboxMessage);

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

                    // ✅ FEATURE: Create inbox notification for the applicant
                    Inbox inboxMessage = new Inbox();
                    inboxMessage.setUserId(app.getApplicantId());
                    inboxMessage.setType(Inbox.NotificationType.APPLICATION_FEEDBACK);
                    inboxMessage.setTitle("Application Rejected");
                    inboxMessage.setMessage("Your application to '" + teamPost.getTitle() + "' has been rejected.");
                    inboxMessage.setApplicationId(applicationId);
                    inboxMessage.setPostId(postId);
                    inboxMessage.setPostTitle(teamPost.getTitle());
                    inboxMessage.setSenderId(userId);
                    inboxMessage.setApplicationStatus("REJECTED");
                    inboxMessage.setRejectionReason(reason != null ? reason.toString() : "");
                    inboxMessage.setRejectionNote(note);
                    inboxRepository.save(inboxMessage);

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
