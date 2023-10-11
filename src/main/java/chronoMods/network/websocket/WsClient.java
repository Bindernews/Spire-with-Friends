package chronoMods.network.websocket;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.val;

import java.net.URI;

public class WsClient {
    @Getter
    private Channel channel;
    private final Handler handler;

    public WsClient(Handler handler) {
        this.handler = handler;
    }

    @SneakyThrows
    public void connect(URI server) {
        if (channel != null) {
            throw new IllegalStateException("Already connected");
        }

        final boolean ssl = "wss".equalsIgnoreCase(server.getScheme());
        final SslContext sslContext;
        if (ssl) {
            sslContext = SslContextBuilder.forClient().build();
        } else {
            sslContext = null;
        }

        EventLoopGroup group = new NioEventLoopGroup();
        val handShaker = WebSocketClientHandshakerFactory.newHandshaker(
                server, WebSocketVersion.V13, "spire-with-friends", false, null);
        val handler = new ClientHandler(handShaker);
        Bootstrap b = new Bootstrap();
        b.group(group);
        b.channel(NioSocketChannel.class);
        b.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ChannelPipeline p = ch.pipeline();
                if (sslContext != null) {
                    p.addLast(sslContext.newHandler(ch.alloc(), server.getHost(), server.getPort()));
                }
                p.addLast(
                        new HttpClientCodec(),
                        new HttpObjectAggregator(4096),
                        WebSocketClientCompressionHandler.INSTANCE,
                        handler);
            }
        });

        try {
            val channel = b.connect(server.getHost(), server.getPort()).sync().channel();
            handler.handshakeFuture.sync();
            this.channel = channel;
            channel.closeFuture().addListener(f -> this.handler.onDisconnect());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public ByteBuf buffer(int capacity) {
        return channel.alloc().buffer(capacity);
    }

    public void send(ByteBuf buf) {
        channel.writeAndFlush(new BinaryWebSocketFrame(buf));
    }

    public void disconnect() {
        channel.writeAndFlush(new CloseWebSocketFrame());
    }

    public boolean isConnected() {
        return channel.isActive();
    }

    public class ClientHandler extends SimpleChannelInboundHandler<Object> {

        private final WebSocketClientHandshaker handshaker;


        private ChannelPromise handshakeFuture;

        public ClientHandler(WebSocketClientHandshaker handshaker) {
            this.handshaker = handshaker;
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
        public void channelInactive(ChannelHandlerContext ctx) {
            // Disconnected
            // TODO
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            Channel ch = ctx.channel();
            if (!handshaker.isHandshakeComplete()) {
                try {
                    handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                    handshakeFuture.setSuccess();
                    handler.onConnect();
                } catch (WebSocketHandshakeException e) {
                    handshakeFuture.setFailure(e);
                }
                return;
            }

            if (msg instanceof FullHttpResponse) {
                throw new IllegalStateException("Unexpected FullHttpResponse: " + msg);
            }

            WebSocketFrame frame = (WebSocketFrame) msg;
            if (frame instanceof BinaryWebSocketFrame) {
                handler.onFrame(frame.content());
            } else if (frame instanceof TextWebSocketFrame) {
                throw new IllegalStateException("Unexpected TextWebSocketFrame");
            } else if (frame instanceof CloseWebSocketFrame) {
                ch.close();
                handler.onDisconnect();
            }
        }
    }

    public interface Handler {
        void onFrame(ByteBuf buf);
        void onConnect();
        void onDisconnect();
    }
}
