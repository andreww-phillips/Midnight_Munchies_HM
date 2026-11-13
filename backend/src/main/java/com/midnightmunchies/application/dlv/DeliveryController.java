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
public class DeliveryController extends DlvControllerSupport {
    private final CourierService courierService;

    public DeliveryController(CourierService courierService) {
        this.courierService = courierService;
    }

    @PostMapping("/{orderId}/deliver")
    public ResponseEntity<?> deliver(@PathVariable UUID orderId, HttpSession session) {
        return execute(() -> courierService.deliver(account(session), orderId));
    }
}
