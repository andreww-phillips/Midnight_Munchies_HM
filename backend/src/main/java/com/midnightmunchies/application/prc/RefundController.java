package com.midnightmunchies.application.prc;

import com.midnightmunchies.application.ord.AuthorizationException;
import com.midnightmunchies.application.ord.OrderNotFoundException;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.reg.UserAccount;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prc/orders")
@CrossOrigin(allowCredentials = "true", origins = "http://localhost:5173")
public class RefundController {

    private static final Logger log = LoggerFactory.getLogger(RefundController.class);
    private final RefundService refunds;

    public RefundController(RefundService refunds) {
        this.refunds = refunds;
    }

    @PostMapping("/{orderId}/refund")
    public ResponseEntity<?> refund(@PathVariable String orderId, HttpSession session) {
        try {
            return ResponseEntity.ok(refunds.refund(orderId, (UserAccount) session.getAttribute("authenticatedUser")));
        } catch (AuthenticationRequiredException exception) {
            return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
        } catch (AuthorizationException exception) {
            return error(HttpStatus.FORBIDDEN, exception.getMessage());
        } catch (OrderNotFoundException exception) {
            return error(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (RefundConflictException exception) {
            return error(HttpStatus.CONFLICT, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Refund commit failed for order {}", orderId, exception);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Refund could not be completed");
        }
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }

    public record ErrorResponse(String message) {
    }
}
