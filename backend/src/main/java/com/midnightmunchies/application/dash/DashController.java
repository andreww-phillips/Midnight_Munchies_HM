package com.midnightmunchies.application.dash;

import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dash")
@CrossOrigin(allowCredentials = "true", origins = "http://localhost:5173")
public class DashController {

    private final MapService mapService;

    public DashController(MapService mapService) {
        this.mapService = mapService;
    }

    @GetMapping("/map")
    public ResponseEntity<?> map(HttpSession session) {
        try {
            return ResponseEntity.ok(mapService.cityMap(session));
        } catch (AuthenticationRequiredException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(exception.getMessage()));
        }
    }

    public record ErrorResponse(String message) {
    }
}
