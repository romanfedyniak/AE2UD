package appeng.parts.misc;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.AELog;
import appeng.api.storage.IStorageChangeSource;
import appeng.me.storage.ITickingMonitor;
import appeng.me.storage.StorageChangeListeners;
import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;
import net.minecraft.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;


/**
 * Wraps AE2UD's Storage Drawers integration ({@link IItemRepository}, a fork/third-party capability upstream AE2
 * has never had - see CONTRACT.md §10/§11) so it can be used as an {@link MEStorage}. Used exclusively by the
 * Storage Bus, as an explicit fork-specific extra on top of the generic {@link ExternalStorageStrategy} lookup
 * (see {@link PartStorageBus#findExternalStorage}) - it is not registered through
 * {@link appeng.api.behaviors.StackWorldBehaviors} because {@code IItemRepository} is not keyed by
 * {@link appeng.api.stacks.AEKeyType} at all, it is a capability specific to one third-party inventory mod.
 */
class ItemRepositoryAdapter implements MEStorage, ITickingMonitor, IStorageChangeSource {
    private final IItemRepository itemRepository;
    private KeyCounter currentlyCached = new KeyCounter();
    // The key of each drawer's prototype stack, which a drawer keeps as one object while it holds that item: a
    // rescan of a few hundred drawers otherwise spent most of its time hashing the same stacks into keys again.
    private Map<ItemStack, AEItemKey> keys = new IdentityHashMap<>();
    private final StorageChangeListeners listeners = new StorageChangeListeners();

    @Override
    public void addChangeListener(final Listener listener) {
        this.listeners.addChangeListener(listener);
    }

    @Override
    public void removeChangeListener(final Listener listener) {
        this.listeners.removeChangeListener(listener);
    }


    ItemRepositoryAdapter(final IItemRepository itemRepository) {
        this.itemRepository = itemRepository;
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
            this.moved(itemKey, inserted);
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
            this.moved(itemKey, -extracted.getCount());
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

    /**
     * Counts what this adapter itself moved under its own key only, and leaves the bus's ticking alone. A full
     * rescan after every insertion, and the bus woken for another one next tick, cost a storage bus on a few hundred
     * drawers far more than the drawers' own work while items kept flowing. The bus's own rescans still catch what
     * else changed with it, such as the other tiers of a compacting drawer.
     */
    private void moved(final AEItemKey key, final long delta) {
        this.currentlyCached.set(key, Math.max(0, this.currentlyCached.get(key) + delta));
        this.listeners.post(key, delta);
    }

    private void updateCache() {
        final KeyCounter fresh = new KeyCounter();
        final Map<ItemStack, AEItemKey> freshKeys = new IdentityHashMap<>();
        for (final IItemRepository.ItemRecord entry : this.itemRepository.getAllItems()) {
            if (entry.count <= 0) {
                continue;
            }
            AEItemKey key = this.keys.get(entry.itemPrototype);
            if (key == null || !key.matches(entry.itemPrototype)) {
                key = AEItemKey.of(entry.itemPrototype);
            }
            if (key != null) {
                freshKeys.put(entry.itemPrototype, key);
                fresh.add(key, entry.count);
            }
        }
        this.keys = freshKeys;
        // Every change this adapter can see passes through here - its own insertions as much as a
        // hopper filling the box next door - so this is the one place that has to say what moved.
        this.listeners.postDiff(this.currentlyCached, fresh);
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
