package chronoMods.network.websocket;

import chronoMods.TogetherManager;
import chronoMods.network.Integration;
import chronoMods.network.NetworkHelper;
import chronoMods.network.Packet;
import chronoMods.network.RemotePlayer;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class WebsocketIntegration implements Integration {
    public static final Logger LOG = LogManager.getLogger(WebsocketIntegration.class);

    private final URI server;
    @Getter
    private final LongMap<WebsocketPlayer> playerIdMap = new LongMap<>();
    private final LongMap<WebsocketLobby> lobbyIdMap = new LongMap<>();
    private final Queue<Packet> packetQueue = new ConcurrentLinkedQueue<>();
    private final ReentrantLock lobbiesLock = new ReentrantLock();

    @Getter
    private Channel channel;

    public WebsocketIntegration(URI server) {
        this.server = server;
    }

    protected void onConnect() {
        val userName = CardCrawlGame.playerName;
        val packet = new ControlPacket(
                ControlCode.LoginReq,
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
            case CreateLobby: {
                val data = pkt.<ControlCode.CreateLobbyData>getData();
                val lobby = new WebsocketLobby(this, data.getLobbyId(), data.getOwnerId());
                lobby.setMaxPlayers(data.getMaxPlayers());
                lobbyIdMap.put(lobby.getLobbyId(), lobby);
            } break;
            case DeleteLobby: {
                val data = pkt.<ControlCode.CreateLobbyData>getData();
                val lobby = lobbyIdMap.get(data.getLobbyId());
                lobbyIdMap.remove(data.getLobbyId());
                if (TogetherManager.currentLobby == lobby) {
                    setCurrentLobby(null);
                }
            } break;
            case ListLobbiesReq:
                throwBadClientPacket(ControlCode.ListLobbiesReq);
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
            case JoinLobby: {
                // Handle incoming lobby join information
                val data = pkt.<ControlCode.JoinLobbyData>getData();
                val lobby = checkedGetLobby(data.getLobbyId());
                // If current player ID is part of the lobby data, we have been joined into this lobby
                if (data.hasId(TogetherManager.currentUser.getAccountID())) {
                    setCurrentLobby(lobby);
                }
                // Regardless, update the lobby with list of player IDs
                lobby.addPlayers(data.getPlayerIds());
            } break;
            case LeaveLobby: {
                val data = pkt.<ControlCode.JoinLobbyData>getData();
                val lobby = checkedGetLobby(data.getLobbyId());
                // If we are in the leave lobby list AND currently in the lobby, then leave
                if (data.hasId(TogetherManager.currentUser.getAccountID()) && TogetherManager.currentLobby == lobby) {
                    setCurrentLobby(null);
                }
                // Regardless, update the lobby
                lobby.removePlayers(data.getPlayerIds());
            } break;
            case LoginReq:
                throwBadClientPacket(ControlCode.LoginReq);
                break;
        }
    }

    private void throwBadClientPacket(ControlCode code) {
        throw new SwfProtocolException(String.format("client received non-client code '%s'", code.name()));
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
                new ControlCode.CreateLobbyData(0, 0, maxPlayers, gameMode.name())));
    }

    @Override
    public void setLobbyPrivate(boolean priv) {
        val lobby = (WebsocketLobby) TogetherManager.currentLobby;
        if (lobby != null) {
            lobby.setPrivate(priv);
        }
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

    /**
     * Returns a list of players with the given IDs. Any player not found will
     * not be returned, but it will not be an error.
     * @param playerIds List of player IDs to search for
     */
    public List<WebsocketPlayer> getPlayersById(long[] playerIds) {
        return Arrays.stream(playerIds)
                .mapToObj(playerIdMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private WebsocketLobby checkedGetLobby(long lobbyId) {
        val lobby = lobbyIdMap.get(lobbyId);
        if (lobby == null) {
            throw new SwfProtocolException(String.format("unknown lobby ID x%x", lobbyId));
        }
        return lobby;
    }

    private void setCurrentLobby(WebsocketLobby lobby) {
        if (TogetherManager.currentLobby != lobby) {
            TogetherManager.currentLobby = lobby;
            // TODO there's probably other stuff I have to do to change lobby
        }
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
