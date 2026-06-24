package com.seedling.platform.dto;

public class MarketStatusResponse {
    private boolean open;
    private String session;
    private String message;

    public MarketStatusResponse() {}

    public MarketStatusResponse(boolean open, String session, String message) {
        this.open = open;
        this.session = session;
        this.message = message;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
