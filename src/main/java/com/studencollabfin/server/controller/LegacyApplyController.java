package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.Application;
import com.studencollabfin.server.dto.ApplyRequest;
import com.studencollabfin.server.service.BuddyBeaconService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegacyApplyController {

    @Autowired
    private BuddyBeaconService beaconService;

    // Temporary placeholder for current user id â€” replace with auth integration
    private String getCurrentUserId() {
        return "anonymous-user-id";
    }

    @PostMapping("/api/apply/{beaconId}")
    public ResponseEntity<?> legacyApply(@PathVariable String beaconId,
            @RequestBody(required = false) ApplyRequest request) {
        Application application = new Application();
        if (request != null) {
            application.setMessage(request.getMessage());
        }
        Application saved = beaconService.applyToBeaconPost(beaconId, getCurrentUserId(), application);
        return ResponseEntity.ok(saved);
    }
}
