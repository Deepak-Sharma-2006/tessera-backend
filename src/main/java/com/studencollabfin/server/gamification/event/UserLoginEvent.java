package com.studencollabfin.server.gamification.event;

import java.time.LocalDateTime;

public record UserLoginEvent(String userId, LocalDateTime loginTime) {
}
