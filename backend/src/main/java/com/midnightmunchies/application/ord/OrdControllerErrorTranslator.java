package com.midnightmunchies.application.ord;

import com.midnightmunchies.application.ord.OrdController.ErrorResponse;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.ord.DuplicateNameException;
import com.midnightmunchies.domain.ord.IllegalOrderTransitionException;
import com.midnightmunchies.technicalservices.CatalogAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class OrdControllerErrorTranslator {

    ResponseEntity<ErrorResponse> translate(RuntimeException exception) {
        if (exception instanceof CatalogAccessException catalogException) {
            return error(catalogException.getStatusCode(), exception.getMessage());
        }
        if (exception instanceof AuthenticationRequiredException) {
            return error(HttpStatus.UNAUTHORIZED.value(), exception.getMessage());
        }
        if (exception instanceof AuthorizationException) {
            return error(HttpStatus.FORBIDDEN.value(), exception.getMessage());
        }
        if (exception instanceof MenuItemNotFoundException || exception instanceof OrderNotFoundException) {
            return error(HttpStatus.NOT_FOUND.value(), exception.getMessage());
        }
        if (exception instanceof DuplicateNameException || exception instanceof EmptyPreparationQueueException
                || exception instanceof IllegalOrderTransitionException) {
            return error(HttpStatus.CONFLICT.value(), exception.getMessage());
        }
        if (exception instanceof IllegalArgumentException) {
            return error(HttpStatus.BAD_REQUEST.value(), exception.getMessage());
        }
        // Only the thrown exception is classified; recognized causes do not make wrappers handled errors.
        throw exception;
    }

    ResponseEntity<ErrorResponse> invalidRequestBody() {
        return error(HttpStatus.BAD_REQUEST.value(), "Request body is missing or malformed");
    }

    ResponseEntity<ErrorResponse> invalidIdentifier() {
        return error(HttpStatus.BAD_REQUEST.value(), "Restaurant, menu item and order IDs must be valid UUIDs");
    }

    private ResponseEntity<ErrorResponse> error(int status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }
}
