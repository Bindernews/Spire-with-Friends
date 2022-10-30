package chronoMods.utilities;

import basemod.DevConsole;
import chronoMods.TogetherManager;
import chronoMods.devcommands.SfRun;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;

public class DevConsolePatch {

    static final String SAFE_PREFIX = SfRun.KEY + " ";

    @SpirePatch(clz = DevConsole.class, method="execute")
    public static class interceptExecute {
        public static SpireReturn Prefix() {
            if (!(TogetherManager.getAllowDevCommands() || DevConsole.currentText.startsWith(SAFE_PREFIX))) {
                DevConsole.log("Console is disabled during a run, please use /run in the chat");
                return SpireReturn.Return();
            }
            return SpireReturn.Continue();
        }
    }
}
