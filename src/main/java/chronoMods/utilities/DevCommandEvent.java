package chronoMods.utilities;

import basemod.DevConsole;
import chronoMods.TogetherManager;
import chronoMods.network.BBuf;
import chronoMods.network.DevCommandHandler;
import chronoMods.network.NetworkHelper;
import chronoMods.network.RemotePlayer;
import lombok.Getter;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Manages a single request to allow a dev console command in co-op mode.
 * <br/>
 * The static {@code events} field holds all current active events.
 */
public class DevCommandEvent {
    /** Player who proposed this command */
    public final RemotePlayer proposer;

    /** The proposed command */
    @Getter
    private final String command;

    /**
     * Who is going to run the command? Either a player name, ALL, or ME.
     */
    @Getter
    private final String target;

    /** The event ID, should be unique for a given run */
    public final int id;

    /**
     * Local event ID, to make it easier for players to type.
     * This will be -1 for the player who proposed the event (since they don't generate a local ID).
     */
    public int localId = -1;

    /** Track if the local player has made a choice, used for UI */
    public boolean hasChosen = false;

    /**
     * Track the choices of each player.
     */
    private final VoteTracker playerChoices = new VoteTracker(VoteTracker.VoteMode.CONSENSUS);

    public DevCommandEvent(int id, String target, String command, RemotePlayer proposer) {
        this.id = id;
        this.target = target;
        this.command = command;
        this.proposer = proposer;
    }

    /**
     * Encode and return this player's choice for this command as a byte buffer.
     */
    public ByteBuffer encodeChoicePacket() {
        ByteBuffer data = BBuf.allocate(10 + 1);
        putHeader(data, DevCommandHandler.PACKET_SELECT);
        data.put((byte)(getCurrentChoice() ? 1:0));
        return BBuf.pos(data, 0);
    }

    /**
     * Encode and return a command proposal packet as a byte buffer
     */
    public ByteBuffer encodeProposePacket() {
        byte[] targetBytes = target.getBytes(UTF_8);
        byte[] commandBytes = command.getBytes(UTF_8);
        ByteBuffer data = BBuf.allocate(10 + targetBytes.length + 2 + commandBytes.length + 2);
        putHeader(data, DevCommandHandler.PACKET_PROPOSE);
        BBuf.putLenBytes(data, targetBytes);
        BBuf.putLenBytes(data, commandBytes);
        return BBuf.pos(data, 0);
    }

    private void putHeader(ByteBuffer buf, short kind) {
        buf.putInt(NetworkHelper.dataType.CoopCommand.ordinal());
        buf.putShort(kind);
        buf.putInt(id);
    }

    /**
     * Register a choice made by remote players (or the local one)
     * @param choice True to allow the command, false to reject it
     * @param playerInfo Remote player info
     */
    public void registerChoice(boolean choice, RemotePlayer playerInfo) {
        // Update choice
        playerChoices.put(playerInfo, choice);
        // If the player is our current user, set hasChosen = true
        if (playerInfo.isUser(TogetherManager.currentUser)) {
            hasChosen = true;
        }
        // Check for a final decision
        if (playerChoices.check(TogetherManager.players.size())) {
            handleChoice(playerChoices.getChoice() == 1);
        }
    }

    private void handleChoice(boolean choice) {
        if (choice) {
            // Everyone agrees!
            TogetherManager.log("Co-op command was accepted");
            // Only run the command if we are the intended target, or if everyone should run it
            if (target.equals("all") || target.equals(TogetherManager.currentUser.userName)) {
                runCommand();
            }
        } else {
            // Not consensus, don't allow
            TogetherManager.log("Co-op command was rejected");
        }
        // Either way, remove from the list of events.
        DevCommandHandler.getInst().removeEvent(this);
    }

    private void runCommand() {
        TogetherManager.setAllowDevCommands(true);
        DevConsole.currentText = command;
        DevConsole.execute();
        DevConsole.priorCommands.remove(0);
        TogetherManager.setAllowDevCommands(false);
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

    /**
     * @return the current player's choice, or false if they haven't chosen yet
     */
    public boolean getCurrentChoice() {
        return playerChoices.get(TogetherManager.currentUser, 0) == 1;
    }

}
