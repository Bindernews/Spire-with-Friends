package chronoMods.network.websocket;

import chronoMods.TogetherManager;
import chronoMods.network.Integration;
import chronoMods.network.Packet;
import chronoMods.network.RemotePlayer;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.LongMap;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import io.netty.buffer.ByteBuf;
import lombok.val;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WebsocketIntegration implements Integration, WsClient.Handler {
    private static final Logger LOG = LogManager.getLogger(WebsocketIntegration.class);

    private final URI server;
    private final WsClient client;
    private final LongMap<WebsocketPlayer> playerIdMap;
    private final Queue<Packet> packetQueue;

    public WebsocketIntegration(URI server) {
        this.server = server;
        this.playerIdMap = new LongMap<>();
        this.packetQueue = new ConcurrentLinkedQueue<>();
        this.client = new WsClient(this);
    }

    @Override
    public void onFrame(ByteBuf buf) {
        // NOTE: Runs in Netty IO thread
        // Read the sender ID
        final long senderId = buf.readLong();
        WebsocketPlayer player = playerIdMap.get(senderId);
        if (player == null) {
            return;
        }
        ByteBuffer data = ByteBuffer.allocateDirect(buf.readableBytes());
        buf.readBytes(data);
        data.rewind();
        packetQueue.add(new Packet(player, data));
    }

    @Override
    public void onConnect() {
        val userName = CardCrawlGame.playerName;
        val buf = client.buffer(10 + 1 + 2 + userName.length());
        buf.writeLong(PKID_CONTROL_OUT).writeShort(ControlCode.Login.toShort());
        buf.writeByte(0); // login method 0
        bufWriteString(buf, CardCrawlGame.playerName);
    }

    @Override
    public void onDisconnect() {

    }

    private void handleControlFrame(ByteBuf buf, ControlCode code) {
        switch (code) {
            case None:
                break;
            case AddPlayer: {
                long playerId = buf.readLong();
                String userName = bufReadString(buf);
                playerIdMap.put(playerId, new WebsocketPlayer(playerId, userName));
            } break;
            case RemovePlayer: {
                long playerId = buf.readLong();
                playerIdMap.remove(playerId);
            } break;
        }
    }

    private static String bufReadString(ByteBuf b) {
        val length = (int) b.readShort();
        val bytes = new byte[length];
        b.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void bufWriteString(ByteBuf b, String s) {
        val bytes = s.getBytes(StandardCharsets.UTF_8);
        b.writeShort((short) bytes.length);
        b.writeBytes(bytes);
    }

    @Override
    public void initialize() {
        client.connect(server);
        WebsocketPlayer.service = this;
    }

    @Override
    public boolean isInitialized() {
        return false;
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

    }

    @Override
    public void setLobbyPrivate(boolean priv) {

    }

    @Override
    public void getLobbies() {

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
        sendDataId(data, TogetherManager.currentUser.getAccountID());
    }

    @Override
    public void messageUser(RemotePlayer player) {
        LOG.error("Unsupported operation.");
    }

    private void sendDataId(ByteBuffer data, long destinationId) {
        val nettyBuf = client.buffer(data.capacity() + 8);
        nettyBuf.writeLong(destinationId);
        nettyBuf.writeBytes(data);
        client.send(nettyBuf);
    }

    @Override
    public void dispose() {
        client.disconnect();
    }

    @Override
    public Texture getLogo() {
        return null;
    }


}
