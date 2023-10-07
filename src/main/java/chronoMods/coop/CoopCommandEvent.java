package chronoMods.coop;

import basemod.DevConsole;
import chronoMods.TogetherManager;
import chronoMods.network.BBuf;
import chronoMods.network.CoopCommandHandler;
import chronoMods.network.NetworkHelper;
import chronoMods.network.RemotePlayer;
import com.badlogic.gdx.utils.LongMap;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Manages a single request to allow a dev console command in co-op mode.
 * <br/>
 * The static {@code events} field holds all current active events.
 */
public class CoopCommandEvent {
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
    public boolean hasChosen = false;

    /**
     * All players' choices on accept/reject
     */
    private final LongMap<Boolean> playerChoices = new LongMap<>();

    public CoopCommandEvent(int id, String target, String command, RemotePlayer proposer) {
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
        putHeader(data, CoopCommandHandler.PACKET_SELECT);
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
        putHeader(data, CoopCommandHandler.PACKET_PROPOSE);
        BBuf.putLenBytes(data, targetBytes);
        BBuf.putLenBytes(data, commandBytes);
        return BBuf.pos(data, 0);
    }

    void putHeader(ByteBuffer buf, short kind) {
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
        playerChoices.put(playerInfo.getAccountID(), choice);
        // If the player is our current user, set hasChosen = true
        if (playerInfo.isUser(TogetherManager.currentUser)) {
            hasChosen = true;
        }

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
                // Only run the command if we are the intended target, or if everyone should run it
                if (target.equals("all") || target.equals(TogetherManager.currentUser.userName)) {
                    runCommand();
                }
            } else {
                // Not consensus, don't allow
                TogetherManager.log("Co-op command was rejected");
            }
            // Regardless, remove event from list
            CoopCommandHandler.events.remove(this);
        }
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

    public boolean getCurrentChoice() {
        return playerChoices.get(TogetherManager.currentUser.getAccountID(), false);
    }

    public String getCommand() {
        return command;
    }

    public String getTarget() {
        return target;
    }
}
