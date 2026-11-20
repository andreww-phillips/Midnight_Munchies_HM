package com.midnightmunchies.application.dash;

import com.midnightmunchies.application.dlv.CourierService;
import com.midnightmunchies.application.ord.AuthorizationException;
import com.midnightmunchies.application.ord.OrdResetService;
import com.midnightmunchies.application.prc.OrderLedgerService;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.application.reg.SessionService;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ResetService {
    private final SimulationAccess access;
    private final Optional<OrdResetService> orders;
    private final CourierService delivery;
    private final OrderLedgerService pricing;
    private final SessionService sessions;

    public ResetService(SimulationAccess access, Optional<OrdResetService> orders, CourierService delivery,
                        OrderLedgerService pricing, SessionService sessions) {
        this.access = access;
        this.orders = orders;
        this.delivery = delivery;
        this.pricing = pricing;
        this.sessions = sessions;
    }

    public ResetResult resetToInitialState(HttpSession session) {
        requireOperator(session);
        OrdResetService ord = orders.orElseThrow(() -> new UnsupportedOperationException(
                "Reset is available only in memory catalog mode"));
        return access.reset(() -> {
            // A session may have expired while waiting for existing requests to finish.
            requireOperator(session);
            try {
                // Disk persistence is the first fallible step; failure here leaves all state intact.
                delivery.resetToInitialState();
                access.requireRecovery();
                ord.resetToInitialState();
                pricing.resetToInitialState();
                sessions.resetToInitialState();
                access.restored();
                return new ResetResult("RESET", access.revision(), "Initial simulator state restored.");
            } catch (RuntimeException failure) {
                throw new ResetFailedException(failure);
            }
        });
    }

    private void requireOperator(HttpSession session) {
        UserAccount user;
        try {
            user = session != null && session.getAttribute("authenticatedUser") instanceof UserAccount account
                    ? account : null;
        } catch (IllegalStateException expired) {
            user = null;
        }
        if (user == null) throw new AuthenticationRequiredException("Authentication is required");
        if (user.role() != UserRole.PLATFORM_OPERATOR) {
            throw new AuthorizationException("Only platform operators can reset the simulator");
        }
    }

    public record ResetResult(String status, long revision, String message) { }

    public static class ResetFailedException extends RuntimeException {
        public ResetFailedException(Throwable cause) { super("Reset could not be completed", cause); }
    }
}
