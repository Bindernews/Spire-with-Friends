package chronoMods.devcommands;

import basemod.DevConsole;
import basemod.devcommands.ConsoleCommand;
import chronoMods.TogetherManager;
import chronoMods.network.CoopCommandHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

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
            DevConsole.log(getText().get("sfrun not in lobby"));
            return;
        }
//        if (!TogetherManager.currentLobby.isOwner()) {
//            DevConsole.log(getText().get("sfrun only host"));
//            return;
//        }

        String player = tokens[1];
        String command = String.join(" ", Arrays.copyOfRange(tokens, 2, tokens.length));
        CoopCommandHandler.proposeNewCommand(player, command);
    }

    @Override
    protected ArrayList<String> extraOptions(String[] tokens, int depth) {
        if (tokens.length < 3) {
            ArrayList<String> opts = new ArrayList<>();
            opts.add(getText().get("all"));
            opts.add(getText().get("me"));
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

    public static Map<String, String> getText() { return CoopCommandHandler.TEXT; }
}
