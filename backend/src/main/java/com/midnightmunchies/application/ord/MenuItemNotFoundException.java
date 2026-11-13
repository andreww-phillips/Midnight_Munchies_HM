package com.midnightmunchies.application.ord;

public class MenuItemNotFoundException extends IllegalArgumentException {
    public MenuItemNotFoundException(String message) {
        super(message);
    }
}
