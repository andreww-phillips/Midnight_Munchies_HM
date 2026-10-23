package com.midnightmunchies.technicalservices.ord.repository;

/** A catalog outage is distinct from invalid input or an empty menu. */
public class CatalogAccessException extends RuntimeException {
    private final int statusCode;

    public CatalogAccessException(Throwable cause) {
        this("Unable to access the catalog. Check the backend Supabase settings and table access policies.", 503, cause);
    }

    public CatalogAccessException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
}
