package local.token.meter.http;

import com.sun.net.httpserver.HttpExchange;
import local.token.meter.store.TokenHasher;

import java.time.Duration;

public final class AdminAuth {
    private static final String COOKIE_NAME = "token_meter_admin";
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
        return authorizedCookie(exchange.getRequestHeaders().getFirst("Cookie"));
    }

    boolean authorizedCookie(String cookie) {
        if (cookie == null || cookie.isBlank()) {
            return false;
        }
        for (String part : cookie.split(";")) {
            String item = part.trim();
            int index = item.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String name = item.substring(0, index).trim();
            String value = item.substring(index + 1).trim();
            if (COOKIE_NAME.equals(name) && adminTokenHash.equals(value)) {
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
