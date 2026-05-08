package local.agent.dashboard.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record SourceFileState(String path, long sizeBytes, String modifiedAt, String fileFingerprint) {
    public static SourceFileState from(Path file) throws IOException {
        String path = file.toAbsolutePath().normalize().toString();
        long size = Files.size(file);
        String modified = Files.getLastModifiedTime(file).toInstant().toString();
        return new SourceFileState(path, size, modified, path + "|" + size + "|" + modified);
    }
}
