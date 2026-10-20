package com.lipe.proxy.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Auth {
    private final Map<String, String> users = new ConcurrentHashMap<>();

    public Auth() {}

    public void registerUser(String username, String password) {
        users.put(username, password);
    }

    public boolean authenticate(String firstLine) {
        if (firstLine == null || firstLine.isBlank()) {
            return false;
        }

        String[] parts = firstLine.trim().split("\\s+");

        if (parts.length != 3 || !parts[0].equalsIgnoreCase("AUTH")) {
            return false;
        }

        String username = parts[1];
        String password = parts[2];

        String expectedPassword = users.get(username);
        return expectedPassword != null && expectedPassword.equals(password);
    }
}
