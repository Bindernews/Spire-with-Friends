package chronoMods.coop;

import chronoMods.TogetherManager;
import chronoMods.network.BBuf;
import chronoMods.network.Integration;
import chronoMods.network.RemotePlayer;
import chronoMods.ui.hud.InfoPopup;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;

import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static chronoMods.network.NetworkHelper.service;

public class CoopCommandHandler {

    public static final short PACKET_PROPOSE = 1;
    public static final short PACKET_SELECT = 2;

    public static final String[] TEXT = CardCrawlGame.languagePack.getUIString("CoopCommand").TEXT;

    /** The last seen event ID, used to generate new event IDs in a non-colliding manner */
    private static int lastEventId = 1;

    /**
     * List of currently active commands events.
     */
    public static ArrayList<CoopCommandEvent> events = new ArrayList<>();

    /** Popup for asking to accept/reject the command. */
    private static final InfoPopup infoPopup = new InfoPopup();
    /** Tracking the event ID for the current popup, -1 means no event */
    private static int currentEventId = -1;


    public static void handlePacket(ByteBuffer data, RemotePlayer playerInfo) {
        short kind = data.getShort(4);
        ((Buffer) data).position(6);
        switch (kind) {
            case PACKET_PROPOSE:
                handleProposePacket(data, playerInfo);
                break;
            case PACKET_SELECT:
                handleSelectPacket(data, playerInfo);
                break;
        }
    }

    /**
     * Decode the packet with the given choice data and update the appropriate event
     */
    public static void handleSelectPacket(ByteBuffer data, RemotePlayer playerInfo) {
        CoopCommandEvent event = getEventWithId(data.getInt());
        if (event == null) {
            return;
        }
        boolean choice = data.get() == 1;
        event.registerChoice(choice, playerInfo);
    }

    /**
     * Handle a "propose" packet, as created with {@link CoopCommandEvent#encodeProposePacket}.
     * This method has lots of side effects, be careful.
     */
    public static void handleProposePacket(ByteBuffer data, RemotePlayer playerInfo) {

        try {
            int eventId = data.getInt();
            String target = BBuf.getLenString(data);
            String command = BBuf.getLenString(data);
            // Create and add event
            CoopCommandEvent event = new CoopCommandEvent(eventId, target, command, playerInfo);
            // Check for duplicates
            if (getEventWithId(eventId) != null) {
                // TODO add more detailed error message
                TogetherManager.log("Duplicate event ID error!");
                return;
            }
            // Add to list of events
            events.add(event);
            // Update last event ID
            if (lastEventId < eventId) {
                lastEventId = eventId;
            }
            // NB. UI is handled in global update function (see below)
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
        final Integration srv = service();
        if (srv == null) {
            return;
        }
        if (executor.equalsIgnoreCase(TEXT[3])) {
            executor = TogetherManager.getCurrentUser().userName;
        }
        CoopCommandEvent event = new CoopCommandEvent(
                getNextEventId(), executor, command, TogetherManager.getCurrentUser());
        events.add(event);
        srv.sendPacket(event.encodeProposePacket());
        // Auto-choose true if we're proposing the command
        int oldId = currentEventId;
        currentEventId = event.id;
        event.choose(true);
        currentEventId = oldId;
    }

    public static void sendChoice(CoopCommandEvent event) {
        final Integration srv = service();
        if (srv == null) {
            return;
        }
        srv.sendPacket(event.encodeChoicePacket());
    }

    /**
     * Returns the event with the given event ID, or null if not found
     * @param eventId Event ID to search for
     */
    public static CoopCommandEvent getEventWithId(int eventId) {
        return events.stream().filter((e) -> e.id == eventId).findFirst().orElse(null);
    }

    /**
     * Returns the last CoopCommandEvent created by the local player, used for determining
     * which event to encode when sending the proposal to other players.
     * @return The proposed command event or null
     */
    public static CoopCommandEvent getProposedEvent() {
        for (int i = events.size() - 1; i >= 0; i--) {
            CoopCommandEvent e = events.get(i);
            if (e.proposer == TogetherManager.getCurrentUser()) {
                return e;
            }
        }
        return null;
    }

    /**
     * Returns the most recent event or null
     */
    public static CoopCommandEvent getCurrentEvent() {
        if (events.isEmpty() || currentEventId == -1) {
            return null;
        } else {
            return getEventWithId(currentEventId);
        }
    }

    /**
     * Returns a new event ID that should be unique to this player and session
     */
    public static int getNextEventId() {
        // By using the player index like this it's extremely unlikely that two commands will generate the same
        // event IDs. For this to occur (in the easiest scenario) player 0 would have to submit 2 commands and at the
        // same time as player 1 submits one command. In that case player 0 should just mod the game and allow cheats.
        int playerIndex = TogetherManager.players.indexOf(TogetherManager.getCurrentUser());
        return lastEventId + (playerIndex + 1) * 2;
    }

    // See InfoPopupPatches for where these are called

    /**
     * Global UI update function called via patch
     */
    public static void update() {
        infoPopup.update();
        // If it's not visible then either a choice was made, or we can show a new choice
        if (!infoPopup.shown) {
            // Choice was made, update the choice
            if (currentEventId != -1) {
                CoopCommandEvent event = getEventWithId(currentEventId);
                if (event != null) {
                    event.choose(infoPopup.confirmed);
                    currentEventId = -1;
                }
            }
            // No choice (or we just ended), see if we need to open a new popup
            if (currentEventId == -1) {
                // Try to find an event that the player hasn't made a choice for yet
                events.stream().filter(e -> !e.hasChosen).findFirst().ifPresent(e -> {
                    String msg = String.format(TEXT[1], e.proposer.userName, e.getCommand());
                    infoPopup.show(TEXT[0], msg, true);
                    currentEventId = e.id;
                });
            }
        }
    }

    /**
     * Global UI render callback
     */
    public static void render(SpriteBatch sb) {
        infoPopup.render(sb);
    }
}
