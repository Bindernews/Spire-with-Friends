package chronoMods.network.websocket;

import chronoMods.TogetherManager;
import chronoMods.network.Lobby;
import chronoMods.network.RemotePlayer;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class WebsocketLobby extends Lobby {

    private final long lobbyId;
    private long ownerId;

    public WebsocketLobby(WebsocketIntegration service, long lobbyId, long ownerId) {
        super(service);
        this.lobbyId = lobbyId;
        this.ownerId = ownerId;
    }


    @Override
    public String getOwnerName() {
        return null;
    }

    @Override
    public long getOwner() {
        return ownerId;
    }

    @Override
    public boolean isOwner() {
        return TogetherManager.currentUser.isUser(ownerId);
    }

    @Override
    public void newOwner() {

    }

    @Override
    public int getMemberCount() {
        return 0;
    }

    @Override
    public CopyOnWriteArrayList<RemotePlayer> getLobbyMembers() {
        return null;
    }

    @Override
    public String getMemberNameList() {
        return null;
    }

    @Override
    public Object getID() {
        return lobbyId;
    }

    @Override
    public void leaveLobby() {
        wsService().write(new ControlPacket(ControlCode.LeaveLobby, new ControlCode.EmptyData()));
    }

    @Override
    public void setJoinable(boolean toggle) {
        wsService().write(new ControlPacket(ControlCode.SetLobbyFlags,
                new ControlCode.LobbyFlagsData(toggle, null)));
    }

    @Override
    public void setPrivate(boolean toggle) {
        wsService().write(new ControlPacket(ControlCode.SetLobbyFlags,
                new ControlCode.LobbyFlagsData(null, !toggle)));
    }

    @Override
    public void join() {
        wsService().write(new ControlPacket(ControlCode.JoinLobby, new ControlCode.JoinLobbyData(lobbyId)));
    }

    @Override
    public int getCapacity() {
        return WebsocketIntegration.maxPlayerForGameMode(TogetherManager.gameMode);
    }

    @Override
    public String getMetadata(String key) {
        return null;
    }

    @Override
    public void setMetadata(Map<String, String> pairs) {

    }

    /**
     * Returns service, but cast to its actual type.
     */
    private WebsocketIntegration wsService() {
        return (WebsocketIntegration) service;
    }
}
