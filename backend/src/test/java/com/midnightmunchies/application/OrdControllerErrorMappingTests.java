package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.IllegalOrderTransitionException;
import com.midnightmunchies.domain.ord.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrdControllerErrorMappingTests {

    private final AuthorizedOrdService service = mock(AuthorizedOrdService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OrdController(service)).build();

    @Test
    void illegalOrderTransitionReturnsConflictWithMessage() throws Exception {
        when(service.startNextPreparation(any(), any()))
                .thenThrow(new IllegalOrderTransitionException(OrderStatus.PLACED, "start preparing"));

        mockMvc.perform(post("/api/ord/restaurants/{restaurantId}/preparation/next", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot start preparing an order that is PLACED"));
    }

    @Test
    void otherIllegalStateErrorsAreNotTreatedAsConflicts() {
        when(service.startNextPreparation(any(), any())).thenThrow(new IllegalStateException("unexpected"));

        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/api/ord/restaurants/{restaurantId}/preparation/next", UUID.randomUUID())));
    }
}
