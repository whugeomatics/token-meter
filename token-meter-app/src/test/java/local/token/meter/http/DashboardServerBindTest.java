package local.token.meter.http;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DashboardServerBindTest {

    @Test
    void bindAddressShouldBeZeroZeroZeroZero() {
        assertEquals("0.0.0.0", DashboardServer.BIND_ADDRESS,
            "DashboardServer must bind to 0.0.0.0 to be reachable from other hosts");
    }
}
