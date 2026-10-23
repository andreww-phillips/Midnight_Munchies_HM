package com.midnightmunchies.application;

public class MenuItemNotFoundException extends IllegalArgumentException {
    public MenuItemNotFoundException(String message) {
        super(message);
    }
}
