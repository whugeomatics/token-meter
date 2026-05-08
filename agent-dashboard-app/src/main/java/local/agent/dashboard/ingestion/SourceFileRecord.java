package local.agent.dashboard.ingestion;

public record SourceFileRecord(long id, long sizeBytes, String modifiedAt, String fileFingerprint) {
    public boolean sameFile(SourceFileState state) {
        return sizeBytes == state.sizeBytes()
                && modifiedAt.equals(state.modifiedAt())
                && fileFingerprint.equals(state.fileFingerprint());
    }
}
