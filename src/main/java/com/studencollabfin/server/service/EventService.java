package com.studencollabfin.server.service;

import com.studencollabfin.server.dto.CreateEventRequest;
import com.studencollabfin.server.model.Event;
import com.studencollabfin.server.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class EventService {

    private final EventRepository eventRepository;

    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    public List<Event> getEventsByCategory(String category) {
        // This requires a new method in your EventRepository
        return eventRepository.findByCategory(category);
    }

    public Event getEventById(String id) {
        // Find the event by ID, or throw an exception if not found
        return eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with id: " + id));
    }

    public Event createEvent(CreateEventRequest request) {
        // Create a new Event object from the request DTO
        Event newEvent = new Event();
        newEvent.setTitle(request.getTitle());
        newEvent.setDescription(request.getDescription());
        newEvent.setCategory(request.getCategory());
        newEvent.setOrganizer(request.getOrganizer());

        // Note: You will need to handle the String dates and other fields from your DTO
        // here
        // For now, we are just setting the basic fields.

        // Save the new event to the database
        return eventRepository.save(newEvent);
    }

    public void deleteEvent(String id) {
        // First check if the event exists to provide a better error message
        if (!eventRepository.existsById(id)) {
            throw new RuntimeException("Event not found with id: " + id);
        }
        eventRepository.deleteById(id);
    }
}
