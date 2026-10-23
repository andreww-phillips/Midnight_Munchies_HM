package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryMenuItemRepository;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryOrderRepository;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryRestaurantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PreparationQueueAuthorizationTests {

    private final InMemoryRestaurantRepository restaurants = new InMemoryRestaurantRepository();
    private final InMemoryMenuItemRepository menuItems = new InMemoryMenuItemRepository();
    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final PreparationQueue queue = new PreparationQueue(orders);
    private final AuthorizedOrdService service = new AuthorizedOrdService(
            new RealOrdService(restaurants, menuItems, orders, queue), restaurants);

    private final UUID ownerId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();

    {
        restaurants.save(new Restaurant(restaurantId, ownerId, "Diner", RestaurantStatus.OPEN, 0, 0));
    }

    @Test
    void ownerCanViewAndStartTheirQueue() {
        Order first = enqueue();
        Order second = enqueue();
        MockHttpSession owner = session(ownerId, UserRole.RESTAURANT_STAFF);

        assertEquals(List.of(first, second), service.viewPreparationQueue(restaurantId, owner));
        Order started = service.startNextPreparation(restaurantId, owner);

        assertEquals(first.getId(), started.getId());
        assertEquals(OrderStatus.PREPARING, started.getStatus());
        assertEquals(List.of(second), service.viewPreparationQueue(restaurantId, owner));
    }

    @Test
    void staffOfAnotherRestaurantIsDeniedAndQueueIsUnchanged() {
        Order waiting = enqueue();
        MockHttpSession otherStaff = session(UUID.randomUUID(), UserRole.RESTAURANT_STAFF);

        assertThrows(AuthorizationException.class, () -> service.startNextPreparation(restaurantId, otherStaff));
        assertThrows(AuthorizationException.class, () -> service.viewPreparationQueue(restaurantId, otherStaff));
        assertEquals(OrderStatus.ACCEPTED, waiting.getStatus());
        assertEquals(List.of(waiting), queue.view(restaurantId));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "RESTAURANT_STAFF", mode = EnumSource.Mode.EXCLUDE)
    void nonStaffRolesAreDenied(UserRole role) {
        Order waiting = enqueue();
        MockHttpSession session = session(ownerId, role);

        assertThrows(AuthorizationException.class, () -> service.startNextPreparation(restaurantId, session));
        assertThrows(AuthorizationException.class, () -> service.viewPreparationQueue(restaurantId, session));
        assertEquals(OrderStatus.ACCEPTED, waiting.getStatus());
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        enqueue();

        assertThrows(AuthenticationRequiredException.class,
                () -> service.startNextPreparation(restaurantId, new MockHttpSession()));
        assertThrows(AuthenticationRequiredException.class,
                () -> service.viewPreparationQueue(restaurantId, new MockHttpSession()));
    }

    @Test
    void unknownRestaurantIsRejected() {
        MockHttpSession owner = session(ownerId, UserRole.RESTAURANT_STAFF);

        assertThrows(IllegalArgumentException.class, () -> service.startNextPreparation(UUID.randomUUID(), owner));
        assertThrows(IllegalArgumentException.class, () -> service.startNextPreparation(null, owner));
    }

    private Order enqueue() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), restaurantId,
                List.of(new OrderLine(UUID.randomUUID(), 1, BigDecimal.TEN)), Instant.now());
        order.accept();
        return queue.enqueueAccepted(order);
    }

    private MockHttpSession session(UUID userId, UserRole role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser",
                new UserAccount(userId.toString(), "user@example.com", "hash", role));
        return session;
    }
}
