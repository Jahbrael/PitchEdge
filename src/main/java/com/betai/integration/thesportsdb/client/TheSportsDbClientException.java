package com.betai.integration.thesportsdb.client;

public class TheSportsDbClientException extends RuntimeException {

    private final int statusCode;

    public TheSportsDbClientException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
