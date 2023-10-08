package chronoMods.devcommands;

import basemod.DevConsole;
import basemod.devcommands.ConsoleCommand;
import chronoMods.coop.relics.CoopRelicHelper;
import com.megacrit.cardcrawl.blights.AbstractBlight;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

import java.util.ArrayList;
import java.util.Arrays;

public class SfRelic extends ConsoleCommand {

    public static final String KEY = "sfrelic";

    public SfRelic() {
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
                KEY + ":",
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
        protected void execute(String[] tokens, int depth) {
            String blightName = tokens[2];
            AbstractBlight blight = AbstractDungeon.player.getBlight(blightName);
            AbstractBlight newBlight = CoopRelicHelper.getBlight(blightName);

            if (blight != null) {
                blight.incrementUp();
                blight.stack();
            } else if (newBlight != null) {
                newBlight.spawn((float)Settings.WIDTH / 2.0f, (float)Settings.HEIGHT / 2.0f);
                newBlight.obtain();
                newBlight.onEquip();
                newBlight.isObtained = true;
                newBlight.isAnimating = false;
                newBlight.isDone = false;
                newBlight.flash();
            } else {
                DevConsole.log("invalid blight ID");
            }
        }

        @Override
        protected ArrayList<String> extraOptions(String[] tokens, int depth) {
            return new ArrayList<>(CoopRelicHelper.blightNames);
        }
    }
}
