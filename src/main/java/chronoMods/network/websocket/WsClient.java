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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class WsClient {
    private static final Logger LOG = LogManager.getLogger(WsClient.class);

    @Getter
    private Channel channel;
    private final Consumer<ByteBuf> onFrame;

    public WsClient(Consumer<ByteBuf> onFrame) {
        this.onFrame = onFrame;
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
        try {
            channel.writeAndFlush(new CloseWebSocketFrame());
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            LOG.error(e.getMessage(), e);
        }
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
                onFrame.accept(frame.content());
            } else if (frame instanceof TextWebSocketFrame) {
                throw new IllegalStateException("Unexpected TextWebSocketFrame");
            } else if (frame instanceof CloseWebSocketFrame) {
                ch.close();
            }
        }
    }
}
