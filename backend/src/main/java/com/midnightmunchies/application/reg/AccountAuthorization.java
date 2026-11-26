package com.midnightmunchies.application.reg;

import com.midnightmunchies.application.ord.AuthorizationException;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import org.springframework.stereotype.Component;

/** Checks account presence and role without interpreting IDs or changing application state. */
@Component
public class AccountAuthorization {

    public UserAccount requireRole(UserAccount account, UserRole requiredRole, String refusalMessage) {
        if (account == null) {
            throw new AuthenticationRequiredException("Authentication is required");
        }
        if (account.role() != requiredRole) {
            throw new AuthorizationException(refusalMessage);
        }
        return account;
    }
}
