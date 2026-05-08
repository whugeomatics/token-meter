package local.agent.dashboard.util;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public final class CliOutput {
    private CliOutput() {
    }

    public static void writeLine(String value) {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(FileDescriptor.out), StandardCharsets.UTF_8), true);
        writer.println(value);
    }
}
