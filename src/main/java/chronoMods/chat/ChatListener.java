package chronoMods.chat;

import chronoMods.network.RemotePlayer;

public interface ChatListener {

    /**
     * Called when the player has pressed the SEND key on a chat message, but before it's actually sent.
     *
     * @param message The chat message
     * @return The new chat message, or the empty string to cancel sending the message
     */
    default String preChatSend(String message) {
        return message;
    }

    /**
     * Called when a message is received from a remote player, after it has been added to the chat log.
     *
     * @param sender Player who sent the message
     * @param message The message that was sent
     */
    default void postChatReceive(RemotePlayer sender, String message) {}
}
