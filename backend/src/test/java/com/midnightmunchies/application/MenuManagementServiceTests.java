package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.MenuItemStatus;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryMenuItemRepository;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryOrderRepository;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryRestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MenuManagementServiceTests {
    private final InMemoryRestaurantRepository restaurants = new InMemoryRestaurantRepository();
    private final InMemoryMenuItemRepository menuItems = new InMemoryMenuItemRepository();
    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final RealOrdService realService = new RealOrdService(restaurants, menuItems, orders);
    private final OrdService service = new AuthorizedOrdService(realService, restaurants);
    private final UUID ownerId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID otherRestaurantId = UUID.randomUUID();
    private final MockHttpSession ownerSession = session(ownerId, UserRole.RESTAURANT_STAFF);
    private final MenuItem original = new MenuItem(UUID.randomUUID(), restaurantId, "Poutine",
            new BigDecimal("12.35"), MenuItemStatus.AVAILABLE);
    private final MenuItem otherItem = new MenuItem(UUID.randomUUID(), otherRestaurantId, "Pizza",
            new BigDecimal("16.99"), MenuItemStatus.AVAILABLE);

    @BeforeEach
    void setUp() {
        restaurants.save(new Restaurant(restaurantId, ownerId, "Diner", RestaurantStatus.OPEN, 0, 0));
        restaurants.save(new Restaurant(otherRestaurantId, UUID.randomUUID(), "Other Diner", RestaurantStatus.OPEN, 0, 0));
        menuItems.save(original);
        menuItems.save(otherItem);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "5.49", "0.12345678901234567890"})
    void ownerCanAddItemWithAvailableStatusAndExactPrice(String price) {
        MenuItem added = service.addMenuItem(restaurantId, "Fries", new BigDecimal(price), ownerSession);

        assertNotEquals(original.getId(), added.getId());
        assertEquals(restaurantId, added.getRestaurantId());
        assertEquals("Fries", added.getName());
        assertEquals(new BigDecimal(price), added.getPrice());
        assertEquals(MenuItemStatus.AVAILABLE, added.getStatus());
        assertSame(added, menuItems.findById(added.getId()).orElseThrow());
        assertEquals(Set.of(original, added), Set.copyOf(service.browseMenu(restaurantId, ownerSession)));
        assertEquals(List.of(otherItem), menuItems.findByRestaurantId(otherRestaurantId));
    }

    @Test
    void ownerCanEditNameAndPriceWithoutChangingIdentityRestaurantOrAvailability() {
        MenuItem soldOut = new MenuItem(original.getId(), restaurantId, original.getName(),
                original.getPrice(), MenuItemStatus.SOLD_OUT);
        menuItems.save(soldOut);

        MenuItem edited = service.editMenuItem(restaurantId, original.getId(), "Loaded Poutine",
                BigDecimal.ZERO, ownerSession);

        assertEquals(original.getId(), edited.getId());
        assertEquals(restaurantId, edited.getRestaurantId());
        assertEquals("Loaded Poutine", edited.getName());
        assertEquals(BigDecimal.ZERO, edited.getPrice());
        assertEquals(MenuItemStatus.SOLD_OUT, edited.getStatus());
        assertEquals("Poutine", soldOut.getName());
        assertEquals(new BigDecimal("12.35"), soldOut.getPrice());
        assertSame(edited, menuItems.findById(original.getId()).orElseThrow());
        assertEquals(Set.of(edited, otherItem), Set.copyOf(menuItems.findAll()));
    }

    @ParameterizedTest
    @EnumSource(MenuItemStatus.class)
    void ownerCanChangeAvailabilityInBothDirectionsWithoutChangingItemDetails(MenuItemStatus status) {
        MenuItemStatus previousStatus = status == MenuItemStatus.AVAILABLE
                ? MenuItemStatus.SOLD_OUT : MenuItemStatus.AVAILABLE;
        menuItems.save(new MenuItem(original.getId(), restaurantId, original.getName(), original.getPrice(), previousStatus));

        MenuItem changed = service.changeMenuItemAvailability(restaurantId, original.getId(), status, ownerSession);

        assertEquals(original.getId(), changed.getId());
        assertEquals(restaurantId, changed.getRestaurantId());
        assertEquals(original.getName(), changed.getName());
        assertEquals(original.getPrice(), changed.getPrice());
        assertEquals(status, changed.getStatus());
        assertSame(changed, menuItems.findById(changed.getId()).orElseThrow());
        assertEquals(List.of(otherItem), menuItems.findByRestaurantId(otherRestaurantId));
    }

    @Test
    void ownerCanRemoveItemWithoutAffectingAnotherRestaurant() {
        service.removeMenuItem(restaurantId, original.getId(), ownerSession);

        assertTrue(menuItems.findById(original.getId()).isEmpty());
        assertTrue(service.browseMenu(restaurantId, ownerSession).isEmpty());
        assertEquals(List.of(otherItem), menuItems.findAll());
    }

    @Test
    void unauthenticatedRequestsAreRejectedBeforeAnyDelegation() {
        OrdService delegate = mock(OrdService.class);
        OrdService guarded = new AuthorizedOrdService(delegate, restaurants);

        assertAllOperationsRejected(guarded, restaurantId, new MockHttpSession(), AuthenticationRequiredException.class);

        verifyNoInteractions(delegate);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "RESTAURANT_STAFF", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesCannotManageMenuEvenWhenTheirIdentityMatchesTheOwner(UserRole role) {
        assertAllOperationsRejected(service, restaurantId, session(ownerId, role), AuthorizationException.class);
    }

    @Test
    void staffCannotManageAnotherOwnersRestaurant() {
        assertAllOperationsRejected(service, otherRestaurantId, ownerSession, AuthorizationException.class);
    }

    @Test
    void staffCannotManageARestaurantWithoutAnOwner() {
        restaurants.save(new Restaurant(restaurantId, "Unassigned Diner", RestaurantStatus.OPEN, 0, 0));

        assertAllOperationsRejected(service, restaurantId, ownerSession, AuthorizationException.class);
    }

    @Test
    void missingOrNullRestaurantsAreRejectedByBothServicesWithoutChanges() {
        for (OrdService target : List.of(service, realService)) {
            assertAllOperationsRejected(target, UUID.randomUUID(), ownerSession, IllegalArgumentException.class);
            assertAllOperationsRejected(target, null, ownerSession, IllegalArgumentException.class);
        }
    }

    @Test
    void missingOrNullMenuItemsAreRejectedWithoutChanges() {
        for (OrdService target : List.of(service, realService)) {
            assertItemOperationsRejected(target, restaurantId, UUID.randomUUID(), ownerSession, IllegalArgumentException.class);
            assertItemOperationsRejected(target, restaurantId, null, ownerSession, IllegalArgumentException.class);
        }
    }

    @Test
    void menuItemMustBelongToRequestedRestaurantEvenWhenStaffOwnsBoth() {
        restaurants.save(new Restaurant(otherRestaurantId, ownerId, "Other Diner", RestaurantStatus.OPEN, 0, 0));

        for (OrdService target : List.of(service, realService)) {
            assertItemOperationsRejected(target, restaurantId, otherItem.getId(), ownerSession, IllegalArgumentException.class);
        }
    }

    @ParameterizedTest
    @MethodSource("invalidItemDetails")
    void invalidNamesAndPricesDoNotAddOrReplaceItems(String name, BigDecimal price) {
        assertRejectedWithoutChanges(IllegalArgumentException.class,
                () -> service.addMenuItem(restaurantId, name, price, ownerSession));
        assertRejectedWithoutChanges(IllegalArgumentException.class,
                () -> service.editMenuItem(restaurantId, original.getId(), name, price, ownerSession));
    }

    static Stream<Arguments> invalidItemDetails() {
        return Stream.of(
                Arguments.of(null, BigDecimal.ONE),
                Arguments.of("", BigDecimal.ONE),
                Arguments.of(" \t\n", BigDecimal.ONE),
                Arguments.of("Poutine", null),
                Arguments.of("Poutine", new BigDecimal("-0.01")),
                Arguments.of("Poutine", new BigDecimal("-10.00"))
        );
    }

    @Test
    void missingAvailabilityDoesNotReplaceTheItem() {
        assertRejectedWithoutChanges(IllegalArgumentException.class,
                () -> service.changeMenuItemAvailability(restaurantId, original.getId(), null, ownerSession));
    }

    @Test
    void menuManagementPreservesPreviouslyPlacedOrderInformation() {
        MockHttpSession customerSession = session(UUID.randomUUID(), UserRole.CUSTOMER);
        Order placed = service.placeOrder(restaurantId,
                List.of(new OrdService.OrderLineRequest(original.getId(), 2)), customerSession);

        service.editMenuItem(restaurantId, original.getId(), "Loaded Poutine", new BigDecimal("15.00"), ownerSession);
        service.changeMenuItemAvailability(restaurantId, original.getId(), MenuItemStatus.SOLD_OUT, ownerSession);
        service.removeMenuItem(restaurantId, original.getId(), ownerSession);

        Order stored = service.viewOrders(customerSession).getFirst();
        assertSame(placed, stored);
        assertEquals(1, stored.getOrderLines().size());
        OrderLine line = stored.getOrderLines().getFirst();
        assertEquals(original.getId(), line.getMenuItemId());
        assertEquals("Poutine", line.getMenuItemName());
        assertEquals(2, line.getQuantity());
        assertEquals(new BigDecimal("12.35"), line.getUnitPrice());
    }

    private void assertAllOperationsRejected(OrdService target, UUID requestedRestaurantId,
                                            MockHttpSession session, Class<? extends Throwable> expected) {
        assertRejectedWithoutChanges(expected,
                () -> target.addMenuItem(requestedRestaurantId, "Fries", BigDecimal.ONE, session));
        UUID itemId = requestedRestaurantId != null && requestedRestaurantId.equals(otherRestaurantId)
                ? otherItem.getId() : original.getId();
        assertItemOperationsRejected(target, requestedRestaurantId, itemId, session, expected);
    }

    private void assertItemOperationsRejected(OrdService target, UUID requestedRestaurantId, UUID itemId,
                                             MockHttpSession session, Class<? extends Throwable> expected) {
        assertRejectedWithoutChanges(expected,
                () -> target.editMenuItem(requestedRestaurantId, itemId, "Changed", BigDecimal.ONE, session));
        assertRejectedWithoutChanges(expected,
                () -> target.removeMenuItem(requestedRestaurantId, itemId, session));
        for (MenuItemStatus status : MenuItemStatus.values()) {
            assertRejectedWithoutChanges(expected,
                    () -> target.changeMenuItemAvailability(requestedRestaurantId, itemId, status, session));
        }
    }

    private void assertRejectedWithoutChanges(Class<? extends Throwable> expected, Executable operation) {
        Set<MenuItem> previousMenu = Set.copyOf(menuItems.findAll());
        Set<Restaurant> previousRestaurants = Set.copyOf(restaurants.findAll());
        Set<Order> previousOrders = Set.copyOf(orders.findAll());

        assertThrows(expected, operation);

        assertEquals(previousMenu, Set.copyOf(menuItems.findAll()));
        assertEquals(previousRestaurants, Set.copyOf(restaurants.findAll()));
        assertEquals(previousOrders, Set.copyOf(orders.findAll()));
    }

    private static MockHttpSession session(UUID userId, UserRole role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", new UserAccount(userId.toString(), "user@example.com", "hash", role));
        return session;
    }
}
