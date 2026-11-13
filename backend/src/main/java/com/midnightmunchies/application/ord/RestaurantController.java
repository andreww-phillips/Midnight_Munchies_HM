package com.midnightmunchies.application.ord;

import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.ord.Restaurant;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/ord/restaurants/{restaurantId}")
@CrossOrigin(allowCredentials = "true", origins = "http://localhost:5173")
public class RestaurantController {

    private final RestaurantStatusService statusService;

    public RestaurantController(RestaurantStatusService statusService) {
        this.statusService = statusService;
    }

    @PostMapping("/pause")
    public ResponseEntity<?> pause(@PathVariable UUID restaurantId, HttpSession session) {
        return execute(() -> statusService.pause(restaurantId, session));
    }

    @PostMapping("/reopen")
    public ResponseEntity<?> reopen(@PathVariable UUID restaurantId, HttpSession session) {
        return execute(() -> statusService.reopen(restaurantId, session));
    }

    private ResponseEntity<?> execute(Supplier<Restaurant> command) {
        try {
            return ResponseEntity.ok(command.get());
        } catch (AuthenticationRequiredException exception) {
            return error(HttpStatus.UNAUTHORIZED, exception);
        } catch (AuthorizationException exception) {
            return error(HttpStatus.FORBIDDEN, exception);
        } catch (RestaurantNotFoundException exception) {
            return error(HttpStatus.NOT_FOUND, exception);
        } catch (IllegalStateException exception) {
            return error(HttpStatus.CONFLICT, exception);
        }
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, RuntimeException exception) {
        return ResponseEntity.status(status).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> invalidIdentifier() {
        return ResponseEntity.badRequest().body(new ErrorResponse("Restaurant ID must be a valid UUID"));
    }

    public record ErrorResponse(String message) {
    }
}
