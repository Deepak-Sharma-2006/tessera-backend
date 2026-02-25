package com.studencollabfin.server.controller;

import com.studencollabfin.server.service.AchievementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final AchievementService achievementService;

    @PostMapping("/{projectId}/join-team")
    public ResponseEntity<?> joinTeam(
            @PathVariable String projectId,
            @RequestBody(required = false) Map<String, String> payload) {
        String userId = payload != null ? payload.get("userId") : null;
        if (userId == null || userId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }

        achievementService.checkHardMode(userId, "team-activity", Map.of("projectId", projectId));
        return ResponseEntity.ok(Map.of(
                "message", "Team join event recorded",
                "projectId", projectId,
                "userId", userId));
    }
}
