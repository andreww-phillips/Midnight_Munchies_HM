package com.midnightmunchies.application;

import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(allowCredentials = "true", origins = "http://localhost:5173")
public class AuthController {
    private static final String SESSION_USER = "authenticatedUser";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@RequestBody LoginRequest request, HttpSession session) {
        UserAccount account = authService.authenticate(request.username(), request.password());
        if (account == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new UserResponse(null, null, null, "Username or password is incorrect"));
        }
        session.setAttribute(SESSION_USER, account);
        return ResponseEntity.ok(UserResponse.from(account));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request, HttpSession session) {
        try {
            UserAccount account = authService.register(request.username(), request.password(), request.role(),
                    request.restaurantName(), request.restaurantLogo(), request.menu());
            session.setAttribute(SESSION_USER, account);
            return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(account));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(new UserResponse(null, null, null, exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new UserResponse(null, null, null, exception.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> currentUser(HttpSession session) {
        UserAccount account = (UserAccount) session.getAttribute(SESSION_USER);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(UserResponse.from(account));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(String username, String password) {
    }

    public record RegisterRequest(String username, String password, UserRole role,
                                  String restaurantName, String restaurantLogo, String menu) {
    }

    public record UserResponse(String id, String username, UserRole role, String message) {
        private static UserResponse from(UserAccount account) {
            return new UserResponse(account.id(), account.username(), account.role(), null);
        }
    }
}
