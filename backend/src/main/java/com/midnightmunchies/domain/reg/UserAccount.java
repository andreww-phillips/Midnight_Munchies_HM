package com.midnightmunchies.domain.reg;

public record UserAccount(String id, String username, String passwordHash, UserRole role) {
}
