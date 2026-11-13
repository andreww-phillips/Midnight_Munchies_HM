package com.midnightmunchies.application.ord;

public class RestaurantNotFoundException extends IllegalArgumentException {
    public RestaurantNotFoundException(String message) {
        super(message);
    }
}
