package local.token.meter.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class AdminAuthTest {
    @Test
    void authorizesAdminCookieWhenItIsNotFirstCookie() {
        AdminAuth auth = new AdminAuth("secret-admin-token");
        String cookie = "other_cookie=other-value; " + auth.loginCookie().split(";", 2)[0];

        assertTrue(auth.authorizedCookie(cookie));
    }
}
