package chronoMods.devcommands;

import basemod.DevConsole;
import basemod.devcommands.ConsoleCommand;
import chronoMods.TogetherManager;
import chronoMods.network.DevCommandHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import static chronoMods.network.DevCommandHandler.TEXT;

public class SfRun extends ConsoleCommand {

    public static final String KEY = "sfrun";


    public SfRun() {
        requiresPlayer = true;
        minExtraTokens = 1;
        maxExtraTokens = 20;
    }

    @Override
    protected void execute(String[] tokens, int depth) {
        if (TogetherManager.currentLobby == null) {
            DevConsole.log(TEXT.sfrun_not_in_game);
            return;
        }
//        if (!TogetherManager.currentLobby.isOwner()) {
//            DevConsole.log(getText().get("sfrun only host"));
//            return;
//        }

        String player = tokens[1];
        String command = String.join(" ", Arrays.copyOfRange(tokens, 2, tokens.length));
        DevCommandHandler.getInst().proposeNewCommand(player, command);
    }

    @Override
    protected ArrayList<String> extraOptions(String[] tokens, int depth) {
        if (tokens.length < 3) {
            ArrayList<String> opts = new ArrayList<>();
            opts.add(TEXT.all);
            opts.add(TEXT.me);
            opts.addAll(TogetherManager.players.stream().map(p -> p.userName).collect(Collectors.toList()));
            return opts;
        } else {
            // auto-complete other commands
            String[] subtokens = Arrays.copyOfRange(tokens, 2, tokens.length);
            return ConsoleCommand.suggestions(subtokens);
        }
    }

    @Override
    protected void errorMsg() {
        DevConsole.couldNotParse();
        DevConsole.log("sfrun [player|all|me] [command...]");
    }
}
