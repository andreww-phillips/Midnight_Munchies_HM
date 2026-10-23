package com.midnightmunchies.application;

public class EmptyPreparationQueueException extends RuntimeException {

    public EmptyPreparationQueueException(String message) {
        super(message);
    }
}
