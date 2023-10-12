package chronoMods.network.websocket;

import chronoMods.TogetherManager;
import chronoMods.network.Integration;
import chronoMods.network.NetworkHelper;
import chronoMods.network.Packet;
import chronoMods.network.RemotePlayer;
import chronoMods.ui.lobby.MainLobbyScreen;
import chronoMods.ui.mainMenu.NewMenuButtons;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.LongMap;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

public class WebsocketIntegration implements Integration {
    public static final Logger LOG = LogManager.getLogger(WebsocketIntegration.class);

    private final URI server;
    private final LongMap<WebsocketPlayer> playerIdMap;
    private final Queue<Packet> packetQueue = new ConcurrentLinkedQueue<>();
    private final ReentrantLock lobbiesLock = new ReentrantLock();

    @Getter
    private Channel channel;

    public WebsocketIntegration(URI server) {
        this.server = server;
        this.playerIdMap = new LongMap<>();
    }

    protected void onConnect() {
        val userName = CardCrawlGame.playerName;
        val packet = new ControlPacket(
                ControlCode.Login,
                new ControlCode.LoginData("unauthenticated", userName));
        channel.writeAndFlush(packet);
    }

    protected void handleDataPacket(ChannelHandlerContext ctx, DataPacket pkt) {
        WebsocketPlayer player = playerIdMap.get(pkt.getPlayerId());
        if (player == null) {
            return;
        }
        ByteBuffer data = ByteBuffer.allocateDirect(pkt.content().readableBytes());
        pkt.content().readBytes(data);
        data.rewind();
        packetQueue.add(new Packet(player, data));
    }

    protected void handleControlPacket(ChannelHandlerContext ctx, ControlPacket pkt) {
        switch (pkt.getKind()) {
            case None:
                break;
            case AddPlayer: {
                val data = pkt.<ControlCode.AddPlayerData>getData();
                playerIdMap.put(data.playerId, new WebsocketPlayer(data.playerId, data.userName));
            } break;
            case RemovePlayer: {
                val data = pkt.<ControlCode.RemovePlayerData>getData();
                playerIdMap.remove(data.playerId);
            } break;
            case ListLobbiesReq:
                // TODO report protocol error, client should not receive this
                break;
            case ListLobbiesRes: {
                val data = pkt.<ControlCode.ListLobbiesResData>getData();
                val newLobbies = data.createLobbies(this);
                lobbiesLock.lock();
                try {
                    NetworkHelper.lobbies.clear();
                    NetworkHelper.lobbies.addAll(newLobbies);
                } finally {
                    lobbiesLock.unlock();
                }
                NewMenuButtons.lobbyScreen.createFreshGameList();
            } break;
            case Login:
                // TODO report protocol error
                break;
        }
    }

    @Override
    public void initialize() {
        connect(server);
        WebsocketPlayer.service = this;
    }

    @Override
    public boolean isInitialized() {
        return channel != null;
    }

    @Override
    public RemotePlayer makeCurrentUser() {
        return null;
    }

    @Override
    public void updateLobbyData() {

    }

    @Override
    public void createLobby(TogetherManager.mode gameMode) {
        val maxPlayers = maxPlayerForGameMode(gameMode);
        write(new ControlPacket(ControlCode.CreateLobby,
                new ControlCode.CreateLobbyData(maxPlayers, gameMode.name())));
    }

    @Override
    public void setLobbyPrivate(boolean priv) {

    }

    @Override
    public void getLobbies() {
        channel.writeAndFlush(new ControlPacket(ControlCode.ListLobbiesReq, new ControlCode.EmptyData()));
    }

    @Override
    public void getPacket(Packet packet) {
        Packet next = packetQueue.poll();
        if (next == null) {
            packet.clear();
        } else {
            packet.set(next.player(), next.data());
        }
    }

    @Override
    public void sendPacket(ByteBuffer data) {
        val playerId = TogetherManager.currentUser.getAccountID();
        channel.writeAndFlush(DataPacket.fromNioBuffer(channel, playerId, data));
    }

    @Override
    public void messageUser(RemotePlayer player) {
        LOG.error("Unsupported operation.");
    }

    @Override
    public void dispose() {
        if (channel != null && channel.isOpen()) {
            channel.writeAndFlush(new CloseWebSocketFrame());
        }
        if (WebsocketPlayer.service == this) {
            WebsocketPlayer.service = null;
        }
    }

    @Override
    public Texture getLogo() {
        return null;
    }

    /**
     * Shortcut for {@code getChannel().writeAndFlush(message)}.
     * @param message Message to send
     */
    public void write(Object message) {
        channel.writeAndFlush(message);
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
        val handler = new MyCodecs.WsClientHandler(server, "spire-with-friends", null);
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
                        handler,
                        new MyCodecs.Codec(true));
                p.addLast(new PacketHandler());
            }
        });

        try {
            val channel = b.connect(server.getHost(), server.getPort()).sync().channel();
            handler.getHandshakeFuture().sync();
            this.channel = channel;
            onConnect();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected class PacketHandler extends SimpleChannelInboundHandler<MyCodecs.WsPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, MyCodecs.WsPacket pkt) {
            if (pkt instanceof ControlPacket) {
                handleControlPacket(ctx, (ControlPacket) pkt);
            } else if (pkt instanceof DataPacket) {
                handleDataPacket(ctx, (DataPacket) pkt);
            } else {
                throw new RuntimeException(String.format("Unknown packet type: %s", pkt.getClass().getName()));
            }
        }
    }

    public static int maxPlayerForGameMode(TogetherManager.mode mode) {
        switch (mode) {
            case Coop:
                return 6;
            case Bingo:
            case Versus:
                return 200;
            case Normal:
            default:
                return 1;
        }
    }
}
