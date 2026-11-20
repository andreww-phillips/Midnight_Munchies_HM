package com.midnightmunchies.application.dash;

import com.midnightmunchies.application.dlv.CourierService;
import com.midnightmunchies.application.ord.OrdResetService;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ResetControllerTests {
    private static final UUID DINER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    @Autowired private MockMvc mvc;
    @Autowired private SimulationAccess access;
    @Autowired private RestaurantRepository restaurants;
    @MockitoSpyBean private CourierService delivery;
    @MockitoSpyBean private OrdResetService orders;
    private MockHttpSession operator;

    @BeforeEach
    void startFromSeed() throws Exception {
        operator = login("operator");
        resetSuccessfully();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/dash/reset")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
        assertNormalRequestsWork();
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "restaurant", "courier"})
    void deniesOtherRolesWithoutRestoringState(String role) throws Exception {
        pauseRestaurant();
        mvc.perform(post("/api/dash/reset").session(login(role))).andExpect(status().isForbidden());
        assertEquals(RestaurantStatus.PAUSED, restaurants.findById(DINER).orElseThrow().getStatus());
        assertNormalRequestsWork();
    }

    @Test
    void operatorCanResetRepeatedlyAndKeepTheSameSession() throws Exception {
        pauseRestaurant();
        resetSuccessfully();
        assertEquals(RestaurantStatus.OPEN, restaurants.findById(DINER).orElseThrow().getStatus());
        resetSuccessfully();
        mvc.perform(get("/api/auth/me").session(operator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PLATFORM_OPERATOR"));
        mvc.perform(get("/api/dash/reset").session(operator)).andExpect(status().isMethodNotAllowed());
        assertNormalRequestsWork();
    }

    @Test
    void persistenceFailureReturnsASafeErrorAndKeepsExistingStateUsable() throws Exception {
        pauseRestaurant();
        doThrow(new IllegalStateException("private queue file path")).when(delivery).resetToInitialState();
        try {
            mvc.perform(post("/api/dash/reset").session(operator)).andExpect(status().isInternalServerError())
                    .andExpect(content().json("{\"message\":\"Reset could not be completed. Please retry reset.\"}"));
            assertEquals(RestaurantStatus.PAUSED, restaurants.findById(DINER).orElseThrow().getStatus());
            assertNormalRequestsWork();
        } finally {
            doCallRealMethod().when(delivery).resetToInitialState();
            resetSuccessfully();
        }
    }

    @Test
    void partialFailureBlocksStateEndpointsButAllowsOperatorLoginAndRetry() throws Exception {
        doThrow(new IllegalStateException("ORD failure")).when(orders).resetToInitialState();
        try {
            mvc.perform(post("/api/dash/reset").session(operator)).andExpect(status().isInternalServerError());
            for (String path : new String[]{"/api/ord/restaurants", "/api/dlv/courier", "/api/prc/history", "/api/dash/map"}) {
                mvc.perform(get(path).session(operator)).andExpect(status().isServiceUnavailable());
            }
            mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("{}"))
                    .andExpect(status().isServiceUnavailable());
            mvc.perform(get("/api/auth/me").session(operator)).andExpect(status().isOk());
            MockHttpSession recovery = login("operator");
            mvc.perform(post("/api/auth/logout").session(recovery)).andExpect(status().isNoContent());
            operator = login("operator");
        } finally {
            doCallRealMethod().when(orders).resetToInitialState();
            resetSuccessfully();
        }
        assertNormalRequestsWork();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/me", "/api/ord/restaurants", "/api/dlv/courier", "/api/prc/history", "/api/dash/map"})
    void requestsAcrossAllModulesAreRejectedDuringReset(String path) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> access.reset(() -> {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test reset timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return null;
            }));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                mvc.perform(get(path).session(operator)).andExpect(status().isConflict());
                mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("{}"))
                        .andExpect(status().isConflict());
                mvc.perform(post("/api/dash/reset").session(operator)).andExpect(status().isConflict());
            } finally {
                release.countDown();
            }
            running.get(5, TimeUnit.SECONDS);
        }
        assertNormalRequestsWork();
    }

    private void pauseRestaurant() throws Exception {
        mvc.perform(patch("/api/ord/restaurants/" + DINER + "/status").session(login("restaurant"))
                .contentType(APPLICATION_JSON).content("{\"status\":\"PAUSED\"}")).andExpect(status().isOk());
    }

    private void resetSuccessfully() throws Exception {
        mvc.perform(post("/api/dash/reset").session(operator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESET"))
                .andExpect(jsonPath("$.revision").isNumber())
                .andExpect(jsonPath("$.message").value("Initial simulator state restored."));
    }

    private void assertNormalRequestsWork() throws Exception {
        mvc.perform(get("/api/ord/restaurants").session(operator)).andExpect(status().isOk());
    }

    private MockHttpSession login(String role) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session).contentType(APPLICATION_JSON)
                .content("{\"username\":\"" + role + "@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk());
        return session;
    }
}
