package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.Order;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ord")
@CrossOrigin(allowCredentials = "true", origins = "http://localhost:5173")
public class OrdController {
    private final OrdService authorizedService;

    public OrdController(AuthorizedOrdService authorizedService) {
        this.authorizedService = authorizedService;
    }

    @GetMapping("/restaurants/{restaurantId}/menu")
    public ResponseEntity<List<MenuItem>> menu(@PathVariable UUID restaurantId, HttpSession session) {
        return execute(() -> authorizedService.browseMenu(restaurantId, session));
    }

    @PostMapping("/restaurants/{restaurantId}/orders")
    public ResponseEntity<Order> placeOrder(
            @PathVariable UUID restaurantId,
            @RequestBody PlaceOrderRequest request,
            HttpSession session
    ) {
        return execute(() -> authorizedService.placeOrder(restaurantId, request.lines(), session));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Order>> orders(HttpSession session) {
        return execute(() -> authorizedService.viewOrders(session));
    }

    private <T> ResponseEntity<T> execute(AuthorizedOperation<T> operation) {
        try {
            return ResponseEntity.ok(operation.run());
        } catch (AuthenticationRequiredException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (AuthorizationException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    @FunctionalInterface
    private interface AuthorizedOperation<T> {
        T run();
    }

    public record PlaceOrderRequest(List<OrdService.OrderLineRequest> lines) {
    }
}
