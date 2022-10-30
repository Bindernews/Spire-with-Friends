package chronoMods.coop;

import basemod.DevConsole;
import chronoMods.TogetherManager;
import chronoMods.network.BBuf;
import chronoMods.network.NetworkHelper;
import chronoMods.network.RemotePlayer;
import chronoMods.ui.hud.InfoPopup;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.LongMap;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Manages a single request to allow a dev console command in co-op mode.
 *
 * The static {@code events} field holds all current active events.
 */
public class CoopCommandEvent {

    public static final String[] TEXT = CardCrawlGame.languagePack.getUIString("CoopCommand").TEXT;

    /** The last seen event ID, used to generate new event IDs in a non-colliding manner */
    private static int lastEventId = 1;

    /**
     * List of currently active commands events.
     */
    public static ArrayList<CoopCommandEvent> events = new ArrayList<>();

    /** Popup for asking to accept/reject the command. */
    private static InfoPopup infoPopup = new InfoPopup();
    /** Tracking the event ID for the current popup, -1 means no event */
    private static int currentEventId = -1;

    /** Player who proposed this command */
    public final RemotePlayer proposer;

    /** The proposed command */
    private final String command;

    /**
     * Who is going to run the command? Either a player name, or ALL.
     * The keyword ME is resolved when the command is run.
     */
    private final String target;

    /** The event ID, should be unique for a given run */
    public final int id;

    /** Track if the local player has made a choice, used for UI */
    private boolean hasChosen = false;

    /** The local player's choice to allow or reject the command */
    private boolean currentChoice = false;

    /**
     * All players' choices on accept/reject
     */
    private final LongMap<Boolean> playerChoices = new LongMap<>();

    private CoopCommandEvent(int id, String target, String command, RemotePlayer proposer) {
        this.id = id;
        this.target = target;
        this.command = command;
        this.proposer = proposer;
    }

    /**
     * Encode and return this player's choice for this command as a byte buffer.
     */
    public ByteBuffer encodeChoicePacket() {
        ByteBuffer data = ByteBuffer.allocateDirect(4+4+1);
        data.putInt(4, id);
        data.put(8, (byte)(currentChoice ? 1:0));
        ((Buffer)data).rewind();
        return data;
    }

    /**
     * Decode the packet with the given choice data and update the appropriate event
     * @param data
     */
    public static void handleSelectPacket(ByteBuffer data, RemotePlayer playerInfo) {
        CoopCommandEvent event = CoopCommandEvent.getEventWithId(data.getInt(4));
        if (event == null) {
            return;
        }
        boolean choice = data.get(8) == 1;
        event.registerChoice(choice, playerInfo);
    }

    /**
     * Encode and return a command proposal packet as a byte buffer
     */
    public ByteBuffer encodeProposePacket() {
        byte[] targetBytes = target.getBytes(StandardCharsets.UTF_8);
        byte[] commandBytes = command.getBytes(StandardCharsets.UTF_8);
        BBuf data = BBuf.allocate(4 + 4 + targetBytes.length + 2 + commandBytes.length + 2);
        data.putInt(0); // skip 4
        data.putInt(id);
        data.putLenBytes(targetBytes);
        data.putLenBytes(commandBytes);
        return data.b;
    }

    /**
     * Handle a "propose" packet, as created with {@link CoopCommandEvent#encodeProposePacket}.
     * This method has lots of side effects, be careful.
     */
    public static void handleProposePacket(ByteBuffer data1, RemotePlayer playerInfo) {
        BBuf data = new BBuf(data1);
        data.pos(4);
        int eventId = data.getInt();
        String target = data.getLenString();
        String command = data.getLenString();
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
    }

    /**
     * Begins a command proposal, creating a new event with a unique ID and sending the request to other players.
     * @param executor Who will be running the command
     * @param command command to propose
     */
    public static void proposeNewCommand(String executor, String command) {
        if (executor.equalsIgnoreCase(TEXT[3])) {
            executor = TogetherManager.getCurrentUser().userName;
        }
        CoopCommandEvent event = new CoopCommandEvent(
                getNextEventId(), executor, command, TogetherManager.getCurrentUser());
        events.add(event);
        NetworkHelper.sendData(NetworkHelper.dataType.CoopCommandPropose);
        // Auto-choose true if we're proposing the command
        int oldId = currentEventId;
        currentEventId = event.id;
        event.choose(true);
        currentEventId = oldId;
    }

    /**
     * Register a choice made by remote players (or the local one)
     * @param choice True to allow the command, false to reject it
     * @param playerInfo Remote player info
     */
    public void registerChoice(boolean choice, RemotePlayer playerInfo) {
        // Update choice
        playerChoices.put(playerInfo.getAccountID(), choice);
        // Check if decision is complete, either everyone agrees, or it doesn't go through
        if (playerChoices.size == TogetherManager.players.size()) {
            boolean accept = true;
            for (Boolean v : playerChoices.values()) {
                if (!v) {
                    accept = false;
                    break;
                }
            }
            if (accept) {
                // Everyone agrees!
                TogetherManager.log("Co-op command was accepted");
                TogetherManager.setAllowDevCommands(true);
                DevConsole.currentText = command;
                DevConsole.execute();
                DevConsole.priorCommands.remove(0);
                TogetherManager.setAllowDevCommands(false);
            } else {
                // Not consensus, don't allow
                TogetherManager.log("Co-op command was rejected");
            }
            // Regardless, remove event from list
            events.remove(this);
        }
    }

    /**
     * Called to set the local player's choice
     * @param allow true to allow, false to reject
     */
    public void choose(boolean allow) {
        currentChoice = allow;
        // Send the network choice BEFORE calling registerChoice so that we don't remove the event before using it
        NetworkHelper.sendData(NetworkHelper.dataType.CoopCommandSelect);
        hasChosen = true;
        registerChoice(currentChoice, TogetherManager.currentUser);

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

    /**
     * Helper function that checks to make sure the game is in co-op mode
     */
    private boolean assertCoop() {
        if (TogetherManager.gameMode != TogetherManager.mode.Coop) {
            TogetherManager.log("Not in a co-op game!");
            return false;
        }
        return true;
    }

    public boolean getCurrentChoice() {
        return currentChoice;
    }

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
                    String msg = String.format(TEXT[1], e.proposer.userName, e.command);
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

    // These are copied from InfoPopupPatches

    @SpirePatch(clz = CardCrawlGame.class, method="update")
    public static class infoDungeonUpdate {
        @SpireInsertPatch(rloc=760-733)
        public static void Insert(CardCrawlGame __instance) {
            CoopCommandEvent.update();
        }
    }

    @SpirePatch(clz = CardCrawlGame.class, method="render")
    public static class infoRender {
        @SpireInsertPatch(rloc=458-408)
        public static void Insert(CardCrawlGame __instance, SpriteBatch ___sb) {
            CoopCommandEvent.render(___sb);
        }
    }


}
