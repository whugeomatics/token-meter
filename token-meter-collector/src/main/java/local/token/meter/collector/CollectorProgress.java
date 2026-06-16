package local.token.meter.collector;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

final class CollectorProgress {
    private final PrintWriter writer;

    CollectorProgress(PrintWriter writer) {
        this.writer = writer;
    }

    static CollectorProgress stderr() {
        return new CollectorProgress(new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(FileDescriptor.err), StandardCharsets.UTF_8), true));
    }

    void step(int percent, String message) {
        int normalized = Math.max(0, Math.min(100, percent));
        writer.println("[" + normalized + "%] " + message);
    }
}
