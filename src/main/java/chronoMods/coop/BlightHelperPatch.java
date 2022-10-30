package chronoMods.coop;

import chronoMods.coop.relics.*;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.blights.AbstractBlight;
import com.megacrit.cardcrawl.helpers.BlightHelper;

public class BlightHelperPatch {

    @SpirePatch(clz = BlightHelper.class, method = "initialize")
    public static class patchAddBlights {
        public static void Postfix() {
            BlightHelper.blights.addAll(CoopRelicHelper.blightNames);
        }
    }

    @SpirePatch(clz = BlightHelper.class, method = "getBlight")
    public static class patchGetBlights {
        public static AbstractBlight Postfix(AbstractBlight __result, String id) {
            if (__result != null) {
                return __result;
            }
            return CoopRelicHelper.getBlight(id);
        }
    }
}
