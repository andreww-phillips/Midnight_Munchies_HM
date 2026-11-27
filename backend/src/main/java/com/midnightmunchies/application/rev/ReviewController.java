package com.midnightmunchies.application.rev;

import com.midnightmunchies.application.ord.AuthorizationException;
import com.midnightmunchies.application.ord.OrderNotFoundException;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.rev.ReviewTarget;
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

import java.util.UUID;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/reviews")
@CrossOrigin(allowCredentials = "true", origins = "http://localhost:5173")
public class ReviewController {
    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> orderReviews(@PathVariable UUID orderId, HttpSession session) {
        return execute(() -> reviews.orderReviews(orderId, account(session)));
    }

    @PostMapping("/orders/{orderId}")
    public ResponseEntity<?> submit(
            @PathVariable UUID orderId,
            @RequestBody ReviewRequest request,
            HttpSession session
    ) {
        return execute(() -> reviews.submit(orderId, request.target(), request.rating(), request.text(),
                account(session)), HttpStatus.CREATED);
    }

    @GetMapping("/restaurants/{restaurantId}")
    public ResponseEntity<?> restaurantReviews(@PathVariable UUID restaurantId, HttpSession session) {
        return execute(() -> reviews.restaurantReviews(restaurantId, account(session)));
    }

    @GetMapping("/couriers/{courierId}")
    public ResponseEntity<?> courierReviews(@PathVariable UUID courierId, HttpSession session) {
        return execute(() -> reviews.courierReviews(courierId, account(session)));
    }

    private UserAccount account(HttpSession session) {
        return session == null ? null : (UserAccount) session.getAttribute("authenticatedUser");
    }

    private ResponseEntity<?> execute(Supplier<?> operation) {
        return execute(operation, HttpStatus.OK);
    }

    private ResponseEntity<?> execute(Supplier<?> operation, HttpStatus successStatus) {
        try {
            return ResponseEntity.status(successStatus).body(operation.get());
        } catch (AuthenticationRequiredException exception) {
            return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
        } catch (AuthorizationException exception) {
            return error(HttpStatus.FORBIDDEN, exception.getMessage());
        } catch (OrderNotFoundException exception) {
            return error(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (IllegalStateException exception) {
            return error(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }

    public record ReviewRequest(ReviewTarget target, Integer rating, String text) {
    }

    public record ErrorResponse(String message) {
    }
}
