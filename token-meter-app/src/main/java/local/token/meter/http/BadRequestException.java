package local.token.meter.http;

public final class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
