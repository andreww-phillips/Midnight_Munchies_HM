package com.midnightmunchies.application.reg;

import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.InMemoryRestaurantRepository;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionServiceTests {
    private final AuthService auth = new AuthService(new InMemoryRestaurantRepository());
    private final SessionService sessions = new SessionService(auth);

    @Test
    void resetRetainsSeededCredentialsAndIdentitiesAndRevokesEverySessionOfRemovedAccounts() {
        List<UserAccount> baseline = List.of("customer", "restaurant", "courier", "operator").stream()
                .map(role -> auth.authenticate(role + "@example.com", "password")).toList();
        MockHttpSession seededOperator = tracked(baseline.getLast());
        UserAccount runtime = auth.register("new@example.test", "runtime-password", UserRole.CUSTOMER);
        MockHttpSession first = tracked(runtime);
        MockHttpSession second = tracked(runtime);

        sessions.resetToInitialState();
        sessions.resetToInitialState();
        assertTrue(first.isInvalid());
        assertTrue(second.isInvalid());
        assertFalse(seededOperator.isInvalid());
        assertEquals(baseline.getLast(), seededOperator.getAttribute("authenticatedUser"));
        assertNull(auth.authenticate("new@example.test", "runtime-password"));
        baseline.forEach(user -> assertEquals(user, auth.authenticate(user.username(), "password")));
        assertNotEquals(runtime.id(), auth.register("new@example.test", "new-password", UserRole.CUSTOMER).id());
    }

    @Test
    void expiredAndDestroyedSessionsDoNotPreventReset() {
        UserAccount runtime = auth.register("expired@example.test", "password", UserRole.COURIER);
        MockHttpSession expired = tracked(runtime);
        expired.invalidate();
        MockHttpSession destroyed = tracked(runtime);
        sessions.sessionDestroyed(new HttpSessionEvent(destroyed));
        destroyed.invalidate();
        assertDoesNotThrow(sessions::resetToInitialState);
        assertNull(auth.authenticate(runtime.username(), "password"));
    }

    @Test
    void sessionReauthenticatedAsASeededAccountIsRetained() {
        UserAccount runtime = auth.register("old@example.test", "password", UserRole.CUSTOMER);
        MockHttpSession session = tracked(runtime);
        UserAccount seed = auth.authenticate("operator@example.com", "password");
        session.setAttribute("authenticatedUser", seed);
        sessions.track(session);
        sessions.resetToInitialState();
        assertFalse(session.isInvalid());
        assertEquals(seed, session.getAttribute("authenticatedUser"));
        assertNull(auth.authenticate(runtime.username(), "password"));
    }

    private MockHttpSession tracked(UserAccount account) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", account);
        sessions.track(session);
        return session;
    }
}
