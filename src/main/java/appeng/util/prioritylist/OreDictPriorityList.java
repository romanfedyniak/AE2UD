package appeng.util.prioritylist;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.util.item.OreDictFilterMatcher;
import appeng.util.item.OreDictFilterMatcher.MatchRule;
import appeng.util.item.OreHelper;
import appeng.util.item.OreReference;


public class OreDictPriorityList implements IPartitionList {
    private final Set<Integer> oreIDs;
    private final boolean matchesEmptyOreDict;

    public OreDictPriorityList(List<MatchRule> oreMatch) {
        this.oreIDs = OreHelper.INSTANCE.getMatchingOre(oreMatch);
        this.matchesEmptyOreDict = OreDictFilterMatcher.matches(oreMatch, "");
    }

    @Override
    public boolean isListed(final AEKey input) {
        if (!(input instanceof AEItemKey itemKey)) {
            return matchesEmptyOreDict;
        }

        OreReference or = OreHelper.INSTANCE.getOre(itemKey.getReadOnlyStack()).orElse(null);
        if (or == null) return matchesEmptyOreDict;

        for (Integer oreID : or.getOres()) {
            if (this.oreIDs.contains(oreID)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        return oreIDs.isEmpty();
    }

    @Override
    public Iterable<AEKey> getItems() {
        return Collections.emptyList();
    }

}
