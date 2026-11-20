package com.midnightmunchies.application.dash;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SimulationExceptionHandler {
    @ExceptionHandler(SimulationAccess.UnavailableException.class)
    public ResponseEntity<ErrorResponse> unavailable(SimulationAccess.UnavailableException exception) {
        HttpStatus status = exception.recoveryRequired() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(new ErrorResponse(exception.getMessage()));
    }

    public record ErrorResponse(String message) { }
}
