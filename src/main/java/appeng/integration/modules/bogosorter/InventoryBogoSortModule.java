package appeng.integration.modules.bogosorter;

import appeng.api.stacks.AEKey;
import appeng.util.Platform;
import com.cleanroommc.bogosorter.common.sort.SortHandler;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.Comparator;

public class InventoryBogoSortModule {
    private static final boolean loaded = Platform.isModLoaded("bogosorter");

    /**
     * Drop-in replacement for {@code ItemSorters.CONFIG_BASED_SORT_BY_INV_TWEAKS}, so it is shaped like
     * the rest of that class: an entry of a {@code KeyCounter}, key plus stored amount.
     * <p>
     * Bogosorter can only order {@link net.minecraft.item.ItemStack}s, so every key reaches it through
     * {@link AEKey#wrapForDisplayOrFilter()} — the stack itself for an {@code AEItemKey}, the generic
     * wrapper item for every other type. Keys of a non-item type consequently all look alike to
     * bogosorter and compare equal; because the terminal sorts with a stable sort they keep whatever
     * relative order they already had instead of being shuffled. Ordering them properly is bogosorter's
     * call to make, not ours — it has no notion of a fluid.
     */
    public static final Comparator<Object2LongMap.Entry<AEKey>> COMPARATOR = (o1, o2) -> SortHandler.getClientItemComparator()
            .compare(o1.getKey().wrapForDisplayOrFilter(), o2.getKey().wrapForDisplayOrFilter());

    public static boolean isLoaded() {

        return loaded;
    }
}
