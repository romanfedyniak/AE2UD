package appeng.integration.modules.jei;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.me.GridInventoryEntry;
import appeng.helpers.IContainerCraftingPacket;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The slice of the old {@code IItemList<IAEItemStack>} that {@link JEIMissingItem} and
 * {@link CraftableCallBack} actually used: for every key, how much of it the terminal can supply and
 * whether the network can craft it, with precise and fuzzy lookup over the pair.
 * <p>
 * {@link appeng.api.stacks.KeyCounter} deliberately cannot serve here, for two reasons that both matter
 * to this code:
 * <ul>
 * <li>It stores no craftable flag — keys carry none either (see {@code CONTRACT.md} §8.3), which is why
 * {@link GridInventoryEntry} exists at all.</li>
 * <li>{@code KeyCounter.add}/{@code set} treat an amount of zero as "not present" and drop the key. The
 * JEI helpers depend on the opposite: an entry with amount zero and craftable true is exactly what
 * paints an ingredient slot blue instead of red, and the old {@code IItemList} kept those.</li>
 * </ul>
 * Fuzzy lookup is grouped by {@link AEKey#getPrimaryKey()} the same way {@code KeyCounter} groups it, so
 * a fuzzy search scans the variants of one item rather than the whole network.
 */
class AvailableItems {

    /**
     * One row. Mutable in the amount, like the {@code IAEItemStack} it replaces — the merge below adds
     * the player inventory and the crafting grid on top of what the network reports.
     */
    static final class Entry {
        private final AEKey what;
        private long amount;
        private boolean craftable;

        private Entry(AEKey what) {
            this.what = what;
        }

        AEKey what() {
            return this.what;
        }

        long amount() {
            return this.amount;
        }

        boolean craftable() {
            return this.craftable;
        }
    }

    private final Map<AEKey, Entry> byKey = new LinkedHashMap<>();
    private final Map<Object, List<Entry>> byPrimary = new HashMap<>();

    void add(@Nullable AEKey what, long amount, boolean craftable) {
        if (what == null) {
            return;
        }

        Entry entry = this.byKey.get(what);
        if (entry == null) {
            entry = new Entry(what);
            this.byKey.put(what, entry);
            this.byPrimary.computeIfAbsent(what.getPrimaryKey(), k -> new ArrayList<>()).add(entry);
        }

        entry.amount += amount;
        entry.craftable |= craftable;
    }

    void add(GridInventoryEntry entry) {
        this.add(entry.getWhat(), entry.getStoredAmount(), entry.isCraftable());
    }

    @Nullable
    Entry findPrecise(@Nullable AEKey what) {
        return what == null ? null : this.byKey.get(what);
    }

    Collection<Entry> findFuzzy(@Nullable AEKey what, FuzzyMode mode) {
        if (what == null) {
            return Collections.emptyList();
        }

        final List<Entry> candidates = this.byPrimary.get(what.getPrimaryKey());
        if (candidates == null) {
            return Collections.emptyList();
        }

        final List<Entry> matches = new ArrayList<>();
        for (final Entry candidate : candidates) {
            if (what.fuzzyEquals(candidate.what, mode)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    /**
     * Everything the player can reach without leaving the recipe screen: the network's listing, plus the
     * main inventory, plus the crafting grid on a crafting terminal.
     * <p>
     * The network half used to come from the public {@code ContainerMEMonitorable.items} field. That
     * field is gone — the client-side listing now lives in the terminal screen's {@code ItemRepo}, which
     * is the only place the craftable flag survives the trip from the server. A container with no screen
     * attached contributes nothing rather than failing, so this is safe to call from anywhere.
     */
    static AvailableItems merge(ContainerMEMonitorable container) {
        final AvailableItems merged = new AvailableItems();

        if (container.getGui() instanceof GuiMEMonitorable gui) {
            for (final GridInventoryEntry entry : gui.getRepo().getAllEntries()) {
                merged.add(entry.getWhat(), entry.getStoredAmount(), entry.isCraftable());
            }
        }

        final PlayerMainInvWrapper invWrapper = new PlayerMainInvWrapper(container.getPlayerInv());
        for (int i = 0; i < invWrapper.getSlots(); i++) {
            merged.addStack(invWrapper.getStackInSlot(i));
        }

        if (container instanceof IContainerCraftingPacket) {
            final IItemHandler itemHandler = ((IContainerCraftingPacket) container).getInventoryByName("crafting");
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                merged.addStack(itemHandler.getStackInSlot(i));
            }
        }

        return merged;
    }

    private void addStack(ItemStack stack) {
        if (!stack.isEmpty()) {
            this.add(AEItemKey.of(stack), stack.getCount(), false);
        }
    }
}
