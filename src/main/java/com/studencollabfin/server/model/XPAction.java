package com.studencollabfin.server.model;

import lombok.Getter;

@Getter
public enum XPAction {
    JOIN_POD(30),
    CREATE_POST(15),
    GIVE_ENDORSEMENT(10),
    RECEIVE_ENDORSEMENT(20),
    CREATE_EVENT(150),
    MENTOR_BONUS(50),
    PROJECT_COMPLETE(200);

    private final int points;

    XPAction(int points) {
        this.points = points;
    }
}
