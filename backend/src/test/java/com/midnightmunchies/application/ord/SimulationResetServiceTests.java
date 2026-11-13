package com.midnightmunchies.application.ord;

import com.midnightmunchies.application.reg.AuthService;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.InMemoryMenuItemRepository;
import com.midnightmunchies.technicalservices.InMemoryOrderEventLog;
import com.midnightmunchies.technicalservices.InMemoryOrderRepository;
import com.midnightmunchies.technicalservices.InMemoryRestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SimulationResetServiceTests {

    @Test
    void resetIsRefusedWhenDataIsNotInMemory() {
        RestaurantRepository shared = mock(RestaurantRepository.class);
        SimulationResetService service = new SimulationResetService(shared, new InMemoryMenuItemRepository(),
                new InMemoryOrderRepository(), new InMemoryOrderEventLog(), new AuthService(new InMemoryRestaurantRepository()));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.reset(operator()));
        assertEquals("Reset is only available with the in-memory simulator data", exception.getMessage());
    }

    @Test
    void resetSeedsTheDemoCatalog() {
        InMemoryRestaurantRepository restaurants = new InMemoryRestaurantRepository();
        InMemoryMenuItemRepository menuItems = new InMemoryMenuItemRepository();
        SimulationResetService service = new SimulationResetService(restaurants, menuItems,
                new InMemoryOrderRepository(), new InMemoryOrderEventLog(), new AuthService(restaurants));

        service.reset(operator());
        service.reset(operator());

        assertEquals(2, restaurants.findAll().size());
        assertEquals(4, menuItems.findAll().size());
    }

    private static MockHttpSession operator() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser",
                new UserAccount(UUID.randomUUID().toString(), "operator@example.com", "hash", UserRole.PLATFORM_OPERATOR));
        return session;
    }
}
