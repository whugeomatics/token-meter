package local.token.meter.store;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public final class TeamUsageStores {
    private TeamUsageStores() {
    }

    public static TeamUsageStore open(Path path) throws IOException {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".sqlite") || name.endsWith(".db")) {
            return new SqliteTeamUsageStore(path);
        }
        return new ShardedTeamUsageStore(path);
    }

    public static Path resolveTeamDbPath(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".sqlite") || name.endsWith(".db")) {
            return path;
        }
        return path.resolve("token-meter-team-YYYY-MM.sqlite");
    }

    public static Path resolveTeamRegistryPath(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".sqlite") || name.endsWith(".db")) {
            return path;
        }
        return path.resolve("token-meter-team-registry.sqlite");
    }
}
