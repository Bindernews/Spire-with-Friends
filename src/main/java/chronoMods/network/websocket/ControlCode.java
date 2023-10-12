package chronoMods.network.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.val;

import java.util.ArrayList;

/**
 * Control codes for communication with the websocket server.
 * Also details the format for each packet.
 * <br>
 * In this format, strings are always UTF-8, and prefixed with the 2-byte length of the string.
 */
public enum ControlCode implements MyCodecs.WsPacket {
    None,
    AddPlayer,
    RemovePlayer,
    CreateLobby,
    ListLobbiesReq,
    ListLobbiesRes,
    JoinLobby,
    LeaveLobby,
    SetLobbyFlags,
    Login,
    ;

    /**
     * Returns the POJO data class to use for encoding this control code's data.
     */
    @SuppressWarnings("DuplicateBranchesInSwitch")
    public Class<?> getJsonClass() {
        switch (this) {
            case AddPlayer:
                return AddPlayerData.class;
            case RemovePlayer:
                return RemovePlayerData.class;
            case CreateLobby:
                return CreateLobbyData.class;
            case ListLobbiesReq:
                return EmptyData.class;
            case ListLobbiesRes:
                return ListLobbiesResData.class;
            case JoinLobby:
                return JoinLobbyData.class;
            case LeaveLobby:
                return EmptyData.class;
            case SetLobbyFlags:
                return LobbyFlagsData.class;
            case Login:
                return LoginData.class;
            case None:
            default:
                return EmptyData.class;
        }
    }

    /**
     * Returns the control code with the given ordinal value, or null if out of range.
     *
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

    public static class EmptyData {}

    /**
     * POJO for Login control code
     */
    @Data @AllArgsConstructor
    public static class LoginData {
        public String method;
        public String username;
    }

    @Data @AllArgsConstructor
    public static class AddPlayerData {
        public long playerId;
        public String userName;
    }

    @Data @AllArgsConstructor
    public static class RemovePlayerData {
        public long playerId;
    }

    @Data @AllArgsConstructor
    public static class CreateLobbyData {
        public int maxPlayers;
        public String mode;
    }

    @Data @AllArgsConstructor
    public static class ListLobbiesResData {
        public long[] lobbyIds;
        public long[] ownerIds;

        public ArrayList<WebsocketLobby> createLobbies(WebsocketIntegration service) {
            val out = new ArrayList<WebsocketLobby>();
            for (int i = 0; i < lobbyIds.length; i++) {
                out.add(new WebsocketLobby(service, lobbyIds[i], ownerIds[i]));
            }
            return out;
        }
    }

    @Data @AllArgsConstructor
    public static class JoinLobbyData {
        public long lobbyId;
    }

    @Data @AllArgsConstructor
    public static class LobbyFlagsData {
        // These may be null, which indicates the flag is unchanged
        public Boolean canJoin;
        public Boolean isPublic;
    }
}
