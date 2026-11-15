package com.midnightmunchies.application.prc;

import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import com.midnightmunchies.domain.prc.HistorySession;
import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.LedgerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Reads ledger facts through the existing role strategies without changing orders or pricing. */
@Service
public class OrderHistoryService {

    private final LedgerRepository ledger;
    private final RestaurantRepository restaurants;
    private final HistoryService history;

    public OrderHistoryService(LedgerRepository ledger, RestaurantRepository restaurants, HistoryService history) {
        this.ledger = ledger;
        this.restaurants = restaurants;
        this.history = history;
    }

    public List<OrderHistoryResponse> getHistory(UserAccount account) {
        requireAuthenticated(account);
        return authorizedEntries(account, ledger.findAll()).stream()
                .map(entry -> OrderHistoryResponse.from(entry, account.role()))
                .toList();
    }

    /** Missing and unauthorized orders are deliberately indistinguishable. */
    public Optional<OrderHistoryResponse> findOrder(UUID orderId, UserAccount account) {
        requireAuthenticated(account);
        return authorizedEntries(account, ledger.findByOrderId(orderId).stream().toList()).stream()
                .map(entry -> OrderHistoryResponse.from(entry, account.role()))
                .findFirst();
    }

    private List<LedgerEntry> authorizedEntries(UserAccount account, List<LedgerEntry> entries) {
        UUID userId = UUID.fromString(account.id());
        if (account.role() == UserRole.RESTAURANT_STAFF) {
            // Accounts need not share their ID with a restaurant, and may own more than one.
            Set<UUID> visibleOrders = restaurants.findAll().stream()
                    .filter(restaurant -> userId.equals(restaurant.getOwnerId()))
                    .flatMap(restaurant -> history.getHistory(
                            new HistorySession(userId, account.role(), restaurant.getId()), entries).stream())
                    .map(LedgerEntry::orderId)
                    .collect(Collectors.toSet());
            // Keep the repository's stable order across all owned restaurants.
            return entries.stream().filter(entry -> visibleOrders.contains(entry.orderId())).toList();
        }
        return history.getHistory(new HistorySession(userId, account.role(), null), entries);
    }

    private void requireAuthenticated(UserAccount account) {
        if (account == null) {
            throw new AuthenticationRequiredException("Authentication is required");
        }
    }
}
