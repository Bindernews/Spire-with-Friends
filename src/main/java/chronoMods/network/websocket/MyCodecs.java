package chronoMods.network.websocket;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.LongSerializationPolicy;
import io.netty.channel.*;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.*;
import lombok.AllArgsConstructor;
import lombok.val;

import java.net.URI;
import java.util.List;
import java.util.function.BiConsumer;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MyCodecs {
    private static final Gson gson = new GsonBuilder()
            .setLongSerializationPolicy(LongSerializationPolicy.STRING)
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    public static final long PKID_CONTROL_OUT = Long.MIN_VALUE;
    public static final long PKID_CONTROL_IN = PKID_CONTROL_OUT + 1;

    @AllArgsConstructor
    public static class Codec extends MessageToMessageCodec<BinaryWebSocketFrame, WsPacket> {
        private final boolean client;

        @Override
        protected void encode(ChannelHandlerContext ctx, WsPacket packet0, List<Object> out) {
            if (packet0 instanceof ControlPacket) {
                val packet = (ControlPacket) packet0;
                val dataBytes = gson.toJson(packet.<Object>getData()).getBytes(UTF_8);
                val buf = ctx.alloc().buffer(8 + 2 + 2 + dataBytes.length);
                val kindOrd = (short) packet.getKind().ordinal();
                buf.writeLong(client ? PKID_CONTROL_OUT : PKID_CONTROL_IN).writeShort(kindOrd);
                buf.writeShort((short) dataBytes.length).writeBytes(dataBytes);
                buf.retain();
                out.add(new BinaryWebSocketFrame(buf));
            } else if (packet0 instanceof DataPacket) {
                val packet = (DataPacket) packet0;
                packet.content().setLong(0, packet.getPlayerId());
                out.add(new BinaryWebSocketFrame(packet.content()));
            }
        }

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
            } else {
                val packet = new DataPacket(buf.retain());
                packet.setPlayerId(senderId);
                out.add(packet);
            }
        }
    }

    public static class WsClientHandler extends SimpleChannelInboundHandler<Object> {
        private final WebSocketClientHandshaker handshaker;
        private ChannelPromise handshakeFuture;

        public WsClientHandler(URI remote, String subprotocol, HttpHeaders customHeaders) {
            handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                    remote, WebSocketVersion.V13,
                    subprotocol, false, customHeaders);
        }

        public ChannelFuture getHandshakeFuture() {
            return handshakeFuture;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            handshakeFuture = ctx.newPromise();
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            handshaker.handshake(ctx.channel());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            Channel ch = ctx.channel();
            if (!handshaker.isHandshakeComplete()) {
                try {
                    handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                    handshakeFuture.setSuccess();
                } catch (WebSocketHandshakeException e) {
                    handshakeFuture.setFailure(e);
                }
                return;
            }

            if (msg instanceof FullHttpResponse) {
                throw new IllegalStateException("Unexpected FullHttpResponse: " + msg);
            }

            WebSocketFrame frame = (WebSocketFrame) msg;
            if (frame instanceof TextWebSocketFrame) {
                throw new IllegalStateException("Unexpected TextWebSocketFrame");
            } else if (frame instanceof CloseWebSocketFrame) {
                ch.close();
            }
        }
    }

    /**
     * Tag for packet classes.
     */
    public interface WsPacket {}
}
