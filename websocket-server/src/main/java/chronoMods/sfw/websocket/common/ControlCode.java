package chronoMods.sfw.websocket.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;
import lombok.val;

import java.util.ArrayList;
import java.util.Arrays;

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
    DeleteLobby,
    ListLobbiesReq,
    ListLobbiesRes,
    JoinLobby,
    LeaveLobby,
    SetLobbyFlags,
    LoginReq,
    LoginRes,
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
            case DeleteLobby:
                return JoinLobbyData.class;
            case ListLobbiesReq:
                return EmptyData.class;
            case ListLobbiesRes:
                return ListLobbiesResData.class;
            case JoinLobby:
                return JoinLobbyData.class;
            case LeaveLobby:
                return JoinLobbyData.class;
            case SetLobbyFlags:
                return LobbyFlagsData.class;
            case LoginReq:
                return LoginData.class;
            case LoginRes:
                return LoginResData.class;
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

    @Value
    public static class LoginResData {
        long playerId;
        String username;
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
        /**
         * When sending as request, lobby ID is 0. When receiving from the server, this will be filled in.
         */
        private long lobbyId;
        /**
         * Request: value = 0
         */
        private long ownerId;
        public int maxPlayers;
        public String mode;
    }

    @Data @AllArgsConstructor
    public static class ListLobbiesResData {
        public long[] lobbyIds;
        public long[] ownerIds;
    }

    @Data @AllArgsConstructor
    public static class JoinLobbyData {
        public long lobbyId;
        public long[] playerIds;

        /**
         * Search {@code playerIds} for the given player ID.
         * @param playerId player ID to search for
         * @return true if in the list of player IDs
         */
        public boolean hasId(final long playerId) {
            return Arrays.stream(playerIds).anyMatch(v -> v == playerId);
        }
    }

    @Data @AllArgsConstructor
    public static class LobbyFlagsData {
        // These may be null, which indicates the flag is unchanged
        public Boolean canJoin;
        public Boolean isPublic;
    }
}
