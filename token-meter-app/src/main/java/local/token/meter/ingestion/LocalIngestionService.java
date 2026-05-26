package local.token.meter.ingestion;

public final class LocalIngestionService {
    private final CodexIngestionService codex;
    private final ClaudeCodeLocalIngestionService claudeCode;

    public LocalIngestionService(CodexIngestionService codex, ClaudeCodeLocalIngestionService claudeCode) {
        this.codex = codex;
        this.claudeCode = claudeCode;
    }

    public synchronized IngestionResult ingest() {
        IngestionResult result = new IngestionResult();
        result.merge(codex.ingest());
        result.merge(claudeCode.ingest());
        return result;
    }
}
