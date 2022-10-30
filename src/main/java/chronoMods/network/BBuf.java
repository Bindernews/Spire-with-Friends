package chronoMods.network;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Wrapper around {@link ByteBuffer} with convenience methods to help make writing packet code easier.
 */
public class BBuf {

    /** The inner byte buffer */
    public ByteBuffer b;

    public BBuf(ByteBuffer inner) {
        b = inner;
    }

    public static BBuf allocate(int capacity) {
        return new BBuf(ByteBuffer.allocateDirect(capacity));
    }

    /**
     * Shorthand for {@link Buffer#position(int)}
     */
    public BBuf pos(int newPosition) {
        ((Buffer)b).position(newPosition);
        return this;
    }

    public BBuf putInt(int v) {
        b.putInt(v);
        return this;
    }

    public int getInt() {
        return b.getInt();
    }

    /**
     * Adds 2 bytes denoting the length of the byte array, and then the byte array itself.
     * Mostly useful when adding strings.
     */
    public BBuf putLenBytes(byte[] bytes) {
        b.putShort((short)bytes.length);
        b.put(bytes);
        return this;
    }

    /**
     * Puts a UTF8-encoded string into the buffer prefixed with the 2-byte length of the string
     * @param s String to put in the buffer
     */
    public BBuf putLenString(String s) {
        return putLenBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads 2 bytes indicating the length of the string, then reads the UTF8-encoded string
     * @return The string
     */
    public String getLenString() {
        int len = b.getShort();
        byte[] bytes = new byte[len];
        b.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
