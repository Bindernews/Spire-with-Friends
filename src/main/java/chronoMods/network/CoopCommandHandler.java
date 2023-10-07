package chronoMods.network;

import chronoMods.TogetherManager;
import chronoMods.coop.CoopCommandEvent;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.IntMap;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import lombok.val;
import org.apache.logging.log4j.Logger;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;

import static chronoMods.network.NetworkHelper.service;

public class CoopCommandHandler {
    private static final Logger log = TogetherManager.logger;

    /**
     * Code for command proposal packets.
     */
    public static final short PACKET_PROPOSE = 1;

    /**
     * Code for command selection (accept/reject) packets.
     */
    public static final short PACKET_SELECT = 2;


    /**
     * Maximum number of commands a player may send before nextEventId will loop back to initialEventId.
     */
    private static final int COMMAND_ID_BLOCK_SIZE = 1 << 26;

    /**
     * Localized text strings used by co-op commands
     */
    public static final Map<String, String> TEXT =
            CardCrawlGame.languagePack.getUIString("CoopCommand").TEXT_DICT;

    /**
     * Initial event ID. Assigned based on the player index in the lobby,
     * this gives each player a section of 2^26 command IDs before looping back to the beginning of their ID block.
     */
    private static int initialEventId = -1;

    /**
     * The next event ID.
     */
    private static int nextGlobalEventId = 1;

    /**
     * List of currently active commands events.
     */
    public static ArrayList<CoopCommandEvent> events = new ArrayList<>();

    /**
     * When displaying the events in chat, we assign local numbers for players to use when
     * sending accept/reject messages.
     */
    private static int nextLocalEventId = 1;

    /**
     * Map of local event IDs to their actual event IDs.
     */
    private static IntMap<Integer> localToGlobalEventIdMap = new IntMap<>();

    /**
     * Outgoing command event, may be {@code null}.
     */
    private static CoopCommandEvent outgoingEvent = null;

    /**
     * Outgoing packet mode. Same as the PACKET_* constants, or -1 for invalid value.
     */
    private static short outgoingMode = -1;

    public static void handlePacket(Packet packet) {
        short kind = packet.data().getShort(4);
        BBuf.pos(packet.data(), 6);
        switch (kind) {
            case PACKET_PROPOSE:
                handleProposePacket(packet);
                break;
            case PACKET_SELECT:
                handleSelectPacket(packet);
                break;
            default:
                log.warn("invalid packet kind {}", Integer.toHexString(kind));
                break;
        }
    }

    /**
     * Decode the packet with the given choice data and update the appropriate event
     */
    private static void handleSelectPacket(Packet packet) {
        val data = packet.data();
        CoopCommandEvent event = getEventWithId(data.getInt());
        if (event == null) {
            return;
        }
        boolean choice = data.get() == 1;
        event.registerChoice(choice, packet.player());
    }

    /**
     * Handle a "propose" packet, as created with {@link CoopCommandEvent#encodeProposePacket}.
     * This method has lots of side effects, be careful.
     */
    private static void handleProposePacket(Packet packet) {
        try {
            val data = packet.data();
            int eventId = data.getInt();
            String target = BBuf.getLenString(data);
            String command = BBuf.getLenString(data);
            // Create and add event
            CoopCommandEvent event = new CoopCommandEvent(eventId, target, command, packet.player());
            // Check for duplicates
            if (getEventWithId(eventId) != null) {
                // TODO add more detailed error message
                log.warn("Duplicate event ID error!");
                return;
            }
            // Add to list of events
            events.add(event);
            // Mark proposer as agreeing to the command
            event.registerChoice(true, packet.player());
            // Generate local event ID
            int localEid = nextLocalEventId;
            nextLocalEventId++;
            localToGlobalEventIdMap.put(localEid, event.id);
            // Show chat message informing the player of their options
            String chatMsg1 = String.format(TEXT.get("player proposing"), event.proposer.userName, event.getCommand());
            TogetherManager.chatScreen.addMsg(chatMsg1, Color.BLUE);
//            String chatMsg2 = String.format(TEXT.get("yes no help"), localEid);
//            TogetherManager.chatScreen.addMsg(chatMsg2, Color.BLUE);

            // TODO actually handle chat input
            // TODO temporarily we're auto-agreeing
            sendChoiceLocalId(localEid, true);
        } catch (BufferUnderflowException e) {
            TogetherManager.logger.warn(e);
        }
    }

    /**
     * Begins a command proposal, creating a new event with a unique ID and sending the request to other players.
     * @param executor Who will be running the command
     * @param command command to propose
     */
    public static void proposeNewCommand(String executor, String command) {
        // Check that we CAN send commands
        if (nextGlobalEventId == -1) {
            return;
        }

        if (executor.equalsIgnoreCase(TEXT.get("me"))) {
            executor = TogetherManager.getCurrentUser().userName;
        }
        CoopCommandEvent event = new CoopCommandEvent(
                nextGlobalEventId, executor, command, TogetherManager.getCurrentUser());
        nextGlobalEventId++;
        // Add to event list, but don't add local event ID since we automatically agree with it
        events.add(event);

        outgoingEvent = event;
        outgoingMode = PACKET_PROPOSE;
        NetworkHelper.sendData(NetworkHelper.dataType.CoopCommand);

        // Auto-choose true if we're proposing the command
        event.registerChoice(true, TogetherManager.currentUser);
    }

    /**
     * Takes the local event ID printed in the chat and sends a choice for that event.
     *
     * @return if the choice was sent or not
     */
    public static boolean sendChoiceLocalId(int localEid, boolean choice) {
        val event = getEventWithId(localToGlobalEventIdMap.get(localEid));
        if (event == null || event.hasChosen) {
            return false;
        }
        event.registerChoice(choice, TogetherManager.currentUser);

        // Send data
        outgoingEvent = event;
        outgoingMode = PACKET_SELECT;
        NetworkHelper.sendData(NetworkHelper.dataType.CoopCommand);

        return true;
    }

    /**
     * Returns the event with the given event ID, or null if not found
     * @param eventId Event ID to search for
     */
    public static CoopCommandEvent getEventWithId(int eventId) {
        return events.stream().filter((e) -> e.id == eventId).findFirst().orElse(null);
    }

    public static ByteBuffer encodePacket() {
        ByteBuffer data;
        switch (outgoingMode) {
            case PACKET_PROPOSE:
                data = outgoingEvent.encodeProposePacket();
                break;
            case PACKET_SELECT:
                data = outgoingEvent.encodeChoicePacket();
                break;
            default:
                data = ByteBuffer.allocateDirect(4);
                break;
        }
        // Reset outgoing data
        outgoingEvent = null;
        outgoingMode = -1;
        return data;
    }

    /**
     * Called when the players actually START a new run (e.g. DungeonPostInitialize).
     */
    public static void startGame() {
        // Get index of player in lobby
        int playerIndex = TogetherManager.currentLobby.getLobbyMembers().indexOf(TogetherManager.getCurrentUser());
        initialEventId = playerIndex * COMMAND_ID_BLOCK_SIZE;
        nextGlobalEventId = initialEventId;
    }

    /**
     * Reset the list of events, event IDs, etc.
     */
    public static void reset() {
        events.clear();
        initialEventId = -1;
        nextGlobalEventId = -1;
    }

    // See InfoPopupPatches for where these are called

    /**
     * Global UI update function called via patch
     */
    public static void update() {}

    /**
     * Global UI render callback
     */
    public static void render(SpriteBatch sb) {}
}
