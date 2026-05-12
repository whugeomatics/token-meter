package local.token.meter.app;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenMeterAppBindTest {

    @Test
    void serverBindAddressShouldBeZeroZeroZeroZero() {
        assertEquals("0.0.0.0", TokenMeterApp.SERVER_BIND_ADDRESS,
            "TokenMeterApp log message must use 0.0.0.0 for external access");
    }
}
