package com.studencollabfin.server.service;

import com.studencollabfin.server.model.PodMessage;
import com.studencollabfin.server.repository.PodMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PodMessageService {
    private final PodMessageRepository podMessageRepository;

    /**
     * Fetches all messages for a given pod, sorted by timestamp (oldest first).
     */
    public List<PodMessage> getMessagesByPodId(String podId) {
        return podMessageRepository.findByPodIdOrderByTimestampAsc(podId);
    }

    /**
     * Saves a new pod message.
     */
    public PodMessage saveMessage(PodMessage message) {
        return podMessageRepository.save(message);
    }

    // ✅ CASCADE DELETE: Delete all messages for a Pod
    public void deleteMessagesByPodId(String podId) {
        try {
            podMessageRepository.deleteByPodId(podId);
            System.out.println("✅ Cascade Delete: Messages with podId=" + podId + " deleted");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to delete messages for podId " + podId + ": " + e.getMessage());
        }
    }
}
