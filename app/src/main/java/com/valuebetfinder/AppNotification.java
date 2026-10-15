package com.valuebetfinder;

public class AppNotification {
    private final String id;
    private final String title;
    private final String message;

    public AppNotification(String id, String title, String message) {
        this.id = id;
        this.title = title;
        this.message = message;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
}
