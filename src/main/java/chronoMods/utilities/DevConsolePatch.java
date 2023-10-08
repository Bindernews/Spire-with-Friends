package chronoMods.utilities;

import basemod.DevConsole;
import chronoMods.TogetherManager;
import chronoMods.devcommands.SfRun;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;

import java.util.ArrayList;

public class DevConsolePatch {

    public static final ArrayList<String> SAFE_PREFIXES = new ArrayList<>();
    static {
        SAFE_PREFIXES.add(SfRun.KEY + " ");
        SAFE_PREFIXES.add("help");
    }

    public static boolean isSafeCommand(String cmd) {
        for (String prefix : SAFE_PREFIXES) {
            if (cmd.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @SpirePatch(clz = DevConsole.class, method="execute")
    public static class interceptExecute {
        public static SpireReturn<Void> Prefix() {
            if (TogetherManager.getAllowDevCommands() || isSafeCommand(DevConsole.currentText)) {
                return SpireReturn.Continue();
            } else {
                DevConsole.log("Only the sfrun command is allow during co-op.");
                return SpireReturn.Return();
            }
        }
    }
}
