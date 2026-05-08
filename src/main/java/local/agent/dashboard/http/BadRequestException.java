package local.agent.dashboard.http;

public final class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
