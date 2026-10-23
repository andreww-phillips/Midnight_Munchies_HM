package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.IllegalOrderTransitionException;
import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.MenuItemStatus;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.technicalservices.ord.repository.CatalogAccessException;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.math.BigDecimal;
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

    @GetMapping("/restaurants")
    public ResponseEntity<?> restaurants(HttpSession session) {
        return execute(() -> authorizedService.browseRestaurants(session));
    }

    @GetMapping("/restaurants/{restaurantId}/menu")
    public ResponseEntity<?> menu(@PathVariable UUID restaurantId, HttpSession session) {
        return execute(() -> authorizedService.browseMenu(restaurantId, session));
    }

    @PostMapping("/restaurants/{restaurantId}/menu")
    public ResponseEntity<?> addMenuItem(
            @PathVariable UUID restaurantId,
            @RequestBody MenuItemRequest request,
            HttpSession session
    ) {
        return execute(() -> authorizedService.addMenuItem(restaurantId, request.name(), request.price(), session),
                HttpStatus.CREATED);
    }

    @PutMapping("/restaurants/{restaurantId}/menu/{menuItemId}")
    public ResponseEntity<?> editMenuItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID menuItemId,
            @RequestBody MenuItemRequest request,
            HttpSession session
    ) {
        return execute(() -> authorizedService.editMenuItem(restaurantId, menuItemId, request.name(), request.price(), session));
    }

    @DeleteMapping("/restaurants/{restaurantId}/menu/{menuItemId}")
    public ResponseEntity<?> removeMenuItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID menuItemId,
            HttpSession session
    ) {
        return execute(() -> {
            authorizedService.removeMenuItem(restaurantId, menuItemId, session);
            return null;
        }, HttpStatus.NO_CONTENT);
    }

    @PatchMapping("/restaurants/{restaurantId}/menu/{menuItemId}/status")
    public ResponseEntity<?> changeMenuItemAvailability(
            @PathVariable UUID restaurantId,
            @PathVariable UUID menuItemId,
            @RequestBody MenuItemStatusRequest request,
            HttpSession session
    ) {
        return execute(() -> authorizedService.changeMenuItemAvailability(
                restaurantId, menuItemId, request.toStatus(), session));
    }

    @PostMapping("/restaurants/{restaurantId}/orders")
    public ResponseEntity<?> placeOrder(
            @PathVariable UUID restaurantId,
            @RequestBody PlaceOrderRequest request,
            HttpSession session
    ) {
        return execute(() -> authorizedService.placeOrder(restaurantId, request.lines(), session));
    }

    @GetMapping("/orders")
    public ResponseEntity<?> orders(HttpSession session) {
        return execute(() -> authorizedService.viewOrders(session));
    }

    @PostMapping("/restaurants/{restaurantId}/preparation/next")
    public ResponseEntity<?> startNextPreparation(@PathVariable UUID restaurantId, HttpSession session) {
        return execute(() -> authorizedService.startNextPreparation(restaurantId, session));
    }

    @GetMapping("/restaurants/{restaurantId}/preparation")
    public ResponseEntity<?> preparationQueue(@PathVariable UUID restaurantId, HttpSession session) {
        return execute(() -> authorizedService.viewPreparationQueue(restaurantId, session));
    }

    @PostMapping("/restaurants/{restaurantId}/orders/{orderId}/ready")
    public ResponseEntity<?> markOrderReady(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            HttpSession session
    ) {
        return execute(() -> authorizedService.markOrderReady(restaurantId, orderId, session));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable UUID orderId, HttpSession session) {
        return execute(() -> authorizedService.cancelOrder(orderId, session));
    }

    @GetMapping("/orders/{orderId}/events")
    public ResponseEntity<?> orderEvents(@PathVariable UUID orderId, HttpSession session) {
        return execute(() -> authorizedService.viewOrderEvents(orderId, session));
    }

    private <T> ResponseEntity<?> execute(AuthorizedOperation<T> operation) {
        return execute(operation, HttpStatus.OK);
    }

    private <T> ResponseEntity<?> execute(AuthorizedOperation<T> operation, HttpStatus successStatus) {
        try {
            return ResponseEntity.status(successStatus).body(operation.run());
        } catch (CatalogAccessException exception) {
            return ResponseEntity.status(exception.getStatusCode())
                    .body(new ErrorResponse(exception.getMessage()));
        } catch (AuthenticationRequiredException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(exception.getMessage()));
        } catch (AuthorizationException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(exception.getMessage()));
        } catch (MenuItemNotFoundException | OrderNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(exception.getMessage()));
        } catch (EmptyPreparationQueueException | IllegalOrderTransitionException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(exception.getMessage()));
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> invalidRequestBody() {
        return ResponseEntity.badRequest().body(new ErrorResponse("Request body is missing or malformed"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> invalidIdentifier() {
        return ResponseEntity.badRequest().body(new ErrorResponse("Restaurant, menu item and order IDs must be valid UUIDs"));
    }

    @FunctionalInterface
    private interface AuthorizedOperation<T> {
        T run();
    }

    public record PlaceOrderRequest(List<OrdService.OrderLineRequest> lines) {
    }

    public record MenuItemRequest(String name, BigDecimal price) {
    }

    public record MenuItemStatusRequest(String status) {
        public MenuItemStatus toStatus() {
            if (status != null) {
                try {
                    return MenuItemStatus.valueOf(status);
                } catch (IllegalArgumentException exception) {
                    // Only the enum names are accepted, including when JSON scalars are coerced to strings.
                }
            }
            throw new IllegalArgumentException("Menu item status must be AVAILABLE or SOLD_OUT");
        }
    }

    public record ErrorResponse(String message) {
    }
}
