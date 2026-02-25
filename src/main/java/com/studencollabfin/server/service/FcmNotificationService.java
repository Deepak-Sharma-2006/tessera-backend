package com.studencollabfin.server.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.studencollabfin.server.model.User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
public class FcmNotificationService {

    public static final String TYPE_INBOX = "INBOX";
    public static final String TYPE_POLL = "POLL";
    public static final String TYPE_DM = "DM";
    public static final String TYPE_POD = "POD";

    public static final String CHANNEL_CHATS = "chats";
    public static final String CHANNEL_UPDATES = "updates";
    public static final String CHANNEL_POLLS = "polls";
    public static final String CHANNEL_IMPORTANT = "channel_important";

    /**
     * Send notification to a specific user token with preference checks.
     * Enforces user notification preferences for INBOX and DM types.
     * Topics (POLL, POD) are not filtered here - they're handled client-side.
     */
    public String sendToToken(
            String token,
            String title,
            String body,
            Map<String, String> data,
            String androidChannelId,
            String androidTag,
            User user) {
        if (token == null || token.isBlank()) {
            return null;
        }

        // ✅ PREFERENCE CHECK: Enforce user notification settings
        if (user != null && data != null && data.containsKey("type")) {
            String type = data.get("type");
            User.NotificationPreferences prefs = user.getNotificationPreferences();

            if (TYPE_INBOX.equals(type) && !prefs.isAllowInbox()) {
                System.out.println("⏭️ [FCM] INBOX notification blocked by user preference");
                return null;
            }
            if (TYPE_DM.equals(type) && !prefs.isAllowDMs()) {
                System.out.println("⏭️ [FCM] DM notification blocked by user preference");
                return null;
            }
        }

        Map<String, String> safeData = (data == null) ? Collections.emptyMap() : data;

        AndroidNotification.Builder androidNotif = AndroidNotification.builder();
        if (androidTag != null && !androidTag.isBlank()) {
            androidNotif.setTag(androidTag);
        }
        androidNotif.setChannelId(CHANNEL_IMPORTANT);

        // 🔔 BADGE COUNT: Set notification count for app icon badge
        // TODO: Calculate actual unread count (DMs + Inbox) from UserService
        // For now, using 1 to indicate new notification
        androidNotif.setNotificationCount(1);

        // 🎯 CLICK ACTION: Essential for Flutter notification handling
        androidNotif.setClickAction("FLUTTER_NOTIFICATION_CLICK");

        Message msg = Message.builder()
                .setToken(token)
                .putAllData(safeData)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(androidNotif.build())
                        .build())
                .build();

        try {
            return FirebaseMessaging.getInstance().send(msg);
        } catch (Exception e) {
            System.err.println("❌ [FCM] sendToToken failed: " + e.getMessage());
            return null;
        }
    }

    public String sendToTopic(
            String topic,
            String title,
            String body,
            Map<String, String> data,
            String androidChannelId,
            String androidTag) {
        if (topic == null || topic.isBlank()) {
            return null;
        }

        Map<String, String> safeData = (data == null) ? Collections.emptyMap() : data;

        AndroidNotification.Builder androidNotif = AndroidNotification.builder();
        if (androidTag != null && !androidTag.isBlank()) {
            androidNotif.setTag(androidTag);
        }
        androidNotif.setChannelId(CHANNEL_IMPORTANT);

        // 🔔 BADGE COUNT: Set notification count for app icon badge
        androidNotif.setNotificationCount(1);

        // 🎯 CLICK ACTION: Essential for Flutter notification handling
        androidNotif.setClickAction("FLUTTER_NOTIFICATION_CLICK");

        Message msg = Message.builder()
                .setTopic(topic)
                .putAllData(safeData)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(androidNotif.build())
                        .build())
                .build();

        try {
            return FirebaseMessaging.getInstance().send(msg);
        } catch (Exception e) {
            System.err.println("❌ [FCM] sendToTopic failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Legacy sendToToken method without user parameter for backward compatibility.
     * New code should use the version with User parameter for preference
     * enforcement.
     */
    public String sendToToken(
            String token,
            String title,
            String body,
            Map<String, String> data,
            String androidChannelId,
            String androidTag) {
        return sendToToken(token, title, body, data, androidChannelId, androidTag, null);
    }

    /**
     * Topic-safe campus segment for topics like campus_polls_{campusId}.
     * FCM topics allow: [a-zA-Z0-9-_.~%], max 900 chars.
     */
    public static String toTopicSegment(String raw) {
        if (raw == null) {
            return "";
        }
        String lowered = raw.trim().toLowerCase();
        // Replace any character outside the allowed set with underscore.
        return lowered.replaceAll("[^a-z0-9\\-_.~%]", "_");
    }
}
