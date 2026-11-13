package com.midnightmunchies.application.dlv;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dlv/courier/duty")
@CrossOrigin(allowCredentials = "true", origins = "http://localhost:5173")
public class DutyController extends DlvControllerSupport {
    private final CourierService courierService;

    public DutyController(CourierService courierService) {
        this.courierService = courierService;
    }

    @PutMapping
    public ResponseEntity<?> setDuty(@RequestBody DutyRequest request, HttpSession session) {
        return execute(() -> {
            if (request.onDuty() == null) {
                throw new IllegalArgumentException("onDuty must be true or false");
            }
            return courierService.setDuty(account(session), request.onDuty());
        });
    }

    public record DutyRequest(Boolean onDuty) {
    }
}
