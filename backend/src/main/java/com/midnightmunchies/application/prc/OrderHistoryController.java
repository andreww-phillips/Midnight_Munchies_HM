package com.midnightmunchies.application.prc;

import com.midnightmunchies.application.ord.OrderNotFoundException;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.reg.UserAccount;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.function.Supplier;

/** Session-scoped history reads; request parameters cannot supply a viewer identity or role. */
@RestController
@RequestMapping("/api/prc/history")
@CrossOrigin(allowCredentials = "true", origins = "http://localhost:5173")
public class OrderHistoryController {

    private final OrderHistoryService history;

    public OrderHistoryController(OrderHistoryService history) {
        this.history = history;
    }

    @GetMapping
    public ResponseEntity<?> history(HttpSession session) {
        return execute(() -> history.getHistory(account(session)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> order(@PathVariable String orderId, HttpSession session) {
        return execute(() -> {
            UserAccount viewer = account(session);
            UUID id;
            try {
                id = UUID.fromString(orderId);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Order ID must be a valid UUID");
            }
            return history.findOrder(id, viewer)
                    .orElseThrow(() -> new OrderNotFoundException("Order does not exist"));
        });
    }

    private UserAccount account(HttpSession session) {
        UserAccount account = (UserAccount) session.getAttribute("authenticatedUser");
        if (account == null) {
            throw new AuthenticationRequiredException("Authentication is required");
        }
        return account;
    }

    private ResponseEntity<?> execute(Supplier<?> operation) {
        try {
            return ResponseEntity.ok(operation.get());
        } catch (AuthenticationRequiredException exception) {
            return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
        } catch (OrderNotFoundException exception) {
            return error(HttpStatus.NOT_FOUND, exception.getMessage());
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
