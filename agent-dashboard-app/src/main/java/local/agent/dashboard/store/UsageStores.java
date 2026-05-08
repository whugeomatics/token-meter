package local.agent.dashboard.store;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public final class UsageStores {
    private UsageStores() {
    }

    public static UsageStore open(Path path) throws IOException {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".sqlite") || name.endsWith(".db")) {
            return new SqliteUsageStore(path);
        }
        return new ShardedUsageStore(path);
    }
}
