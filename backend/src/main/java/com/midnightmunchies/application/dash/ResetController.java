package com.midnightmunchies.application.dash;

import com.midnightmunchies.application.ord.AuthorizationException;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dash")
@CrossOrigin(allowCredentials = "true", origins = "http://localhost:5173")
public class ResetController {
    private static final Logger log = LoggerFactory.getLogger(ResetController.class);
    private final ResetService reset;

    public ResetController(ResetService reset) { this.reset = reset; }

    @PostMapping("/reset")
    public ResponseEntity<?> reset(HttpSession session) {
        try {
            return ResponseEntity.ok(reset.resetToInitialState(session));
        } catch (AuthenticationRequiredException exception) {
            return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
        } catch (AuthorizationException exception) {
            return error(HttpStatus.FORBIDDEN, exception.getMessage());
        } catch (UnsupportedOperationException exception) {
            return error(HttpStatus.CONFLICT, exception.getMessage());
        } catch (ResetService.ResetFailedException exception) {
            log.error("Simulator reset failed", exception);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Reset could not be completed. Please retry reset.");
        }
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }

    public record ErrorResponse(String message) { }
}
