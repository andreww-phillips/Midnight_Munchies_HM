package com.midnightmunchies.application.ord;

import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.ord.DuplicateNameException;
import com.midnightmunchies.domain.ord.IllegalOrderTransitionException;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.technicalservices.CatalogAccessException;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrdControllerErrorMappingTests {

    private static final String NEXT = "/api/ord/restaurants/{restaurantId}/preparation/next";

    private final AuthorizedOrdService service = mock(AuthorizedOrdService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OrdController(service)).build();

    @ParameterizedTest(name = "{0} returns HTTP {2}")
    @MethodSource("mappedErrors")
    void translatesServiceErrorsToHttpResponses(
            String scenario, RuntimeException error, int httpStatus, String message
    ) throws Exception {
        when(service.browseRestaurants(any())).thenThrow(error);

        mockMvc.perform(get("/api/ord/restaurants"))
                .andExpect(status().is(httpStatus))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(message));
    }

    static Stream<Arguments> mappedErrors() {
        return Stream.of(
                Arguments.of("Catalog unavailable", new CatalogAccessException("Catalog unavailable", 503, null),
                        503, "Catalog unavailable"),
                Arguments.of("Catalog schema conflict", new CatalogAccessException("Catalog schema outdated", 409, null),
                        409, "Catalog schema outdated"),
                Arguments.of("Authentication required", new AuthenticationRequiredException("Authentication is required"),
                        401, "Authentication is required"),
                Arguments.of("Access denied", new AuthorizationException("Action is forbidden"),
                        403, "Action is forbidden"),
                Arguments.of("Menu item missing", new MenuItemNotFoundException("Menu item does not exist"),
                        404, "Menu item does not exist"),
                Arguments.of("Order missing", new OrderNotFoundException("Order does not exist"),
                        404, "Order does not exist"),
                Arguments.of("Duplicate name", new DuplicateNameException("Name already exists"),
                        409, "Name already exists"),
                Arguments.of("Empty queue", new EmptyPreparationQueueException("No accepted orders are waiting to be prepared"),
                        409, "No accepted orders are waiting to be prepared"),
                Arguments.of("Invalid input", new IllegalArgumentException("Restaurant does not exist"),
                        400, "Restaurant does not exist")
        );
    }

    @Test
    void illegalOrderTransitionReturnsConflictWithMessage() throws Exception {
        when(service.startNextPreparation(any(), any()))
                .thenThrow(new IllegalOrderTransitionException(OrderStatus.PLACED, "start preparing"));

        mockMvc.perform(post(NEXT, UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot start preparing an order that is PLACED"));
    }

    @Test
    void otherIllegalStateErrorsAreNotTreatedAsConflicts() {
        IllegalStateException failure = new IllegalStateException("unexpected");
        when(service.startNextPreparation(any(), any())).thenThrow(failure);

        ServletException thrown = assertThrows(ServletException.class, () ->
                mockMvc.perform(post(NEXT, UUID.randomUUID())));
        assertSame(failure, thrown.getCause());
    }

    @Test
    void wrappedOrderNotFoundDoesNotBecomeANotFoundResponse() {
        IllegalStateException failure = new IllegalStateException("unexpected",
                new OrderNotFoundException("Order does not exist"));
        when(service.startNextPreparation(any(), any())).thenThrow(failure);

        ServletException thrown = assertThrows(ServletException.class, () ->
                mockMvc.perform(post(NEXT, UUID.randomUUID())));
        assertSame(failure, thrown.getCause());
    }

    @ParameterizedTest
    @MethodSource("unreadableRequestBodies")
    void unreadableJsonReturnsBadRequestWithMessage(String body) throws Exception {
        mockMvc.perform(post("/api/ord/restaurants/{restaurantId}/orders", UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
        verifyNoInteractions(service);
    }

    static Stream<String> unreadableRequestBodies() {
        return Stream.of("{", "");
    }

    @Test
    void invalidUuidReturnsBadRequestWithMessage() throws Exception {
        mockMvc.perform(post(NEXT, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Restaurant, menu item and order IDs must be valid UUIDs"));
        verifyNoInteractions(service);
    }
}
