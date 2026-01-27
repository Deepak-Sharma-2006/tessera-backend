package com.studencollabfin.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @SuppressWarnings("null")
    public void notifyPodMembers(String podId, String message) {
        if (podId != null) {
            messagingTemplate.convertAndSend("/topic/pod/" + podId, (Object) message);
        }
    }

    public void notifyUser(String userId, Object notification) {
        if (userId != null && notification != null) {
            messagingTemplate.convertAndSend("/queue/user/" + userId, notification);
        }
    }

    public void notifyPodUpdate(String podId, Object update) {
        if (podId != null && update != null) {
            messagingTemplate.convertAndSend("/topic/pod/" + podId + "/updates", update);
        }
    }

    public void notifyBuddyBeacon(String userId, Object beaconData) {
        if (beaconData != null) {
            messagingTemplate.convertAndSend("/topic/campus/beacons", beaconData);
        }
    }
}
