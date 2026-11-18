package appeng.integration.modules.bogosorter;

import appeng.api.stacks.AEKey;
import appeng.util.Platform;
import com.cleanroommc.bogosorter.common.sort.SortHandler;
import net.minecraft.item.ItemStack;

import java.util.Comparator;

public class InventoryBogoSortModule {
    private static final boolean loaded = Platform.isModLoaded("bogosorter");

    /**
     * BogoSorter's order of two display stacks. It can only order {@link ItemStack}s, so every key reaches it
     * through {@link AEKey#wrapForDisplayOrFilter()} - the stack itself for an {@code AEItemKey}, the generic
     * wrapper item for every other type. Keys of a non-item type therefore all look alike to it, and the
     * terminal's own tie-break orders them; ordering them properly is BogoSorter's call, it has no notion of a
     * fluid.
     */
    public static final Comparator<ItemStack> STACK_COMPARATOR = (a, b) -> SortHandler.getClientItemComparator()
            .compare(a, b);

    public static boolean isLoaded() {

        return loaded;
    }
}
