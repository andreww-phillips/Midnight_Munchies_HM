package com.midnightmunchies.application.dlv;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dlv/orders")
@CrossOrigin(allowCredentials = "true", origins = "http://localhost:5173")
public class PickupController extends DlvControllerSupport {
    private final CourierService courierService;

    public PickupController(CourierService courierService) {
        this.courierService = courierService;
    }

    @PostMapping("/{orderId}/pickup")
    public ResponseEntity<?> pickUp(@PathVariable UUID orderId, HttpSession session) {
        return execute(() -> courierService.pickUp(account(session), orderId));
    }
}
