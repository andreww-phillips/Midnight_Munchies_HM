package com.midnightmunchies.application.ord;

import com.midnightmunchies.application.dash.MapService;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.dash.QueueBand;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.InMemoryOrderRepository;
import com.midnightmunchies.technicalservices.InMemoryRestaurantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestaurantStatusServiceTests {

    private static final UUID OWNER = UUID.fromString("30000000-0000-0000-0000-0000000000aa");

    private final InMemoryRestaurantRepository restaurants = new InMemoryRestaurantRepository();
    private final RestaurantStatusService service = new RestaurantStatusService(restaurants);
    private final Restaurant diner = restaurants.save(
            new Restaurant(UUID.randomUUID(), OWNER, "Diner", RestaurantStatus.OPEN, 45.5, -73.57));

    @Test
    void ownerCanPauseAndReopenTheirRestaurant() {
        MockHttpSession owner = session(OWNER, UserRole.RESTAURANT_STAFF);

        assertEquals(RestaurantStatus.PAUSED, service.pause(diner.getId(), owner).getStatus());
        assertEquals(RestaurantStatus.PAUSED, stored().getStatus());
        assertEquals(RestaurantStatus.OPEN, service.reopen(diner.getId(), owner).getStatus());
        assertEquals(RestaurantStatus.OPEN, stored().getStatus());
    }

    @Test
    void operatorCanPauseAndReopenAnyRestaurant() {
        MockHttpSession operator = session(UUID.randomUUID(), UserRole.PLATFORM_OPERATOR);

        service.pause(diner.getId(), operator);
        assertEquals(RestaurantStatus.PAUSED, stored().getStatus());
        service.reopen(diner.getId(), operator);
        assertEquals(RestaurantStatus.OPEN, stored().getStatus());
    }

    @Test
    void operatorCanReopenAClosedRestaurant() {
        restaurants.save(diner.withStatus(RestaurantStatus.CLOSED));

        service.reopen(diner.getId(), session(UUID.randomUUID(), UserRole.PLATFORM_OPERATOR));

        assertEquals(RestaurantStatus.OPEN, stored().getStatus());
    }

    @Test
    void staffCannotChangeAnotherRestaurant() {
        MockHttpSession otherStaff = session(UUID.randomUUID(), UserRole.RESTAURANT_STAFF);

        assertThrows(AuthorizationException.class, () -> service.pause(diner.getId(), otherStaff));
        assertEquals(RestaurantStatus.OPEN, stored().getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CUSTOMER", "COURIER"})
    void customersAndCouriersCannotPauseOrReopen(UserRole role) {
        MockHttpSession user = session(UUID.randomUUID(), role);

        assertThrows(AuthorizationException.class, () -> service.pause(diner.getId(), user));
        assertThrows(AuthorizationException.class, () -> service.reopen(diner.getId(), user));
        assertEquals(RestaurantStatus.OPEN, stored().getStatus());
    }

    @Test
    void repeatedChangesAreRejected() {
        MockHttpSession owner = session(OWNER, UserRole.RESTAURANT_STAFF);

        assertThrows(IllegalStateException.class, () -> service.reopen(diner.getId(), owner));
        service.pause(diner.getId(), owner);
        assertThrows(IllegalStateException.class, () -> service.pause(diner.getId(), owner));
    }

    @Test
    void closedRestaurantCannotBePaused() {
        restaurants.save(diner.withStatus(RestaurantStatus.CLOSED));

        assertThrows(IllegalStateException.class,
                () -> service.pause(diner.getId(), session(OWNER, UserRole.RESTAURANT_STAFF)));
    }

    @Test
    void unknownRestaurantAndMissingLoginAreRejected() {
        assertThrows(RestaurantNotFoundException.class,
                () -> service.pause(UUID.randomUUID(), session(OWNER, UserRole.PLATFORM_OPERATOR)));
        assertThrows(AuthenticationRequiredException.class, () -> service.pause(diner.getId(), new MockHttpSession()));
    }

    @Test
    void pausedRestaurantTurnsRedOnTheMap() {
        MapService map = new MapService(restaurants, new InMemoryOrderRepository(), List::of);
        MockHttpSession owner = session(OWNER, UserRole.RESTAURANT_STAFF);

        service.pause(diner.getId(), owner);
        assertEquals(QueueBand.RED, map.cityMap(owner).restaurants().get(0).band());
        service.reopen(diner.getId(), owner);
        assertEquals(QueueBand.GREEN, map.cityMap(owner).restaurants().get(0).band());
    }

    private Restaurant stored() {
        return restaurants.findById(diner.getId()).orElseThrow();
    }

    private static MockHttpSession session(UUID id, UserRole role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", new UserAccount(id.toString(), "user@example.com", "hash", role));
        return session;
    }
}
