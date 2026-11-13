package com.midnightmunchies.application.ord;

import com.midnightmunchies.application.reg.AuthService;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.ord.MenuItemRepository;
import com.midnightmunchies.domain.ord.OrderEventLog;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.DemoCatalog;
import com.midnightmunchies.technicalservices.SimulatorStore;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class SimulationResetService {

    private static final String SESSION_USER = "authenticatedUser";

    private final RestaurantRepository restaurants;
    private final MenuItemRepository menuItems;
    private final OrderRepository orders;
    private final OrderEventLog events;
    private final AuthService accounts;

    public SimulationResetService(RestaurantRepository restaurants, MenuItemRepository menuItems,
                                  OrderRepository orders, OrderEventLog events, AuthService accounts) {
        this.restaurants = Objects.requireNonNull(restaurants, "restaurants must not be null");
        this.menuItems = Objects.requireNonNull(menuItems, "menuItems must not be null");
        this.orders = Objects.requireNonNull(orders, "orders must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
        this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
    }

    public synchronized void reset(HttpSession session) {
        if (session == null || !(session.getAttribute(SESSION_USER) instanceof UserAccount user)) {
            throw new AuthenticationRequiredException("Authentication is required");
        }
        if (user.role() != UserRole.PLATFORM_OPERATOR) {
            throw new AuthorizationException("Only the operator can reset the simulation");
        }
        List<Object> stores = List.of(orders, events, menuItems, restaurants);
        if (!stores.stream().allMatch(SimulatorStore.class::isInstance)) {
            throw new IllegalStateException("Reset is only available with the in-memory simulator data");
        }
        stores.forEach(store -> ((SimulatorStore) store).clear());
        accounts.removeRegisteredAccounts();
        DemoCatalog.seed(restaurants, menuItems);
    }
}
