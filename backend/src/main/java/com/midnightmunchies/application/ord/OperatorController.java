package com.midnightmunchies.application.ord;

import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operator")
@CrossOrigin(allowCredentials = "true", origins = "http://localhost:5173")
public class OperatorController {

    private final SimulationResetService resetService;

    public OperatorController(SimulationResetService resetService) {
        this.resetService = resetService;
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset(HttpSession session) {
        try {
            resetService.reset(session);
            return ResponseEntity.noContent().build();
        } catch (AuthenticationRequiredException exception) {
            return error(HttpStatus.UNAUTHORIZED, exception);
        } catch (AuthorizationException exception) {
            return error(HttpStatus.FORBIDDEN, exception);
        } catch (IllegalStateException exception) {
            return error(HttpStatus.CONFLICT, exception);
        }
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, RuntimeException exception) {
        return ResponseEntity.status(status).body(new ErrorResponse(exception.getMessage()));
    }

    public record ErrorResponse(String message) {
    }
}
