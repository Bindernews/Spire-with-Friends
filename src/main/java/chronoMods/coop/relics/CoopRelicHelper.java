package chronoMods.coop.relics;

import com.megacrit.cardcrawl.blights.AbstractBlight;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CoopRelicHelper {

    public static List<String> blightNames = new ArrayList<>();

    static {
        blightNames.addAll(Stream.of(
                BigHouse.ID,
                BlueLadder.ID,
                BluntScissors.ID,
                BondsOfFate.ID,
                BrainFreeze.ID,
                ChainsOfFate.ID,
                DimensionalWallet.ID,
                Dimensioneel.ID,
                DowsingRod.ID,
                GhostWriter.ID,
                MessageInABottle.ID,
                MirrorTouch.ID,
                PneumaticPost.ID,
                StringOfFate.ID,
                TransfusionBag.ID,
                VaporFunnel.ID
        ).collect(Collectors.toList()));
    }

    public static AbstractBlight getBlight(String id) {
        switch (id) {
            case BigHouse.ID:
                return new BigHouse();
            case BlueLadder.ID:
                return new BlueLadder();
            case BluntScissors.ID:
                return new BluntScissors();
            case BondsOfFate.ID:
                return new BondsOfFate();
            case BrainFreeze.ID:
                return new BrainFreeze();
            case ChainsOfFate.ID:
                return new ChainsOfFate();
            case DimensionalWallet.ID:
                return new DimensionalWallet();
            case Dimensioneel.ID:
                return new Dimensioneel();
            case DowsingRod.ID:
                return new DowsingRod();
            case GhostWriter.ID:
                return new GhostWriter();
            case MessageInABottle.ID:
                return new MessageInABottle();
            case MirrorTouch.ID:
                return new MirrorTouch();
            case PneumaticPost.ID:
                return new PneumaticPost();
            case StringOfFate.ID:
                return new StringOfFate();
            case TransfusionBag.ID:
                return new TransfusionBag();
            case VaporFunnel.ID:
                return new VaporFunnel();
            default:
                return null;
        }
    }
}
