package com.midnightmunchies.application.prc;

import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.LedgerRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Authenticated read access to post-delivery summaries; no billing commands. */
@RestController
@RequestMapping("/api/prc")
@CrossOrigin(allowCredentials = "true", origins = "http://localhost:5173")
public class BillingController {

    private final BillingService billing;
    private final LedgerRepository ledger;

    public BillingController(BillingService billing, LedgerRepository ledger) {
        this.billing = billing;
        this.ledger = ledger;
    }

    @GetMapping("/orders/{orderId}/summary")
    public ResponseEntity<?> summary(@PathVariable String orderId, HttpSession session) {
        UserAccount user = (UserAccount) session.getAttribute("authenticatedUser");
        if (user == null) {
            return error(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        if (user.role() != UserRole.CUSTOMER && user.role() != UserRole.PLATFORM_OPERATOR) {
            return error(HttpStatus.FORBIDDEN, "This role cannot view billing summaries");
        }
        UUID id;
        try {
            id = UUID.fromString(orderId);
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, "Order ID must be a valid UUID");
        }
        LedgerEntry order = ledger.findByOrderId(id).orElse(null);
        if (order == null || (user.role() == UserRole.CUSTOMER
                && !order.customerId().toString().equals(user.id()))) {
            // Missing and other customers' orders deliberately have the same response.
            return error(HttpStatus.NOT_FOUND, "Order does not exist");
        }
        return billing.findSummary(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> error(HttpStatus.CONFLICT, "Order summary is available only after delivery"));
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }

    public record ErrorResponse(String message) {
    }
}
