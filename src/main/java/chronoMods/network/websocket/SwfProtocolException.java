package chronoMods.network.websocket;

public class SwfProtocolException extends RuntimeException {
    public SwfProtocolException(String message) {
        super(message);
    }

    public SwfProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
