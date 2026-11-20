package com.midnightmunchies.application.dash;

import com.midnightmunchies.application.reg.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Set;
import java.util.concurrent.locks.Lock;

@Configuration
public class SimulationRequestConfiguration implements WebMvcConfigurer {
    private static final String REQUEST_LOCK = SimulationRequestConfiguration.class.getName() + ".lock";
    private final SimulationAccess access;

    public SimulationRequestConfiguration(SimulationAccess access) {
        this.access = access;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (handler instanceof HandlerMethod method && ResetController.class.isAssignableFrom(method.getBeanType())) {
                    // ResetService takes exclusive access itself, after checking operator permissions.
                    return true;
                }
                boolean recoveryLogin = handler instanceof HandlerMethod method
                        && AuthController.class.isAssignableFrom(method.getBeanType())
                        && Set.of("login", "currentUser", "logout").contains(method.getMethod().getName());
                request.setAttribute(REQUEST_LOCK, access.openRequest(recoveryLogin));
                return true;
            }

            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                        Object handler, Exception exception) {
                if (request.getAttribute(REQUEST_LOCK) instanceof Lock lock) {
                    request.removeAttribute(REQUEST_LOCK);
                    lock.unlock();
                }
            }
        }).addPathPatterns("/api/**");
    }
}
