package chronoMods.devcommands;

import basemod.DevConsole;
import basemod.devcommands.ConsoleCommand;
import chronoMods.coop.relics.CoopRelicHelper;

import java.util.ArrayList;
import java.util.Arrays;

public class CoopRelic extends ConsoleCommand {

    public CoopRelic() {
        followup.put("add", CmdAdd.class);
    }

    @Override
    protected void execute(String[] strings, int i) {
        errorMsg();
    }

    @Override
    protected void errorMsg() {
        DevConsole.couldNotParse();
        DevConsole.log(Arrays.asList(
                "coop-relic:",
                "* add [relic_name]"
        ));
    }

    public static class CmdAdd extends ConsoleCommand {
        public CmdAdd() {
            requiresPlayer = true;
            maxExtraTokens = 1;
            minExtraTokens = 1;
            simpleCheck = true;
        }

        @Override
        protected void execute(String[] strings, int i) {

        }

        @Override
        protected ArrayList<String> extraOptions(String[] tokens, int depth) {
            return new ArrayList<>(CoopRelicHelper.blightNames);
        }
    }
}
