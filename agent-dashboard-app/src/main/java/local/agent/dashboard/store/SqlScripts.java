package local.agent.dashboard.store;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SqlScripts {
    private final Map<String, String> statements;

    private SqlScripts(Map<String, String> statements) {
        this.statements = statements;
    }

    static SqlScripts load(String resourcePath) throws IOException {
        try (InputStream input = SqlScripts.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("SQL resource not found: " + resourcePath);
            }
            return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    String statement(String name) {
        String sql = statements.get(name);
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL statement not found: " + name);
        }
        return sql;
    }

    private static SqlScripts parse(String text) {
        Map<String, StringBuilder> builders = new LinkedHashMap<>();
        String currentName = null;
        for (String line : text.split("\\R")) {
            if (line.startsWith("-- name:")) {
                currentName = line.substring("-- name:".length()).trim();
                builders.put(currentName, new StringBuilder());
                continue;
            }
            if (currentName == null || line.startsWith("--")) {
                continue;
            }
            builders.get(currentName).append(line).append('\n');
        }

        Map<String, String> statements = new LinkedHashMap<>();
        for (Map.Entry<String, StringBuilder> entry : builders.entrySet()) {
            String sql = entry.getValue().toString().trim();
            if (sql.endsWith(";")) {
                sql = sql.substring(0, sql.length() - 1).trim();
            }
            statements.put(entry.getKey(), sql);
        }
        return new SqlScripts(statements);
    }
}
