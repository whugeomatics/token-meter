package local.agent.dashboard.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class DashboardPage {
    private DashboardPage() {
    }

    static String resource(String path) throws IOException {
        try (InputStream input = DashboardPage.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("static dashboard resource not found: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
