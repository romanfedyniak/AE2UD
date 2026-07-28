package appeng.parts.misc;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.AELog;
import appeng.me.storage.ITickingMonitor;
import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;


/**
 * Wraps AE2UD's Storage Drawers integration ({@link IItemRepository}, a fork/third-party capability upstream AE2
 * has never had - see CONTRACT.md §10/§11) so it can be used as an {@link MEStorage}. Used exclusively by the
 * Storage Bus, as an explicit fork-specific extra on top of the generic {@link ExternalStorageStrategy} lookup
 * (see {@link PartStorageBus#findExternalStorage}) - it is not registered through
 * {@link appeng.api.behaviors.StackWorldBehaviors} because {@code IItemRepository} is not keyed by
 * {@link appeng.api.stacks.AEKeyType} at all, it is a capability specific to one third-party inventory mod.
 */
class ItemRepositoryAdapter implements MEStorage, ITickingMonitor {
    private final IItemRepository itemRepository;
    @Nullable
    private final Runnable changeListener;
    private KeyCounter currentlyCached = new KeyCounter();

    ItemRepositoryAdapter(final IItemRepository itemRepository, @Nullable final Runnable changeListener) {
        this.itemRepository = itemRepository;
        this.changeListener = changeListener;
        this.updateCache();
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (!(what instanceof AEItemKey itemKey) || amount <= 0) {
            return 0;
        }

        final int toInsertCount = (int) Math.min(amount, Integer.MAX_VALUE);
        final ItemStack input = itemKey.toStack(toInsertCount);

        final ItemStack remaining = this.itemRepository.insertItem(input, mode == Actionable.SIMULATE);
        final long inserted = toInsertCount - remaining.getCount();

        if (inserted > 0 && mode == Actionable.MODULATE) {
            this.updateCache();
            this.notifyChange();
        }

        return inserted;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (!(what instanceof AEItemKey itemKey) || amount <= 0) {
            return 0;
        }

        final int wanted = (int) Math.min(amount, Integer.MAX_VALUE);
        final ItemStack extracted = this.itemRepository.extractItem(itemKey.getReadOnlyStack(), wanted, mode == Actionable.SIMULATE);

        if (extracted.isEmpty()) {
            return 0;
        }

        if (extracted.getCount() > wanted) {
            // Something broke. It should never return more than we requested. Silently eat the remainder.
            AELog.warn("Mod that provided item repository %s is broken. Returned %s items while only requesting %d.",
                    this.itemRepository.getClass().getName(), extracted, wanted);
            extracted.setCount(wanted);
        }

        if (mode == Actionable.MODULATE) {
            this.updateCache();
            this.notifyChange();
        }

        return extracted.getCount();
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        for (final var entry : this.currentlyCached) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public TickRateModulation onTick() {
        final KeyCounter before = this.currentlyCached;
        this.updateCache();
        return keyCountersEqual(before, this.currentlyCached) ? TickRateModulation.SLOWER : TickRateModulation.URGENT;
    }

    private void notifyChange() {
        if (this.changeListener != null) {
            this.changeListener.run();
        }
    }

    private void updateCache() {
        final KeyCounter fresh = new KeyCounter();
        for (final IItemRepository.ItemRecord entry : this.itemRepository.getAllItems()) {
            final AEItemKey key = AEItemKey.of(entry.itemPrototype);
            if (key != null && entry.count > 0) {
                fresh.add(key, entry.count);
            }
        }
        this.currentlyCached = fresh;
    }

    private static boolean keyCountersEqual(final KeyCounter a, final KeyCounter b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (final var entry : a) {
            if (b.get(entry.getKey()) != entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }
}
