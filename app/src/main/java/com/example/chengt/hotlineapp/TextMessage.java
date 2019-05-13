package com.example.chengt.hotlineapp;

public class TextMessage {
    private String text;
    private boolean belongsToCurrentUser;

    TextMessage(String text, boolean belongsToCurrentUser) {
        this.text = text;
        this.belongsToCurrentUser = belongsToCurrentUser;
    }

    public String getText() {
        return text;
    }

    public boolean isBelongsToCurrentUser() {
        return belongsToCurrentUser;
    }
}
