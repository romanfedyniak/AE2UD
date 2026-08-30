/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.storage;


import javax.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.upgrades.UpgradeCards;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.util.Platform;
import appeng.util.prioritylist.FuzzyPriorityList;
import appeng.util.prioritylist.IPartitionList;


/**
 * The contents of a standard storage cell.
 * <p/>
 * Bytes are counted per {@link AEKeyType}, because a cell is allowed to name several
 * ({@link IBasicCellItem#getKeyTypes()}) and a byte holds a different amount of each - one byte is eight
 * items or one quarter of a bucket. Every cell Applied Energistics ships names exactly one type, for which
 * the sum below has a single term and the arithmetic is the same as it ever was.
 * <p/>
 * Replaces {@code AbstractCellInventory}, {@code BasicCellInventory} and {@code BasicCellInventoryHandler}, which
 * were split across a generic base class, a generic {@code ICellInventory} implementation and a
 * {@code MEInventoryHandler}-derived wrapper that built the whitelist from the cell's upgrades/config. Since
 * {@link StorageCell} is itself an {@link appeng.api.storage.MEStorage}, all three collapse into one class; the
 * whitelist/priority wrapping that {@code BasicCellInventoryHandler} used to add is now just an ordinary
 * {@link MEInventoryHandler} (or {@link DriveWatcher}) built by whoever mounts this cell.
 * <p/>
 * Storage format: rather than the old fixed 63 numbered NBT slots, contents are kept in an NBT list of
 * {@link GenericStack} tags - old-world save compatibility is deliberately not preserved by this migration.
 */
public class BasicCellInventory implements StorageCell {
    private static final int MAX_ITEM_TYPES = 63;
    private static final String ITEMS_TAG = "Items";
    private static final String ITEM_TYPE_TAG = "it";
    private static final String ITEM_COUNT_TAG = "ic";

    private final NBTTagCompound tagCompound;
    @Nullable
    private final ISaveProvider container;
    private final ItemStack i;
    private final IBasicCellItem cellType;
    private final Set<AEKeyType> keyTypes;
    private final IPartitionList partitionList;
    private final IncludeExclude partitionListMode;
    private final boolean sticky;
    private int maxItemTypes;
    private int storedItemTypes;
    private long storedItemCount;
    @Nullable
    private Object2LongMap<AEKey> storedAmounts;
    // How much of each type is stored, which is what the byte count is built from. Derived from
    // storedAmounts, never saved: ITEM_COUNT_TAG stays the plain total it has always been.
    @Nullable
    private Object2LongMap<AEKeyType> storedAmountsByType;
    private boolean isPersisted = true;
    private final boolean equalDistribution;
    /** What the shares divide, and into how many. Both 0 without the card. See {@link #getMaxAmountPerType}. */
    private final long distributableBytes;
    private final long shares;

    private BasicCellInventory(final IBasicCellItem cellType, final ItemStack o, @Nullable final ISaveProvider container) {
        this.i = o;
        this.cellType = cellType;
        this.keyTypes = cellType.getKeyTypes();
        this.maxItemTypes = cellType.getTotalTypes(o);

        if (this.maxItemTypes > MAX_ITEM_TYPES) {
            this.maxItemTypes = MAX_ITEM_TYPES;
        }
        if (this.maxItemTypes < 1) {
            this.maxItemTypes = 1;
        }

        this.container = container;
        this.tagCompound = Platform.openNbtData(o);
        this.storedItemTypes = this.tagCompound.getShort(ITEM_TYPE_TAG);
        this.storedItemCount = this.tagCompound.getLong(ITEM_COUNT_TAG);

        final IItemHandler upgrades = cellType.getUpgradesInventory(o);
        final IItemHandler config = cellType.getConfigInventory(o);

        boolean hasInverter = false;
        boolean hasFuzzy = false;
        boolean hasSticky = false;
        boolean hasEqualDistribution = false;

        // ICellWorkbenchItem does not forbid a null upgrades inventory - this fork's creative cell returned
        // null for years, safely, because CreativeCellInventory never read it. Treating null as "no upgrades"
        // rather than dereferencing it keeps a third-party cell that does the same from crashing the client
        // while it builds the creative search tree, where the stack trace points at a tooltip and not at the
        // cell. This is defensive only; it is not what fixed the creative cell (see ItemCreativeStorageCell).
        for (int x = 0; upgrades != null && x < upgrades.getSlots(); x++) {
            final ItemStack is = upgrades.getStackInSlot(x);
            if (ItemStack.areItemsEqual(is, UpgradeCards.fuzzy())) {
                hasFuzzy = true;
            } else if (ItemStack.areItemsEqual(is, UpgradeCards.inverter())) {
                hasInverter = true;
            } else if (ItemStack.areItemsEqual(is, UpgradeCards.sticky())) {
                hasSticky = true;
            } else if (ItemStack.areItemsEqual(is, UpgradeCards.equalDistribution())) {
                hasEqualDistribution = true;
            }
        }
        this.sticky = hasSticky;

        final IPartitionList.Builder builder = IPartitionList.builder();
        final Set<AEKey> configuredKeys = new HashSet<>();
        for (int x = 0; config != null && x < config.getSlots(); x++) {
            final ItemStack is = config.getStackInSlot(x);
            if (!is.isEmpty()) {
                // Resolve rather than assume: a cell's partition is stored as plain ItemStacks (CellConfig
                // is an item inventory), so a non-item key travels through it as a wrapper. Reading that
                // back with AEItemKey.of would partition the cell on the placeholder item itself - a filter
                // matching nothing, which stops the cell accepting anything at all.
                final GenericStack configured = AppEngInternalAEInventory.toGenericStack(is);
                if (configured != null) {
                    builder.add(configured.what());
                    configuredKeys.add(configured.what());
                }
            }
        }

        if (hasFuzzy) {
            builder.fuzzyMode(cellType.getFuzzyMode(o));
        }

        this.partitionListMode = hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;
        this.partitionList = builder.build();

        this.equalDistribution = hasEqualDistribution;
        this.shares = hasEqualDistribution ? this.countShares(configuredKeys.size(), hasFuzzy) : 0;
        this.distributableBytes = hasEqualDistribution
                ? Math.max(0, this.getTotalBytes() - (long) this.getBytesPerType() * this.shares)
                : 0;
    }

    /**
     * Into how many equal parts the cell is cut: the partition list when there is one to count - a fuzzy
     * list stands for more keys than it holds, so it cannot be - and the cell's own type limit otherwise.
     */
    private long countShares(final int configuredKeys, final boolean hasFuzzy) {
        if (!hasFuzzy && this.partitionListMode == IncludeExclude.WHITELIST && configuredKeys > 0) {
            return Math.min(this.maxItemTypes, configuredKeys);
        }

        return this.maxItemTypes;
    }

    /**
     * The most of {@code type} one key may hold, or {@link Long#MAX_VALUE} without the distribution card.
     * <p>
     * The division happens in the key type's own unit rather than in bytes, because a byte is eight items
     * but only a quarter of a bucket, and rounding a share of bytes upwards would promise each type more
     * than the cell can actually hold. Bytes reserved for the types themselves are taken off the top first,
     * since no type can spend them. A cell naming one key type - which is every cell shipped - gets exactly
     * the numbers upstream computes.
     */
    public long getMaxAmountPerType(final AEKeyType type) {
        if (!this.equalDistribution) {
            return Long.MAX_VALUE;
        }

        final long total = this.distributableBytes * type.getAmountPerByte();
        return Math.max(0, (total + this.shares - 1) / this.shares);
    }

    public boolean isEqualDistribution() {
        return this.equalDistribution;
    }

    /** Into how many parts the cell is cut, and 0 without the distribution card. */
    public long getShares() {
        return this.shares;
    }

    /** What each of those parts is worth. Rounded down, so it never claims room the cell does not have. */
    public long getBytesPerShare() {
        return this.shares <= 0 ? 0 : this.distributableBytes / this.shares;
    }

    /**
     * @return null if {@code o} is not (currently) a storage cell.
     */
    @Nullable
    public static StorageCell createInventory(final ItemStack o, @Nullable final ISaveProvider container) {
        if (o == null || o.isEmpty()) {
            return null;
        }

        final IBasicCellItem cellType = getStorageCell(o);
        if (cellType == null) {
            return null;
        }

        if (!cellType.isStorageCell(o)) {
            // Not an error: items may decide to not be a storage cell temporarily.
            return null;
        }

        return new BasicCellInventory(cellType, o, container);
    }

    public static boolean isCell(final ItemStack input) {
        return getStorageCell(input) != null;
    }

    @Nullable
    private static IBasicCellItem getStorageCell(final ItemStack input) {
        if (input != null && !input.isEmpty() && input.getItem() instanceof IBasicCellItem) {
            return (IBasicCellItem) input.getItem();
        }
        return null;
    }

    public boolean isPreformatted() {
        return !this.partitionList.isEmpty();
    }

    /**
     * A partitioned cell is preferred storage for whatever it is partitioned to. This is how the network
     * fills partitioned cells before general-purpose ones, and it used to fall out of the old model for
     * free: an {@code ICellInventoryHandler}'s own partition list <em>was</em> the cell's, so
     * {@code isPrioritized} saw it. Here the two are separate - {@link MEInventoryHandler}'s list belongs
     * to a storage bus - so the cell has to answer for itself or nothing ever asks it.
     * <p>
     * The Sticky Card makes the omission fatal rather than merely suboptimal: {@code NetworkStorage}'s
     * sticky pass skips a sticky mount that does not claim the key, <em>and</em> the ordinary pass skips
     * every sticky mount as well. A partitioned sticky cell that could not report interest therefore
     * accepted nothing at all until something else had already put a matching stack in it.
     */
    @Override
    public boolean isPreferredStorageFor(final AEKey what, final IActionSource source) {
        return this.partitionListMode == IncludeExclude.WHITELIST && this.partitionList.isListed(what);
    }

    public boolean isFuzzy() {
        return this.partitionList instanceof FuzzyPriorityList;
    }

    /**
     * @return true if this cell has a Sticky Card installed in its own upgrade slots (AE2UD-specific; see
     *         CONTRACT.md §10). Whoever mounts this cell onto the network (a {@link DriveWatcher} or an
     *         {@link MEInventoryHandler} wrapper) is responsible for propagating this into
     *         {@link MEInventoryHandler#setSticky(boolean)} — {@link StorageCell} itself has no such flag.
     */
    public boolean isSticky() {
        return this.sticky;
    }

    public IncludeExclude getPartitionListMode() {
        return this.partitionListMode;
    }

    public ItemStack getItemStack() {
        return this.i;
    }

    public FuzzyMode getFuzzyMode() {
        return this.cellType.getFuzzyMode(this.i);
    }

    public IItemHandler getConfigInventory() {
        return this.cellType.getConfigInventory(this.i);
    }

    public IItemHandler getUpgradesInventory() {
        return this.cellType.getUpgradesInventory(this.i);
    }

    @Override
    public Set<AEKeyType> getSupportedKeyTypes() {
        return this.keyTypes;
    }

    public int getBytesPerType() {
        return this.cellType.getBytesPerType(this.i);
    }

    /**
     * Whether one more type of {@code type} would fit. A new type costs {@link #getBytesPerType()} bytes, and
     * those bytes are only worth taking if at least one unit of that type then fits in them.
     */
    public boolean canHoldNewItem(final AEKeyType type) {
        final long bytesFree = this.getFreeBytes();
        return (bytesFree > this.getBytesPerType()
                || (bytesFree == this.getBytesPerType() && this.getUnusedItemCount(type) > 0))
                && this.getRemainingItemTypes() > 0;
    }

    /**
     * Whether one more type of <em>anything</em> this cell holds would fit.
     */
    public boolean canHoldNewItem() {
        for (final AEKeyType type : this.keyTypes) {
            if (this.canHoldNewItem(type)) {
                return true;
            }
        }
        return false;
    }

    public long getTotalBytes() {
        return this.cellType.getBytes(this.i);
    }

    public long getFreeBytes() {
        return this.getTotalBytes() - this.getUsedBytes();
    }

    public long getTotalItemTypes() {
        return this.maxItemTypes;
    }

    public long getStoredItemCount() {
        return this.storedItemCount;
    }

    /**
     * How much of one type is stored. Meaningful on its own in a way the total is not: a total mixing items
     * and millibuckets counts two things that are not the same size.
     */
    public long getStoredItemCount(final AEKeyType type) {
        return this.getStoredAmountsByType().getLong(type);
    }

    public long getStoredItemTypes() {
        return this.storedItemTypes;
    }

    public long getRemainingItemTypes() {
        final long basedOnStorage = this.getFreeBytes() / this.getBytesPerType();
        final long baseOnTotal = this.getTotalItemTypes() - this.getStoredItemTypes();
        return Math.min(basedOnStorage, baseOnTotal);
    }

    public long getUsedBytes() {
        long bytesForItemCount = 0;
        for (final Object2LongMap.Entry<AEKeyType> entry : this.getStoredAmountsByType().object2LongEntrySet()) {
            final int amountPerByte = entry.getKey().getAmountPerByte();
            // Rounded up, per type: a byte holding part of a bucket is spent whole, and cannot be shared
            // with the items stored beside it.
            bytesForItemCount += (entry.getLongValue() + amountPerByte - 1) / amountPerByte;
        }
        return this.getStoredItemTypes() * this.getBytesPerType() + bytesForItemCount;
    }

    /**
     * How much more of {@code type} would fit: every free byte, plus the tail of the byte that type is
     * already halfway through.
     */
    public long getRemainingItemCount(final AEKeyType type) {
        final long remaining = this.getFreeBytes() * type.getAmountPerByte() + this.getUnusedItemCount(type);
        return remaining > 0 ? remaining : 0;
    }

    public int getUnusedItemCount(final AEKeyType type) {
        final int amountPerByte = type.getAmountPerByte();
        final int div = (int) (this.getStoredItemCount(type) % amountPerByte);

        if (div == 0) {
            return 0;
        }

        return amountPerByte - div;
    }

    @Override
    public boolean canFitInsideCell() {
        return this.cellType.storableInStorageCell() || this.getCellItems().isEmpty();
    }

    @Override
    public CellState getStatus() {
        if (this.getStoredItemTypes() == 0) {
            return CellState.EMPTY;
        }
        if (this.canHoldNewItem()) {
            return CellState.NOT_EMPTY;
        }
        for (final AEKeyType type : this.keyTypes) {
            if (this.getRemainingItemCount(type) > 0) {
                return CellState.TYPES_FULL;
            }
        }
        return CellState.FULL;
    }

    @Override
    public double getIdleDrain() {
        return this.cellType.getIdleDrain();
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        final AEKeyType type = what.getType();
        if (amount <= 0 || !this.keyTypes.contains(type)) {
            return 0;
        }

        if (!this.partitionList.matchesFilter(what, this.partitionListMode)) {
            return 0;
        }

        if (this.cellType.isBlackListed(this.i, what)) {
            return 0;
        }

        // A non-empty storage cell may not be stored recursively inside this one.
        if (what instanceof AEItemKey itemKey) {
            final StorageCell nested = StorageCells.getCellInventory(itemKey.toStack(), null);
            if (nested != null && !nested.canFitInsideCell()) {
                return 0;
            }
        }

        final long currentAmount = this.getCellItems().getLong(what);
        long remainingItemCount = this.getRemainingItemCount(type);

        if (this.equalDistribution) {
            remainingItemCount = Math.max(0, Math.min(this.getMaxAmountPerType(type) - currentAmount, remainingItemCount));
        }

        if (currentAmount <= 0) {
            if (!this.canHoldNewItem(type)) {
                // No room for a new type.
                return 0;
            }

            remainingItemCount -= (long) this.getBytesPerType() * type.getAmountPerByte();
            if (remainingItemCount <= 0) {
                return 0;
            }
        }

        long toInsert = amount;
        if (toInsert > remainingItemCount) {
            toInsert = remainingItemCount;
        }
        if (toInsert <= 0) {
            return 0;
        }

        if (mode == Actionable.MODULATE) {
            this.getCellItems().put(what, currentAmount + toInsert);
            this.saveChanges();
        }

        return toInsert;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        final long currentAmount = this.getCellItems().getLong(what);
        if (currentAmount <= 0) {
            return 0;
        }

        final long extracted = Math.min(amount, currentAmount);

        if (mode == Actionable.MODULATE) {
            if (extracted >= currentAmount) {
                this.getCellItems().removeLong(what);
            } else {
                this.getCellItems().put(what, currentAmount - extracted);
            }
            this.saveChanges();
        }

        return extracted;
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        for (final Object2LongMap.Entry<AEKey> entry : this.getCellItems().object2LongEntrySet()) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public ITextComponent getDescription() {
        return new TextComponentString(this.i.getDisplayName());
    }

    @Override
    public void persist() {
        if (this.isPersisted) {
            return;
        }

        final NBTTagList list = new NBTTagList();
        long itemCount = 0;

        for (final Object2LongMap.Entry<AEKey> entry : this.getCellItems().object2LongEntrySet()) {
            final long amount = entry.getLongValue();
            if (amount <= 0) {
                continue;
            }
            itemCount += amount;

            final NBTTagCompound entryTag = new NBTTagCompound();
            GenericStack.writeTag(entryTag, new GenericStack(entry.getKey(), amount));
            list.appendTag(entryTag);
        }

        this.storedItemTypes = list.tagCount();
        if (list.tagCount() == 0) {
            this.tagCompound.removeTag(ITEMS_TAG);
            this.tagCompound.removeTag(ITEM_TYPE_TAG);
        } else {
            this.tagCompound.setTag(ITEMS_TAG, list);
            this.tagCompound.setShort(ITEM_TYPE_TAG, (short) this.storedItemTypes);
        }

        this.storedItemCount = itemCount;
        if (itemCount == 0) {
            this.tagCompound.removeTag(ITEM_COUNT_TAG);
        } else {
            this.tagCompound.setLong(ITEM_COUNT_TAG, itemCount);
        }

        this.isPersisted = true;
    }

    private Object2LongMap<AEKey> getCellItems() {
        if (this.storedAmounts == null) {
            this.storedAmounts = new Object2LongOpenHashMap<>();
            this.loadCellItems();
        }

        return this.storedAmounts;
    }

    /**
     * Recomputed from the contents rather than saved, which is why this change needs no migration: the cell's
     * NBT has always held the keys themselves, and the totals beside them are a cache.
     * <p>
     * It does mean asking a cell for its byte usage loads its contents, where the plain total was read
     * straight off the tag. That is one pass over a list already in memory, and the alternative - trusting a
     * total that mixes items with millibuckets - cannot answer the question at all.
     */
    private Object2LongMap<AEKeyType> getStoredAmountsByType() {
        if (this.storedAmountsByType == null) {
            final Object2LongMap<AEKeyType> byType = new Object2LongOpenHashMap<>();
            for (final Object2LongMap.Entry<AEKey> entry : this.getCellItems().object2LongEntrySet()) {
                final AEKeyType type = entry.getKey().getType();
                byType.put(type, byType.getLong(type) + entry.getLongValue());
            }
            this.storedAmountsByType = byType;
        }

        return this.storedAmountsByType;
    }

    private void loadCellItems() {
        final NBTTagList list = this.tagCompound.getTagList(ITEMS_TAG, Constants.NBT.TAG_COMPOUND);
        boolean needsUpdate = false;

        for (int idx = 0; idx < list.tagCount(); idx++) {
            final NBTTagCompound entryTag = list.getCompoundTagAt(idx);

            GenericStack stack;
            try {
                stack = GenericStack.readTag(entryTag);
            } catch (final Throwable ex) {
                if (AEConfig.instance().isRemoveCrashingItemsOnLoad()) {
                    AELog.warn(ex, "Removing an item from storage cell " + this.i + " because loading it crashed.");
                    needsUpdate = true;
                    continue;
                }
                throw ex;
            }

            if (stack == null) {
                AELog.warn("Removing an item from storage cell " + this.i + " because its type could not be found.");
                needsUpdate = true;
                continue;
            }

            if (stack.amount() > 0) {
                this.storedAmounts.put(stack.what(), stack.amount());
            }
        }

        if (needsUpdate) {
            this.saveChanges();
        }
    }

    private void saveChanges() {
        this.storedItemTypes = this.getCellItems().size();
        this.storedAmountsByType = null;

        long count = 0;
        for (final Object2LongMap.Entry<AEKey> entry : this.getCellItems().object2LongEntrySet()) {
            count += entry.getLongValue();
        }
        this.storedItemCount = count;

        this.isPersisted = false;
        if (this.container != null) {
            this.container.saveChanges();
        } else {
            // If there is no ISaveProvider, store to NBT immediately.
            this.persist();
        }
    }
}
