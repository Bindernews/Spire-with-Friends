package chronoMods.utilities;

import chronoMods.TogetherManager;
import chronoMods.network.RemotePlayer;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.LongMap;
import lombok.Getter;
import lombok.val;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Holds player votes and can determine the group's decision once enough players have voted.
 */
public class VoteTracker {
    /**
     * Voting mode.
     */
    @Getter
    private final VoteMode mode;

    /**
     * The vote choice. Null if a choice has not been made, or the choice was not conclusive.
     */
    @Getter
    private Integer choice = null;

    /**
     * Special case for boolean consensus modes.
     * Tracks if anyone has voted `false`.
     */
    private boolean hasFalseVote = false;

    /**
     * Map of player IDs to vote choices.
     */
    @Getter
    private final LongMap<Integer> votes = new LongMap<>(TogetherManager.players.size() * 2);

    /**
     * Set of event listeners.
     */
    @Getter
    private final Set<Listener> listeners = new HashSet<>();

    public VoteTracker(VoteMode mode) {
        this.mode = mode;
    }

    /**
     * Boolean version of {@link VoteTracker#put(RemotePlayer, int)}.
     * @param player player who made the vote
     * @param choice the choice value, converted to 1 or 0
     */
    public void put(RemotePlayer player, boolean choice) {
        put(player, choice ? 1 : 0);
    }

    /**
     * Add the player's choice to the tracker.
     * <p>
     * Listeners may not actually trigger if there is a conflict in unique mode, or other conditions.
     * Note that this does NOT automatically call {@link VoteTracker#check(int)}.
     *
     * @param player player who made the vote
     * @param choice the choice value
     */
    public void put(RemotePlayer player, int choice) {
        if (mode == VoteMode.UNIQUE) {
            // If there's a conflict in unique mode, prefer the player with the lower ID
            val conflictKey = votes.findKey(choice, true, Long.MIN_VALUE);
            if (conflictKey != Long.MIN_VALUE && conflictKey != player.getAccountID()) {
                val loser = Math.max(player.getAccountID(), conflictKey);
                // If the loser is the other person, remove them, otherwise return so that we don't over-write.
                if (loser == conflictKey) {
                    votes.remove(loser);
                } else {
                    return;
                }
            }
        }
        // Update the choice
        votes.put(player.getAccountID(), choice);
        // Special cases
        if (mode == VoteMode.CONSENSUS && choice == 0) {
            // Consensus mode tracking if there's a boolean
            hasFalseVote = true;
        }
        // Listeners
        for (val l : listeners) {
            l.onPlayerVoted(player, choice);
        }
    }

    /**
     * Returns the value for the given key, or the provided default value.
     *
     * @param player key
     * @param defaultValue default value to return if key is not found
     *
     * @see Map#get(Object)
     */
    public int get(RemotePlayer player, int defaultValue) {
        return votes.get(player.getAccountID(), defaultValue);
    }

    /**
     * Check if there is a vote winner.
     * @param totalPlayers total number of players
     * @return true if the vote is over (a winner was chosen, or the vote was rejected)
     */
    public boolean check(int totalPlayers) {
        switch (mode) {
            case CONSENSUS: {
                if (hasFalseVote) {
                    setChoiceInternal(0);
                    return true;
                }
                if (votes.size == totalPlayers) {
                    setChoiceInternal(1);
                    return true;
                } else {
                    return false;
                }
            }
            case UNIQUE: {
                // choice defaults to 0, but we announce the choice is made and return true.
                if (votes.size == totalPlayers) {
                    setChoiceInternal(0);
                    return true;
                } else {
                    return false;
                }
            }
            case PICK_ONE: {
                val winner = findVoteWinner(totalPlayers);
                if (winner != null) {
                    setChoiceInternal(winner);
                }
                return winner != null;
            }
            case MAJORITY: {
                val winner = findVoteWinner((totalPlayers / 2) + 1);
                if (winner != null) {
                    setChoiceInternal(winner);
                }
                return winner != null;
            }
            default:
                return false;
        }
    }

    /**
     * Find the choice with the highest number of votes, which must be at or above the minimum.
     *
     * @param votesNeeded minimum number of votes needed to win
     * @return either the winning int, or {@code null}.
     */
    private Integer findVoteWinner(int votesNeeded) {
        // Exit early if there aren't enough votes for a result.
        if (votes.size < votesNeeded) {
            return null;
        }

        // Count votes
        val voteCounts = new IntIntMap();
        for (int t : votes.values()) {
            voteCounts.getAndIncrement(t, 0, 1);
        }
        // Check for a winner
        Integer winner = null;
        for (IntIntMap.Entry e : voteCounts.entries()) {
            if (e.value >= votesNeeded) {
                winner = e.key;
                break;
            }
        }
        return winner;
    }

    private void setChoiceInternal(int choice) {
        this.choice = choice;
        for (val l : listeners) {
            l.onChoiceMade(this.choice);
        }
    }

    public enum VoteMode {
        /**
         * Everyone must agree, or the result is inconclusive.
         */
        PICK_ONE,
        /**
         * Everyone must agree, and any {@code false} votes will result in immediate failure.
         * Basically the boolean version of {@link VoteMode#PICK_ONE}.
         */
        CONSENSUS,
        /**
         * Over half the players must agree on a choice. No special case for booleans.
         */
        MAJORITY,
        /**
         * Each player must pick a unique option. Conflicts will be resolved in favor of
         * the player with the lowest account ID.
         */
        UNIQUE,
    }

    public interface Listener {

        /**
         * Called when a player has voted.
         * @param player player who voted
         * @param vote the vote they made
         */
        default void onPlayerVoted(RemotePlayer player, int vote) {}

        /**
         * Called when enough votes have been cast to make a final choice.
         * @param choice final voted choice
         */
        default void onChoiceMade(int choice) {}
    }
}
