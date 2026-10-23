package com.midnightmunchies.domain.ord;

/** A catalog name conflict, distinct from invalid input or an order state transition. */
public class DuplicateNameException extends IllegalStateException {
    public DuplicateNameException(String message) {
        super(message);
    }
}
