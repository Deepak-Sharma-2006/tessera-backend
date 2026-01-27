package com.studencollabfin.server.controller;

import com.studencollabfin.server.dto.CreateEventRequest;
import com.studencollabfin.server.model.Event;
import com.studencollabfin.server.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /**
     * GET /api/events -> Get all events
     * GET /api/events?category=Hackathon -> Get all events in the "Hackathon"
     * category
     */
    @GetMapping
    public ResponseEntity<List<Event>> getEvents(@RequestParam(required = false) String category) {
        List<Event> events;
        if (category != null && !category.isEmpty()) {
            events = eventService.getEventsByCategory(category);
        } else {
            events = eventService.getAllEvents();
        }
        return ResponseEntity.ok(events);
    }

    /**
     * GET /api/events/{id} -> Get a single event by its ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Event> getEventById(@PathVariable String id) {
        Event event = eventService.getEventById(id);
        return ResponseEntity.ok(event);
    }

    /**
     * POST /api/events -> Create a new event
     * The event data is sent in the request body as JSON matching the
     * CreateEventRequest.
     */
    @PostMapping
    public ResponseEntity<Event> createEvent(@RequestBody CreateEventRequest request) {
        // TODO: Add security here to ensure only users with a 'MODERATOR' role can
        // access this endpoint.
        Event createdEvent = eventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEvent);
    }

    /**
     * DELETE /api/events/{id} -> Delete an event
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable String id) {
        // TODO: Add security here to ensure only moderators can delete events.
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }
}
