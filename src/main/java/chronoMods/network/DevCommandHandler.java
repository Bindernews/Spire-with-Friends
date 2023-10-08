package chronoMods.network;

import chronoMods.TogetherManager;
import chronoMods.chat.ChatListener;
import chronoMods.utilities.DevCommandEvent;
import chronoMods.ui.hud.InfoPopup;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.IntMap;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;

import static chronoMods.TogetherManager.chatScreen;

/**
 * Handles creating, tracking, choosing, networking, etc. for the {@code sfrun} dev command,
 * and the ability to run dev console commands during a multi-player run in general.
 */
public class DevCommandHandler implements ChatListener {
    private static final Logger log = LogManager.getLogger(DevCommandHandler.class);

    /**
     * ID for command proposal packets.
     */
    public static final short PACKET_PROPOSE = 1;

    /**
     * ID for command selection (accept/reject) packets.
     */
    public static final short PACKET_SELECT = 2;


    /**
     * Maximum number of commands a player may send before nextEventId will loop back to initialEventId.
     * This allows for up to 32 players to each have a unique global event ID block.
     */
    private static final int COMMAND_ID_BLOCK_SIZE = 1 << 26;

    /**
     * Localized text strings used by co-op commands
     */
    public static final Strings TEXT = Strings.fromMap(
            CardCrawlGame.languagePack.getUIString("CoopCommand").TEXT_DICT);

    private static final InfoPopup errorPopup = new InfoPopup();

    @Getter
    private static final DevCommandHandler inst = new DevCommandHandler();

    /**
     * Initial event ID. Assigned based on the player index in the lobby,
     * this gives each player a section of 2^26 command IDs before looping back to the beginning of their ID block.
     */
    private int initialEventId = -1;

    /**
     * The next event ID.
     */
    private int nextGlobalEventId = 1;

    /**
     * List of currently active commands events.
     */
    public ArrayList<DevCommandEvent> events = new ArrayList<>();

    /**
     * When displaying the events in chat, we assign local numbers for players to use when
     * sending accept/reject messages.
     */
    private int nextLocalEventId = 1;

    /**
     * Map of local event IDs to their actual event IDs.
     */
    private final IntMap<Integer> localToGlobalEventIdMap = new IntMap<>();

    /**
     * Outgoing command event, may be {@code null}.
     */
    private DevCommandEvent outgoingEvent = null;

    /**
     * Outgoing packet mode. Same as the PACKET_* constants, or -1 for invalid value.
     */
    private short outgoingMode = -1;

    /**
     * If true, proposed commands will automatically be accepted.
     */
    @Getter @Setter
    private boolean autoAccept = true;


    public void handlePacket(Packet packet) {
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
                log.error("invalid packet kind {}", Integer.toHexString(kind));
                break;
        }
    }

    /**
     * Decode the packet with the given choice data and update the appropriate event
     */
    private void handleSelectPacket(Packet packet) {
        val data = packet.data();
        DevCommandEvent event = getEventWithId(data.getInt());
        if (event == null) {
            return;
        }
        boolean choice = data.get() == 1;
        event.registerChoice(choice, packet.player());
    }

    /**
     * Handle a "propose" packet, as created with {@link DevCommandEvent#encodeProposePacket}.
     * This method has lots of side effects, be careful.
     */
    private void handleProposePacket(Packet packet) {
        try {
            val data = packet.data();
            int eventId = data.getInt();
            String target = BBuf.getLenString(data);
            String command = BBuf.getLenString(data);
            // Create and add event
            DevCommandEvent event = new DevCommandEvent(eventId, target, command, packet.player());
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
            event.localId = localEid;
            // Show chat message informing the player of their options
            String chatMsg1 = String.format(TEXT.player_proposing, event.proposer.userName, event.getCommand());
            chatScreen.addMsg(chatMsg1, Color.WHITE);
            // If autoAccept is on, don't bother asking for chat input.
            if (autoAccept) {
                sendChoiceLocalId(localEid, true);
            } else {
                String chatMsg2 = String.format(TEXT.yes_no_help, localEid);
                chatScreen.addMsg(chatMsg2, Color.BLUE);
            }
        } catch (BufferUnderflowException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * Begins a command proposal, creating a new event with a unique ID and sending the request to other players.
     * @param executor Who will be running the command
     * @param command command to propose
     */
    public void proposeNewCommand(String executor, String command) {
        // Check that we CAN send commands
        if (nextGlobalEventId == -1) {
            return;
        }

        // If we want to run a command for ourselves, then do that
        if (executor.equalsIgnoreCase(TEXT.me)) {
            executor = TogetherManager.getCurrentUser().userName;
        }
        DevCommandEvent event = new DevCommandEvent(
                nextGlobalEventId, executor, command, TogetherManager.getCurrentUser());
        // Update global event ID
        nextGlobalEventId++;
        if (nextGlobalEventId >= initialEventId + COMMAND_ID_BLOCK_SIZE) {
            nextGlobalEventId = initialEventId;
        }
        // Add to the event list, but don't add local event ID since we automatically agree with it
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
    public boolean sendChoiceLocalId(int localEid, boolean choice) {
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
    public DevCommandEvent getEventWithId(int eventId) {
        return events.stream().filter((e) -> e.id == eventId).findFirst().orElse(null);
    }

    public void removeEvent(DevCommandEvent event) {
        // Remove from global event list and local->global id map
        if (event.localId != -1) {
            localToGlobalEventIdMap.remove(event.localId);
        }
        events.remove(event);
    }

    public ByteBuffer encodePacket() {
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
    public void startGame() {
        // Get index of player in lobby
        int playerIndex = TogetherManager.players.indexOf(TogetherManager.getCurrentUser());
        initialEventId = playerIndex * COMMAND_ID_BLOCK_SIZE;
        nextGlobalEventId = initialEventId;
    }

    /**
     * Reset the list of events, event IDs, etc.
     */
    public void reset() {
        events.clear();
        initialEventId = -1;
        nextGlobalEventId = -1;
    }

    /**
     * Initialize the {@link DevCommandHandler} static instance.
     */
    public void initialize() {
        chatScreen.listeners.add(this);
    }

    @Override
    public String preChatSend(String message) {
        val prefix = TEXT.yes_no_prefix;
        if (message.startsWith(prefix)) {
            // Parse chat string
            val args = message.split(" ");
            try {
                val localEid = Integer.parseInt(args[1]);
                val choice = parseYesNo(args[2]);
                if (!sendChoiceLocalId(localEid, choice)) {
                    errorPopup.show(TEXT.error, TEXT.error_invalid_command_id);
                }
            } catch (ArrayIndexOutOfBoundsException|IllegalArgumentException e) {
                errorPopup.show(TEXT.error, TEXT.error_sfrun_args);
            }
            return "";
        }
        return message;
    }

    /**
     * Leniently parse a string as yes/no, true/false, or other variations.
     *
     * @param s Boolean-ish string
     * @throws IllegalArgumentException if the string doesn't correspond to true or false
     * @return boolean
     */
    public static boolean parseYesNo(String s) {
        s = s.toLowerCase();
        if (s.startsWith(TEXT.yes) || s.equals("true")) {
            return true;
        }
        if (s.startsWith(TEXT.no) || s.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException(String.format("string '%s' cannot be coerced to a boolean", s));
    }

    // See InfoPopupPatches for where these are called

    /**
     * Global UI update function called via patch
     */
    public static void update() {
        errorPopup.update();
    }

    /**
     * Global UI render callback
     */
    public static void render(SpriteBatch sb) {
        errorPopup.render(sb);
    }


    public static class Strings {
        public String error;
        public String yes;
        public String no;
        public String all;
        public String me;
        public String yes_no_help;
        public String yes_no_prefix;
        public String player_command;
        public String player_proposing;
        public String sfrun_only_host;
        public String sfrun_not_in_game;
        public String error_sfrun_args;
        public String error_invalid_command_id;
        public String allow_command;

        public static Strings fromMap(Map<String, String> m) {
            val gson = new Gson();
            val data = gson.toJsonTree(m);
            return gson.fromJson(data, Strings.class);
        }
    }
}
