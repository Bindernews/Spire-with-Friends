package chronoMods.utilities;

import chronoMods.network.RemotePlayer;
import com.badlogic.gdx.utils.LongMap;
import lombok.Getter;
import lombok.val;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds player votes and check finds the players' decision once enough players have voted.
 * @param <T> The type of thing players are voting on
 */
@Getter
public class VoteTracker<T> {
    /**
     * Voting mode.
     */
    private final VoteMode mode;

    /**
     * The vote choice. Null if a choice has not been made, or the choice was not conclusive.
     */
    private T choice;

    /**
     * Map of player IDs to vote choices.
     */
    private final LongMap<T> votes = new LongMap<>();

    public VoteTracker(VoteMode mode) {
        this.mode = mode;
    }

    /**
     * @see Map#put(Object, Object)
     */
    public void put(RemotePlayer player, T choice) {
        votes.put(player.getAccountID(), choice);
    }

    /**
     * Returns the value for the given key, or the provided default value.
     *
     * @param player key
     * @param defaultValue default value to return if key is not found
     *
     * @see Map#get(Object)
     */
    public T get(RemotePlayer player, T defaultValue) {
        return votes.get(player.getAccountID(), defaultValue);
    }

    /**
     * Check if there is a vote winner.
     * @param totalPlayers total number of players
     * @return true if the vote is over (a winner was chosen, or the vote was rejected)
     */
    public boolean check(int totalPlayers) {
        int votesNeeded = 0;
        switch (mode) {
            case CONSENSUS:
                votesNeeded = totalPlayers;
                break;
            case MAJORITY:
                votesNeeded = (totalPlayers / 2) + 1;
                break;
        }
        // Exit early if there aren't enough votes for a result.
        if (votes.size < votesNeeded) {
            return false;
        }

        val voteCounts = new HashMap<T, Integer>();
        // Count votes
        for (T t : votes.values()) {
            voteCounts.putIfAbsent(t, 0);
            voteCounts.put(t, voteCounts.get(t) + 1);
        }
        // Check for a winner
        T winner = null;
        for (Map.Entry<T, Integer> e : voteCounts.entrySet()) {
            if (e.getValue() >= votesNeeded) {
                winner = e.getKey();
                break;
            }
        }
        // Update the choice field, and if we have a winner return true.
        choice = winner;
        if (choice != null) {
            return true;
        }

        // No winner, so we determine if the result is "fail" or "inconclusive"
        switch (mode) {
            case PICK_ONE:
            case MAJORITY:
                return false;
            case CONSENSUS:
                return true;
        }
        return true;
    }

    public enum VoteMode {
        /** Everyone must agree, or the result is inconclusive. */
        PICK_ONE,
        /** Everyone must agree, or the result is failure. */
        CONSENSUS,
        /** Over half must pick a choice, or the result is inconclusive. */
        MAJORITY,
    }
}
