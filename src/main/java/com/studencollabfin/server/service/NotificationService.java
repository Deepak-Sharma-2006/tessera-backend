package com.studencollabfin.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@SuppressWarnings("null")
public class NotificationService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void notifyPodMembers(String podId, String message) {
        messagingTemplate.convertAndSend("/topic/pod/" + podId, message);
    }

    public void notifyUser(String userId, Object notification) {
        messagingTemplate.convertAndSend("/queue/user/" + userId, notification);
    }

    public void notifyPodUpdate(String podId, Object update) {
        messagingTemplate.convertAndSend("/topic/pod/" + podId + "/updates", update);
    }

    public void notifyBuddyBeacon(String userId, Object beaconData) {
        messagingTemplate.convertAndSend("/topic/campus/beacons", beaconData);
    }
}
