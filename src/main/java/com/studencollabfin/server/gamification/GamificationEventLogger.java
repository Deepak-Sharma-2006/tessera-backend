package com.studencollabfin.server.gamification;

import com.studencollabfin.server.gamification.event.ReplyCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GamificationEventLogger {

    @EventListener
    public void onReplyCreated(ReplyCreatedEvent event) {
        log.info("[GamificationEventBus] ReplyCreatedEvent received: {}", event);
    }
}
