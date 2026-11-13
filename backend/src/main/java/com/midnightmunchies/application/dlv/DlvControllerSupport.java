package com.midnightmunchies.application.dlv;

import com.midnightmunchies.application.ord.AuthorizationException;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.reg.UserAccount;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.function.Supplier;

abstract class DlvControllerSupport {
    private static final String SESSION_USER = "authenticatedUser";

    protected UserAccount account(HttpSession session) {
        UserAccount user = (UserAccount) session.getAttribute(SESSION_USER);
        if (user == null) {
            throw new AuthenticationRequiredException("Authentication is required");
        }
        return user;
    }

    protected ResponseEntity<?> execute(Supplier<?> operation) {
        try {
            return ResponseEntity.ok(operation.get());
        } catch (AuthenticationRequiredException exception) {
            return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
        } catch (AuthorizationException exception) {
            return error(HttpStatus.FORBIDDEN, exception.getMessage());
        } catch (DeliveryNotFoundException exception) {
            return error(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (DeliveryConflictException exception) {
            return error(HttpStatus.CONFLICT, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }

    public record ErrorResponse(String message) {
    }
}
