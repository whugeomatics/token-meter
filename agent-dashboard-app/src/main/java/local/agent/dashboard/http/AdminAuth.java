package local.agent.dashboard.http;

import com.sun.net.httpserver.HttpExchange;
import local.agent.dashboard.store.TokenHasher;

import java.net.HttpCookie;
import java.time.Duration;
import java.util.List;

public final class AdminAuth {
    private static final String COOKIE_NAME = "agent_dashboard_admin";
    private static final Duration COOKIE_MAX_AGE = Duration.ofHours(8);

    private final String adminTokenHash;

    public AdminAuth(String adminToken) {
        this.adminTokenHash = adminToken == null || adminToken.isBlank() ? "" : TokenHasher.sha256(adminToken);
    }

    public boolean enabled() {
        return !adminTokenHash.isBlank();
    }

    public boolean matches(String token) {
        return enabled() && token != null && adminTokenHash.equals(TokenHasher.sha256(token));
    }

    public boolean authorized(HttpExchange exchange) {
        if (!enabled()) {
            return false;
        }
        String headerToken = exchange.getRequestHeaders().getFirst("X-Admin-Token");
        if (matches(headerToken)) {
            return true;
        }
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie == null || cookie.isBlank()) {
            return false;
        }
        List<HttpCookie> cookies = HttpCookie.parse(cookie);
        for (HttpCookie item : cookies) {
            if (COOKIE_NAME.equals(item.getName()) && adminTokenHash.equals(item.getValue())) {
                return true;
            }
        }
        return false;
    }

    public String loginCookie() {
        return COOKIE_NAME + "=" + adminTokenHash + "; Path=/; Max-Age=" + COOKIE_MAX_AGE.toSeconds()
                + "; HttpOnly; SameSite=Strict";
    }
}
