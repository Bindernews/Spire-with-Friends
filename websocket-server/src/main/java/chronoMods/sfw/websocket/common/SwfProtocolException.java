package chronoMods.sfw.websocket.common;

public class SwfProtocolException extends RuntimeException {
    public SwfProtocolException(String message) {
        super(message);
    }

    public SwfProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
