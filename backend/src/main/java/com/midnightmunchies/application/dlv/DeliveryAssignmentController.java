package com.midnightmunchies.application.dlv;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dlv")
@CrossOrigin(allowCredentials = "true", origins = "http://localhost:5173")
public class DeliveryAssignmentController extends DlvControllerSupport {
    private final CourierService courierService;

    public DeliveryAssignmentController(CourierService courierService) {
        this.courierService = courierService;
    }

    @PostMapping("/orders/{orderId}/accept")
    public ResponseEntity<?> accept(@PathVariable UUID orderId, HttpSession session) {
        return execute(() -> courierService.accept(account(session), orderId));
    }

    @PostMapping("/orders/{orderId}/decline")
    public ResponseEntity<?> decline(@PathVariable UUID orderId, HttpSession session) {
        return execute(() -> courierService.decline(account(session), orderId));
    }

    @PostMapping("/orders/{orderId}/reassign")
    public ResponseEntity<?> reassign(@PathVariable UUID orderId, HttpSession session) {
        return execute(() -> courierService.reassign(account(session), orderId));
    }

    @GetMapping("/operator/deliveries")
    public ResponseEntity<?> operatorDeliveries(HttpSession session) {
        return execute(() -> courierService.operatorDeliveries(account(session)));
    }
}
