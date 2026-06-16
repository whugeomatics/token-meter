package local.token.meter.collector;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CollectorProgressTest {
    @Test
    void writesPercentProgressLines() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CollectorProgress progress = new CollectorProgress(new PrintWriter(output, true, StandardCharsets.UTF_8));

        progress.step(85, "Uploading usage snapshot (12 events)");

        assertEquals("[85%] Uploading usage snapshot (12 events)" + System.lineSeparator(),
                output.toString(StandardCharsets.UTF_8));
    }
}
