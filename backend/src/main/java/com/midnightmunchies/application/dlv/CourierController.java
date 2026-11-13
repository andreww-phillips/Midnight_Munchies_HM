package com.midnightmunchies.application.dlv;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dlv/courier")
@CrossOrigin(allowCredentials = "true", origins = "http://localhost:5173")
public class CourierController extends DlvControllerSupport {
    private final CourierService courierService;

    public CourierController(CourierService courierService) {
        this.courierService = courierService;
    }

    @GetMapping
    public ResponseEntity<?> dashboard(HttpSession session) {
        return execute(() -> courierService.view(account(session)));
    }
}
