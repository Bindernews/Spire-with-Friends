package chronoMods.network.websocket;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.LongSerializationPolicy;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@Getter
@AllArgsConstructor
public class ControlPacket {
    private static final Logger LOG = LogManager.getLogger(ControlPacket.class);

    public static final long PKID_CONTROL_OUT = Long.MIN_VALUE;
    public static final long PKID_CONTROL_IN = PKID_CONTROL_OUT + 1;

    private static final Gson gson = new GsonBuilder()
            .setLongSerializationPolicy(LongSerializationPolicy.STRING)
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    @Setter
    private ControlCode kind;

    @Setter
    private Object data;

    @AllArgsConstructor
    public static class Encoder extends MessageToMessageEncoder<ControlPacket> {
        private final boolean client;

        @Override
        protected void encode(ChannelHandlerContext ctx, ControlPacket packet, List<Object> out) {
            val dataBytes = gson.toJson(packet.getData()).getBytes(UTF_8);
            val buf = ctx.alloc().buffer(8 + 2 + 2 + dataBytes.length);
            val kindOrd = (short) packet.getKind().ordinal();
            buf.writeLong(client ? PKID_CONTROL_OUT : PKID_CONTROL_IN).writeShort(kindOrd);
            buf.writeShort((short) dataBytes.length).writeBytes(dataBytes);
            buf.retain();
            out.add(new BinaryWebSocketFrame(buf));
        }
    }

    @AllArgsConstructor
    public static class Decoder extends MessageToMessageDecoder<BinaryWebSocketFrame> {
        private final boolean client;

        @Override
        protected void decode(ChannelHandlerContext ctx, BinaryWebSocketFrame frame, List<Object> out) {
            val buf = frame.content();
            val senderId = buf.getLong(0);
            if ((client && senderId == PKID_CONTROL_IN) || (!client && senderId == PKID_CONTROL_OUT)) {
                val kind = ControlCode.fromOrdinal(buf.getShort(8));
                if (kind == null) {
                    throw new RuntimeException(String.format("Invalid control code %d", buf.getShort(8)));
                }
                // Read string
                val strLen = (int) buf.readerIndex(10).readShort();
                val strBytes = new byte[strLen];
                buf.readBytes(strBytes);
                val strReal = new String(strBytes, UTF_8);
                val pojoData = gson.fromJson(strReal, kind.getJsonClass());
                out.add(new ControlPacket(kind, pojoData));
            }
        }
    }

    /**
     * Control codes for communication with the websocket server.
     * Also details the format for each packet.
     * <br>
     * In this format, strings are always UTF-8, and prefixed with the 2-byte length of the string.
     */
    public enum ControlCode {
        None,
        AddPlayer,
        RemovePlayer,
        Login,
        ;

        /**
         * Returns the POJO data class to use for encoding this control code's data.
         */
        public Class<?> getJsonClass() {
            switch (this) {
                // TODO add and remove player classes
                case Login: return LoginData.class;
                case None:
                default:
                    return String.class;
            }
        }

        /**
         * Returns the control code with the given ordinal value, or null if out of range.
         * @param ord ordinal index
         * @return ControlCode or null
         */
        public static ControlCode fromOrdinal(int ord) {
            if (ord < 0 || ord >= values().length) {
                return null;
            } else {
                return values()[ord];
            }
        }
    }

    /**
     * POJO for Login control code
     */
    public static class LoginData {
        public String method;
        public String username;
    }
}
