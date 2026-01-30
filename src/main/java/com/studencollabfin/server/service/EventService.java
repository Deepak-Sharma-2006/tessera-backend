package com.studencollabfin.server.service;

import com.studencollabfin.server.dto.CreateEventRequest;
import com.studencollabfin.server.model.Event;
import com.studencollabfin.server.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

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

                // Set endDate to 2 hours after start (optional, adjust as needed)
                newEvent.setEndDate(startDate.plusHours(2));
            }
        } catch (Exception e) {
            System.err.println("Error parsing date/time: " + e.getMessage());
            // If parsing fails, leave dates as null - frontend will handle gracefully
        }

        // ✅ FIX: Map the requiredSkills field
        newEvent.setRequiredSkills(request.getRequiredSkills());

        // ✅ FIX: Map the maxTeamSize field
        newEvent.setMaxParticipants(request.getMaxTeamSize() != null ? request.getMaxTeamSize() : 4);

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
}
