package chronoMods.network.websocket;

import chronoMods.TogetherManager;
import chronoMods.network.Lobby;
import chronoMods.network.RemotePlayer;
import lombok.Getter;
import lombok.Setter;
import lombok.val;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class WebsocketLobby extends Lobby {

    @Getter
    private final long lobbyId;
    private long ownerId;
    @Setter
    private int maxPlayers;

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
        // TODO
    }

    @Override
    public int getMemberCount() {
        return players.size();
    }

    @Override
    public CopyOnWriteArrayList<RemotePlayer> getLobbyMembers() {
        return players;
    }

    @Override
    public String getMemberNameList() {
        val sb = new StringBuilder();
        for (RemotePlayer p : players) {
            sb.append("\t");
            sb.append(p.userName);
        }
        return sb.substring(1);
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
        val playerId = new long[]{ TogetherManager.currentUser.getAccountID() };
        wsService().write(new ControlPacket(ControlCode.JoinLobby,
                new ControlCode.JoinLobbyData(lobbyId, playerId)));
    }

    @Override
    public int getCapacity() {
        return maxPlayers;
    }

    @Override
    public String getMetadata(String key) {
        // TODO
        return null;
    }

    @Override
    public void setMetadata(Map<String, String> pairs) {
        // TODO
    }

    /**
     * Add players with the given IDs to this lobby.
     */
    protected void addPlayers(long[] playerIds) {
        val toAdd = wsService().getPlayersById(playerIds);
        val playerSet = new HashSet<>(players);
        // Remove duplicates
        toAdd.forEach(playerSet::remove);
        playerSet.addAll(toAdd);
        // Update players
        players.clear();
        players.addAll(playerSet);
    }

    /**
     * Remove players with the given IDs from this lobby.
     */
    protected void removePlayers(long[] playerIds) {
        val toRemove = wsService().getPlayersById(playerIds);
        val playerSet = new HashSet<>(players);
        toRemove.forEach(playerSet::remove);
        // Update players
        players.clear();
        players.addAll(playerSet);
    }

    /**
     * Returns service, but cast to its actual type.
     */
    private WebsocketIntegration wsService() {
        return (WebsocketIntegration) service;
    }
}
