package com.midnightmunchies.application;

import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final Map<String, UserAccount> users = new ConcurrentHashMap<>();

    public AuthService() {
        seed("customer@example.com", "password", UserRole.CUSTOMER);
        seed("restaurant@example.com", "password", UserRole.RESTAURANT_STAFF);
        seed("courier@example.com", "password", UserRole.COURIER);
        seed("operator@example.com", "password", UserRole.PLATFORM_OPERATOR);
    }

    public UserAccount authenticate(String username, String password) {
        UserAccount account = users.get(normalize(username));
        if (account == null || !matches(password, account.passwordHash())) {
            return null;
        }
        return account;
    }

    public UserAccount register(String username, String password, UserRole role) {
        String normalizedUsername = normalize(username);
        if (normalizedUsername.isBlank() || password == null || password.isBlank() || role == null) {
            throw new IllegalArgumentException("Username, password, and role are required");
        }
        UserAccount account = new UserAccount(
                UUID.randomUUID().toString(),
                normalizedUsername,
                hash(password),
                role
        );
        if (users.putIfAbsent(normalizedUsername, account) != null) {
            throw new IllegalStateException("An account with that username already exists");
        }
        return account;
    }

    private void seed(String username, String password, UserRole role) {
        users.put(username, new UserAccount(UUID.randomUUID().toString(), username, hash(password), role));
    }

    private boolean matches(String password, String passwordHash) {
        return password != null && MessageDigest.isEqual(
                hash(password).getBytes(StandardCharsets.UTF_8),
                passwordHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
