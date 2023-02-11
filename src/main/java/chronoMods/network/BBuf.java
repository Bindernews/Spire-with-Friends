package chronoMods.network;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Helpers for {@link ByteBuffer}.
 */
public class BBuf {

    public static ByteBuffer allocate(int capacity) {
        return ByteBuffer.allocateDirect(capacity);
    }

    /**
     * Shorthand for {@link Buffer#position(int)}
     */
    public static ByteBuffer pos(ByteBuffer b, int newPosition) {
        ((Buffer)b).position(newPosition);
        return b;
    }

    /**
     * Adds 2 bytes denoting the length of the byte array, and then the byte array itself.
     * Mostly useful when adding strings.
     */
    public static ByteBuffer putLenBytes(ByteBuffer b, byte[] bytes) {
        b.putShort((short)bytes.length);
        b.put(bytes);
        return b;
    }

    public static byte[] getLenBytes(ByteBuffer b) {
        int len = b.getShort();
        byte[] bytes = new byte[len];
        b.get(bytes);
        return bytes;
    }

    /**
     * Reads 2 bytes indicating the length of the string, then reads the UTF8-encoded string
     * @return The string
     */
    public static String getLenString(ByteBuffer b) {
        byte[] bytes = getLenBytes(b);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
