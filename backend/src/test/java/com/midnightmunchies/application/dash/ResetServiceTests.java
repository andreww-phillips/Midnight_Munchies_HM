package com.midnightmunchies.application.dash;

import com.midnightmunchies.application.dlv.CourierService;
import com.midnightmunchies.application.ord.AuthorizationException;
import com.midnightmunchies.application.ord.OrdResetService;
import com.midnightmunchies.application.prc.OrderLedgerService;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.application.reg.SessionService;
import com.midnightmunchies.technicalservices.ReviewRepository;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResetServiceTests {
    private final SimulationAccess access = new SimulationAccess();
    private final OrdResetService orders = mock(OrdResetService.class);
    private final CourierService delivery = mock(CourierService.class);
    private final OrderLedgerService pricing = mock(OrderLedgerService.class);
    private final SessionService sessions = mock(SessionService.class);
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ResetService service = new ResetService(access, Optional.of(orders), delivery, pricing, sessions, reviews);

    @Test
    void resetsAllModulesInOrderAndReturnsAnIncreasingRevision() {
        var first = service.resetToInitialState(session(UserRole.PLATFORM_OPERATOR));
        var sequence = inOrder(delivery, orders, pricing, reviews, sessions);
        sequence.verify(delivery).resetToInitialState();
        sequence.verify(orders).resetToInitialState();
        sequence.verify(pricing).resetToInitialState();
        sequence.verify(reviews).clear();
        sequence.verify(sessions).resetToInitialState();
        var second = service.resetToInitialState(session(UserRole.PLATFORM_OPERATOR));
        assertEquals("RESET", first.status());
        assertTrue(second.revision() > first.revision());
        assertAvailable();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "PLATFORM_OPERATOR", mode = EnumSource.Mode.EXCLUDE)
    void deniedRolesCannotStartResetOrChangeState(UserRole role) {
        assertThrows(AuthorizationException.class, () -> service.resetToInitialState(session(role)));
        verifyNoInteractions(delivery, orders, pricing, reviews, sessions);
        assertAvailable();
    }

    @Test
    void missingOrExpiredSessionsCannotStartReset() {
        assertThrows(AuthenticationRequiredException.class, () -> service.resetToInitialState(null));
        assertThrows(AuthenticationRequiredException.class, () -> service.resetToInitialState(new MockHttpSession()));
        MockHttpSession expired = session(UserRole.PLATFORM_OPERATOR);
        expired.invalidate();
        assertThrows(AuthenticationRequiredException.class, () -> service.resetToInitialState(expired));
        verifyNoInteractions(delivery, orders, pricing, reviews, sessions);
    }

    @Test
    void rechecksSessionBeforeChangingAnyState() {
        HttpSession expiring = mock(HttpSession.class);
        when(expiring.getAttribute("authenticatedUser"))
                .thenReturn(account(UserRole.PLATFORM_OPERATOR)).thenReturn(null);
        assertThrows(AuthenticationRequiredException.class, () -> service.resetToInitialState(expiring));
        verifyNoInteractions(delivery, orders, pricing, reviews, sessions);
        assertAvailable();
    }

    @Test
    void unsupportedCatalogModeDoesNotResetOtherModules() {
        ResetService unsupported = new ResetService(access, Optional.empty(), delivery, pricing, sessions, reviews);
        assertThrows(UnsupportedOperationException.class,
                () -> unsupported.resetToInitialState(session(UserRole.PLATFORM_OPERATOR)));
        verifyNoInteractions(delivery, orders, pricing, reviews, sessions);
        assertAvailable();
    }

    @Test
    void failedQueuePersistenceLeavesOtherModulesUntouchedAndRequestsAvailable() {
        IllegalStateException diskFailure = new IllegalStateException("Disk write failed");
        doThrow(diskFailure).when(delivery).resetToInitialState();
        var failure = assertThrows(ResetService.ResetFailedException.class,
                () -> service.resetToInitialState(session(UserRole.PLATFORM_OPERATOR)));
        assertSame(diskFailure, failure.getCause());
        verifyNoInteractions(orders, pricing, reviews, sessions);
        assertAvailable();
    }

    @ParameterizedTest
    @ValueSource(strings = {"orders", "pricing", "sessions"})
    void aPartialFailureBlocksStateAccessUntilAnOperatorRetriesReset(String stage) {
        if (stage.equals("orders")) doThrow(new IllegalStateException("ORD failed")).when(orders).resetToInitialState();
        if (stage.equals("pricing")) doThrow(new IllegalStateException("PRC failed")).when(pricing).resetToInitialState();
        if (stage.equals("sessions")) doThrow(new IllegalStateException("REG failed")).when(sessions).resetToInitialState();

        assertThrows(ResetService.ResetFailedException.class,
                () -> service.resetToInitialState(session(UserRole.PLATFORM_OPERATOR)));
        var blocked = assertThrows(SimulationAccess.UnavailableException.class, () -> access.openRequest(false));
        assertTrue(blocked.recoveryRequired());
        Lock login = access.openRequest(true);
        login.unlock();

        doNothing().when(orders).resetToInitialState();
        doNothing().when(pricing).resetToInitialState();
        doNothing().when(sessions).resetToInitialState();
        assertEquals("RESET", service.resetToInitialState(session(UserRole.PLATFORM_OPERATOR)).status());
        assertAvailable();
    }

    @Test
    void resetWaitsForAnExistingRequestToFinish() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            Lock existingRequest = access.openRequest(false);
            CountDownLatch submitted = new CountDownLatch(1);
            var result = executor.submit(() -> {
                submitted.countDown();
                return service.resetToInitialState(session(UserRole.PLATFORM_OPERATOR));
            });
            try {
                assertTrue(submitted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> result.get(100, TimeUnit.MILLISECONDS));
                verifyNoInteractions(delivery, orders, pricing, reviews, sessions);
            } finally {
                existingRequest.unlock();
            }
            assertEquals("RESET", result.get(5, TimeUnit.SECONDS).status());
        }
    }

    @Test
    void requestsAndConcurrentResetAttemptsAreRejectedWhileResetIsRunning() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test reset timed out");
            return null;
        }).when(delivery).resetToInitialState();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(() -> service.resetToInitialState(session(UserRole.PLATFORM_OPERATOR)));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var blocked = assertThrows(SimulationAccess.UnavailableException.class, () -> access.openRequest(false));
                assertFalse(blocked.recoveryRequired());
                assertThrows(SimulationAccess.UnavailableException.class,
                        () -> service.resetToInitialState(session(UserRole.PLATFORM_OPERATOR)));
            } finally {
                release.countDown();
            }
            assertEquals("RESET", result.get(5, TimeUnit.SECONDS).status());
        }
        verify(delivery, times(1)).resetToInitialState();
        assertAvailable();
    }

    private void assertAvailable() {
        Lock request = access.openRequest(false);
        request.unlock();
    }

    private MockHttpSession session(UserRole role) {
        MockHttpSession result = new MockHttpSession();
        result.setAttribute("authenticatedUser", account(role));
        return result;
    }

    private UserAccount account(UserRole role) {
        return new UserAccount(UUID.randomUUID().toString(), "reset@example.test", "hash", role);
    }
}
