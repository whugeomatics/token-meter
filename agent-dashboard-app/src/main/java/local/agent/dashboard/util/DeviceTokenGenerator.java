package local.agent.dashboard.util;

import java.security.SecureRandom;
import java.util.Base64;

public final class DeviceTokenGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    private DeviceTokenGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
