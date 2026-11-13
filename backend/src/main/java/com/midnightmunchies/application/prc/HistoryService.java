package com.midnightmunchies.application.prc;

import com.midnightmunchies.domain.prc.CourierHistoryScope;
import com.midnightmunchies.domain.prc.CustomerHistoryScope;
import com.midnightmunchies.domain.prc.HistoryEntry;
import com.midnightmunchies.domain.prc.HistoryScope;
import com.midnightmunchies.domain.prc.HistorySession;
import com.midnightmunchies.domain.prc.OperatorHistoryScope;
import com.midnightmunchies.domain.prc.RestaurantHistoryScope;
import com.midnightmunchies.domain.reg.UserRole;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Picks the history scope for the viewer's role and lets it filter the entries. */
@Service
public class HistoryService {

    private final Map<UserRole, HistoryScope> scopes = new EnumMap<>(UserRole.class);

    public HistoryService() {
        scopes.put(UserRole.CUSTOMER, new CustomerHistoryScope());
        scopes.put(UserRole.RESTAURANT_STAFF, new RestaurantHistoryScope());
        scopes.put(UserRole.COURIER, new CourierHistoryScope());
        scopes.put(UserRole.PLATFORM_OPERATOR, new OperatorHistoryScope());
    }

    public <E extends HistoryEntry> List<E> getHistory(HistorySession session, List<E> entries) {
        Objects.requireNonNull(session, "session must not be null");
        return scopeFor(session.role()).filter(entries, session);
    }

    private HistoryScope scopeFor(UserRole role) {
        HistoryScope scope = scopes.get(role);
        if (scope == null) {
            throw new IllegalArgumentException("No order history is defined for role " + role);
        }
        return scope;
    }
}
