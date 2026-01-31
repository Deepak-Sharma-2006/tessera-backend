package com.studencollabfin.server.service;

import com.studencollabfin.server.dto.CreateEventRequest;
import com.studencollabfin.server.model.CollabPod;
import com.studencollabfin.server.model.Event;
import com.studencollabfin.server.model.TeamFindingPost;
import com.studencollabfin.server.repository.CollabPodRepository;
import com.studencollabfin.server.repository.EventRepository;
import com.studencollabfin.server.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final PostRepository postRepository;
    private final CollabPodRepository collabPodRepository;

    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    public List<Event> getEventsByCategory(String category) {
        // This requires a new method in your EventRepository
        return eventRepository.findByCategory(category);
    }

    @SuppressWarnings("null")
    public Event getEventById(String id) {
        // Find the event by ID, or throw an exception if not found
        return eventRepository.findById((String) id)
                .orElseThrow(() -> new RuntimeException("Event not found with id: " + id));
    }

    public Event createEvent(CreateEventRequest request) {
        // Create a new Event object from the request DTO
        Event newEvent = new Event();
        newEvent.setTitle(request.getTitle());
        newEvent.setDescription(request.getDescription());
        newEvent.setCategory(request.getCategory());
        newEvent.setOrganizer(request.getOrganizer());

        // ✅ FIX: Properly parse date and time strings and convert to LocalDateTime
        try {
            // Parse date (format: YYYY-MM-DD from HTML date input)
            // Parse time (format: HH:mm from HTML time input)
            String dateStr = request.getDate(); // e.g., "2025-09-19"
            String timeStr = request.getTime(); // e.g., "21:30"

            if (dateStr != null && !dateStr.isEmpty() && timeStr != null && !timeStr.isEmpty()) {
                // Combine date and time into ISO format string
                String dateTimeStr = dateStr + "T" + timeStr + ":00";
                LocalDateTime startDate = LocalDateTime.parse(dateTimeStr);
                newEvent.setStartDate(startDate);
            }
        } catch (Exception e) {
            System.err.println("Error parsing date/time: " + e.getMessage());
            // If parsing fails, leave dates as null - frontend will handle gracefully
        }

        // ✅ FIX: Map the requiredSkills field
        newEvent.setRequiredSkills(request.getRequiredSkills());

        // ✅ FIX: Map the maxTeamSize field
        newEvent.setMaxParticipants(request.getMaxTeamSize() != null ? request.getMaxTeamSize() : 4);

        // ✅ FIX: Map the registrationLink field
        newEvent.setRegistrationLink(request.getRegistrationLink());

        // ✅ NEW: Map the linkEndDate field (registration deadline)
        if (request.getLinkEndDate() != null && !request.getLinkEndDate().isEmpty()) {
            try {
                LocalDateTime linkDeadline = LocalDateTime.parse(request.getLinkEndDate());
                newEvent.setLinkEndDate(linkDeadline);
            } catch (Exception e) {
                System.err.println("Error parsing linkEndDate: " + e.getMessage());
                // If parsing fails, leave linkEndDate as null
            }
        }

        // ✅ NEW: Map maxTeams field (for team events only)
        newEvent.setMaxTeams(request.getMaxTeams());

        // ✅ NEW: Initialize counter fields
        newEvent.setCurrentParticipants(0L);
        newEvent.setCurrentTeams(0L);

        // Set default values for other fields
        newEvent.setStatus(Event.EventStatus.UPCOMING);
        newEvent.setType(Event.EventType.OTHER);

        // Save the new event to the database
        return eventRepository.save(newEvent);
    }

    @SuppressWarnings("null")
    public void deleteEvent(String id) {
        // First check if the event exists to provide a better error message
        if (!eventRepository.existsById((String) id)) {
            throw new RuntimeException("Event not found with id: " + id);
        }
        eventRepository.deleteById((String) id);
    }

    /**
     * ✅ UPDATED: Track unique user registration for solo events with external
     * links.
     * 
     * SOLO EVENT LOGIC:
     * 1. Check if user already registered (prevent duplicates)
     * 2. Check participant limit (if maxParticipants is set)
     * 3. Add user to registeredUserIds set
     * 4. Increment currentParticipants
     * 5. Set currentTeams = null (NOT stored for solo events)
     * 6. Save Event
     * 
     * @param eventId The event ID
     * @param userId  The user ID making the registration click
     * @return The updated Event with incremented currentParticipants
     * @throws RuntimeException if user already registered or event is full
     */
    public Event trackUserRegistration(String eventId, String userId) {
        Event event = getEventById(eventId);

        // ✅ Check if user already registered (prevent double counting)
        if (event.getRegisteredUserIds() != null && event.getRegisteredUserIds().contains(userId)) {
            throw new RuntimeException("User already registered for this event");
        }

        // ✅ Check participant limit
        if (event.getMaxParticipants() > 0 &&
                event.getCurrentParticipants() != null &&
                event.getCurrentParticipants() >= event.getMaxParticipants()) {
            throw new RuntimeException("Event is full - maximum participants reached");
        }

        // ✅ Add user to set and increment counter
        if (event.getRegisteredUserIds() == null) {
            event.setRegisteredUserIds(new HashSet<>());
        }
        event.getRegisteredUserIds().add(userId);

        // ✅ Update currentParticipants (use the new field)
        long newCount = (event.getCurrentParticipants() != null ? event.getCurrentParticipants() : 0) + 1;
        event.setCurrentParticipants(newCount);

        // ✅ CRITICAL: For solo events, do NOT store currentTeams
        event.setCurrentTeams(null);

        // Save and return
        return eventRepository.save(event);
    }

    /**
     * ✅ CORRECTED: Refresh event statistics avoiding double-counting.
     * 
     * PREVENTION OF DOUBLE-COUNTING:
     * When a TeamFindingPost is converted to a CollabPod (after 24 hours),
     * the Post gets a linkedPodId. This method filters out Relisted Posts
     * to avoid counting the same team twice.
     * 
     * LOGIC:
     * 1. Fetch all TeamFindingPost documents for this event
     * 2. FILTER: Keep only "Standalone" posts (linkedPodId == null)
     * - Ignore "Relisted" posts (linkedPodId != null) because the team
     * is already counted via the CollabPod
     * 3. Count standalone posts
     * 4. Sum participants from standalone posts
     * 5. Count CollabPods with type=TEAM for this event
     * 6. Sum participants from all event pods
     * 7. Aggregate:
     * - currentTeams = standalonePosts.size() + podCount
     * - currentParticipants = postParticipants + podParticipants
     * 8. Check team limit and save
     * 
     * @param eventId The event ID to refresh stats for
     */
    public void refreshEventStats(String eventId) {
        try {
            Event event = getEventById(eventId);

            // ✅ Step 1: Fetch all TeamFindingPost documents for this event
            List<TeamFindingPost> allPosts = postRepository.findByEventId(eventId);

            if (allPosts == null || allPosts.isEmpty()) {
                // No posts yet - reset counts
                event.setCurrentTeams(0L);
                event.setCurrentParticipants(0L);
            } else {
                // ✅ Step 2: FILTER - Only count "Standalone" posts
                // Ignore "Relisted" posts (linkedPodId != null) to avoid double-counting
                List<TeamFindingPost> standalonePosts = allPosts.stream()
                        .filter(p -> p.getLinkedPodId() == null)
                        .collect(Collectors.toList());

                long postsCount = standalonePosts.size();

                // ✅ Step 3: Count participants in standalone posts
                long participantsInPosts = standalonePosts.stream()
                        .mapToLong(post -> {
                            List<String> members = post.getCurrentTeamMembers();
                            return members != null ? members.size() : 0;
                        })
                        .sum();

                // ✅ Step 4: Count Formed Teams (CollabPods)
                // The Relisted Post members are already inside the Pod, so we just count Pod
                // members
                List<CollabPod> teamPods = collabPodRepository.findByEventIdAndType(eventId, CollabPod.PodType.TEAM);
                long podsCount = teamPods.size();

                // ✅ Step 5: Count participants in pods
                long participantsInPods = teamPods.stream()
                        .mapToLong(pod -> {
                            List<String> members = pod.getMemberIds();
                            return members != null ? members.size() : 0;
                        })
                        .sum();

                // ✅ Step 6: Aggregate (No double-counting!)
                long totalTeams = postsCount + podsCount;
                long totalParticipants = participantsInPosts + participantsInPods;

                // Update only for TEAM events
                if (event.getType() != null && event.getType().toString().equals("TEAM")) {
                    event.setCurrentTeams(totalTeams);
                }
                event.setCurrentParticipants(totalParticipants);

                // ✅ Check team limit (informational, not blocking)
                if (event.getMaxTeams() != null && totalTeams > event.getMaxTeams()) {
                    System.out.println(
                            "⚠️ Event " + eventId + " exceeded max teams: " + totalTeams + " > " + event.getMaxTeams());
                }

                // Save the updated event
                eventRepository.save(event);
                System.out.println("✅ Event stats refreshed (no double-counting): " + eventId
                        + " - Standalone Posts: " + postsCount + ", Pods: " + podsCount + ", Total Teams: " + totalTeams
                        + ", Participants: " + totalParticipants);
            }
        } catch (Exception e) {
            System.err.println("❌ Error refreshing event stats for " + eventId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✅ NEW: Double Booking Prevention - Check if user is already in a team for
     * this event.
     * 
     * REQUIREMENT: Prevent a user from joining multiple teams for the same event.
     * 
     * LOGIC:
     * 1. Query all TeamFindingPosts for the event where user is a confirmed member
     * 2. Query all CollabPods (type=TEAM) for the event where user is a member
     * 3. If user found in either, throw exception
     * 
     * RETURN: void (throws exception if user already has a team)
     * 
     * Called before allowing a user to apply/join a team for an event.
     * 
     * @param eventId The event ID
     * @param userId  The user ID to check
     * @throws RuntimeException if user is already in a team for this event
     */
    public void checkDoubleBooking(String eventId, String userId) {
        // ✅ Check if user is a member of any TeamFindingPost for this event
        List<TeamFindingPost> posts = postRepository.findByEventId(eventId);
        boolean userInPost = posts.stream()
                .anyMatch(post -> {
                    // Check if user is current member
                    List<String> members = post.getCurrentTeamMembers();
                    return members != null && members.contains(userId);
                });

        if (userInPost) {
            throw new RuntimeException("User is already a member of a team in this event. Cannot join another team.");
        }

        // ✅ Check if user is a member of any CollabPod (type=TEAM) for this event
        List<CollabPod> pods = collabPodRepository.findByEventIdAndType(eventId, CollabPod.PodType.TEAM);
        boolean userInPod = pods.stream()
                .anyMatch(pod -> {
                    List<String> members = pod.getMemberIds();
                    return members != null && members.contains(userId);
                });

        if (userInPod) {
            throw new RuntimeException(
                    "User is already a member of a formed team in this event. Cannot join another team.");
        }
    }
}
