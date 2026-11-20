package com.midnightmunchies.application.reg;

import com.midnightmunchies.domain.reg.UserAccount;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks authenticated sessions so reset can revoke accounts removed from the scenario. */
@Service
public class SessionService implements HttpSessionListener {
    private final AuthService auth;
    private final Map<String, HttpSession> sessions = new ConcurrentHashMap<>();

    public SessionService(AuthService auth) {
        this.auth = auth;
    }

    public void track(HttpSession session) {
        sessions.put(session.getId(), session);
    }

    public void forget(HttpSession session) {
        sessions.remove(session.getId(), session);
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        forget(event.getSession());
    }

    /** Internal reset operation; callers must exclude registration and other module commands. */
    public void resetToInitialState() {
        Set<String> seededIds = auth.restoreInitialAccounts();
        sessions.forEach((id, session) -> {
            try {
                if (!(session.getAttribute("authenticatedUser") instanceof UserAccount user)
                        || !seededIds.contains(user.id())) {
                    sessions.remove(id, session);
                    session.invalidate();
                }
            } catch (IllegalStateException expired) {
                sessions.remove(id, session);
            }
        });
    }
}
