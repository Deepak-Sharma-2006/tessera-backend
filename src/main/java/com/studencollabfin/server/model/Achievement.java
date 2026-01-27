package com.studencollabfin.server.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "achievements")
public class Achievement {
    @Id
    private String id;
    private String userId;
    private String title;
    private String description;
    private String badgeUrl;
    private AchievementType type;
    private int xpValue;
    private LocalDateTime unlockedAt;
    private List<String> requirements;
    private boolean isUnlocked;

    public enum AchievementType {
        PARTICIPATION,
        COLLABORATION,
        ACADEMIC,
        LEADERSHIP,
        MENTORSHIP,
        SPECIAL
    }
}
