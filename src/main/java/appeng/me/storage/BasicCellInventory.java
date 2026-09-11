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

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.google.common.io.ByteStreams;
import com.google.common.io.CountingOutputStream;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.upgrades.CardTraits;
import appeng.api.upgrades.IUpgradeRegistry;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
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
 * Storage format: on a server, what a cell holds is in a file of its own ({@link CellContentsStore}), and the item
 * carries its id and a summary - how many types, how much of each key type, and the {@value #PREVIEW_SIZE}
 * largest entries for its tooltip. Cells written by an older version keep a list of {@link GenericStack} tags in
 * their NBT, which moves to a file the first time the server opens the cell. Without a store - on the client, or
 * with no server running - a cell that has no id still reads and writes that list, as it always did.
 */
public class BasicCellInventory implements StorageCell {
    /** How many of its entries a cell's item names by itself. */
    public static final int PREVIEW_SIZE = 5;
    /** A previewed key whose own NBT is bigger than this is named by its item alone. */
    private static final int PREVIEW_TAG_LIMIT = 1024;

    private static final String ITEMS_TAG = "Items";
    private static final String ITEM_TYPE_TAG = "it";
    private static final String ITEM_COUNT_TAG = "ic";
    private static final String CELL_ID_TAG = "cellId";
    private static final String CELL_ID_MOST_TAG = CELL_ID_TAG + "Most";
    private static final String CELL_ID_LEAST_TAG = CELL_ID_TAG + "Least";
    private static final String AMOUNT_BY_TYPE_TAG = "ta";
    private static final String PREVIEW_TAG = "pv";

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
    @Nullable
    private CellContents contents;
    /**
     * The id tag this inventory last saw on its item, compared by identity: writing the tag makes a new one, so
     * a different object is how another inventory on the same item is noticed without reading the id each time.
     */
    @Nullable
    private NBTBase followedIdTag;
    private boolean isPersisted = true;
    private final boolean equalDistribution;
    private final boolean voidOverflow;
    /** What the shares divide, and into how many. Both 0 without the card. See {@link #getMaxAmountPerType}. */
    private final long distributableBytes;
    private final long shares;

    private BasicCellInventory(final IBasicCellItem cellType, final ItemStack o, @Nullable final ISaveProvider container) {
        this.i = o;
        this.cellType = cellType;
        this.keyTypes = cellType.getKeyTypes();
        this.maxItemTypes = Math.max(1, cellType.getTotalTypes(o));

        this.container = container;
        this.tagCompound = Platform.openNbtData(o);

        final IItemHandler upgrades = cellType.getUpgradesInventory(o);
        final IItemHandler config = cellType.getConfigInventory(o);

        boolean hasInverter = false;
        boolean hasFuzzy = false;
        boolean hasSticky = false;
        boolean hasEqualDistribution = false;
        boolean hasVoid = false;

        // ICellWorkbenchItem does not forbid a null upgrades inventory - this fork's creative cell returned
        // null for years, safely, because CreativeCellInventory never read it. Treating null as "no upgrades"
        // rather than dereferencing it keeps a third-party cell that does the same from crashing the client
        // while it builds the creative search tree, where the stack trace points at a tooltip and not at the
        // cell. This is defensive only; it is not what fixed the creative cell (see ItemCreativeStorageCell).
        final IUpgradeRegistry registry = upgrades == null ? null : AEApi.instance().registries().upgrades();
        for (int x = 0; upgrades != null && x < upgrades.getSlots(); x++) {
            final ItemStack is = upgrades.getStackInSlot(x);
            // Asked once per card rather than as a chain of alternatives: one card may carry several of
            // these, and the cell takes every one it declared.
            hasFuzzy |= registry.isTraitSupported(is, CardTraits.FUZZY, this.i);
            hasInverter |= registry.isTraitSupported(is, CardTraits.INVERTER, this.i);
            hasSticky |= registry.isTraitSupported(is, CardTraits.STICKY, this.i);
            hasEqualDistribution |= registry.isTraitSupported(is, CardTraits.EQUAL_DISTRIBUTION, this.i);
            hasVoid |= registry.isTraitSupported(is, CardTraits.VOID, this.i);
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
        this.voidOverflow = hasVoid;
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

    public boolean isVoidOverflow() {
        return this.voidOverflow;
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
        return this.contents().getTotal();
    }

    /**
     * How much of one type is stored. Meaningful on its own in a way the total is not: a total mixing items
     * and millibuckets counts two things that are not the same size.
     */
    public long getStoredItemCount(final AEKeyType type) {
        return this.contents().getTotal(type);
    }

    public long getStoredItemTypes() {
        return this.contents().getTypes();
    }

    public long getRemainingItemTypes() {
        final long basedOnStorage = this.getFreeBytes() / this.getBytesPerType();
        final long baseOnTotal = this.getTotalItemTypes() - this.getStoredItemTypes();
        return Math.min(basedOnStorage, baseOnTotal);
    }

    public long getUsedBytes() {
        final CellContents stored = this.contents();
        long bytesForItemCount = 0;
        for (final AEKeyType type : this.keyTypes) {
            final int amountPerByte = type.getAmountPerByte();
            // Rounded up, per type: a byte holding part of a bucket is spent whole, and cannot be shared
            // with the items stored beside it.
            bytesForItemCount += (stored.getTotal(type) + amountPerByte - 1) / amountPerByte;
        }
        return stored.getTypes() * (long) this.getBytesPerType() + bytesForItemCount;
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

    /**
     * Read off the item when nothing has opened the contents yet. This is asked of a copy of every cell pushed
     * into another cell, and opening the contents of a copy would load - or migrate - a cell nobody is holding.
     */
    @Override
    public boolean canFitInsideCell() {
        if (this.cellType.storableInStorageCell()) {
            return true;
        }
        return this.contents != null ? this.contents.isEmpty() : this.tagCompound.getInteger(ITEM_TYPE_TAG) == 0;
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

        // A non-empty storage cell may not be stored recursively inside this one. Kept above the void
        // card with the three refusals before it: all four say "this cell will never hold this", which is
        // not the overflow the card is there to destroy. Upstream voids this one; a player's full cell
        // vanishing because it was pushed at a carded one is not worth mirroring.
        if (what instanceof AEItemKey itemKey) {
            final StorageCell nested = StorageCells.getCellInventory(itemKey.toStack(), null);
            if (nested != null && !nested.canFitInsideCell()) {
                return 0;
            }
        }

        this.followTag();
        final long inserted = this.innerInsert(what, amount, mode, type);

        if (!this.voidOverflow) {
            return inserted;
        }

        // An unformatted cell that can take no new type would otherwise swallow everything the network
        // offers it, including things it never held and could not have started holding.
        if (!this.isPreformatted() && !this.canHoldNewItem(type) && this.contents().get(what) <= 0) {
            return inserted;
        }

        return amount;
    }

    /** What would really fit, before the void card is allowed to say otherwise. */
    private long innerInsert(final AEKey what, final long amount, final Actionable mode, final AEKeyType type) {
        final CellContents stored = this.contents();
        final long currentAmount = stored.get(what);
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
            stored.set(what, currentAmount + toInsert);
            this.saveChanges();
        }

        return toInsert;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        this.followTag();
        final CellContents stored = this.contents();
        final long currentAmount = stored.get(what);
        if (currentAmount <= 0) {
            return 0;
        }

        final long extracted = Math.min(amount, currentAmount);

        if (mode == Actionable.MODULATE) {
            stored.set(what, currentAmount - extracted);
            this.saveChanges();
        }

        return extracted;
    }

    /**
     * On the client, a cell whose contents are in a file answers with the few entries its item names - enough to
     * tell an empty cell from a full one, and to preview it, but not the whole list. {@link #getUnloadedContentsId}
     * says when that is the case.
     */
    @Override
    public void getAvailableStacks(final KeyCounter out) {
        this.followTag();
        for (final Object2LongMap.Entry<AEKey> entry : this.contents().amounts().object2LongEntrySet()) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    /** The id to ask the server for, when all that is known here of this cell is its summary. */
    @Nullable
    public UUID getUnloadedContentsId() {
        return this.contents().isSummary() ? this.readId() : null;
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

        final CellContents stored = this.contents();
        if (stored.isAttached()) {
            this.writeSummary(stored);
        } else if (!stored.isSummary() && CellContentsStore.current() == null) {
            this.writeList(stored);
        }
        // A detached cell on the server has never been written to, so there is nothing to write.

        this.isPersisted = true;
    }

    /** The list an older version keeps in the item, which is also what a cell without a store still uses. */
    private void writeList(final CellContents stored) {
        final NBTTagList list = new NBTTagList();

        for (final Object2LongMap.Entry<AEKey> entry : stored.amounts().object2LongEntrySet()) {
            final NBTTagCompound entryTag = new NBTTagCompound();
            GenericStack.writeTag(entryTag, new GenericStack(entry.getKey(), entry.getLongValue()));
            list.appendTag(entryTag);
        }

        if (list.tagCount() == 0) {
            this.tagCompound.removeTag(ITEMS_TAG);
            this.tagCompound.removeTag(ITEM_TYPE_TAG);
        } else {
            this.tagCompound.setTag(ITEMS_TAG, list);
            this.tagCompound.setInteger(ITEM_TYPE_TAG, list.tagCount());
        }

        if (stored.getTotal() == 0) {
            this.tagCompound.removeTag(ITEM_COUNT_TAG);
        } else {
            this.tagCompound.setLong(ITEM_COUNT_TAG, stored.getTotal());
        }
    }

    private void writeSummary(final CellContents stored) {
        this.tagCompound.removeTag(ITEMS_TAG);

        if (stored.isEmpty()) {
            // An empty cell carries no id, so every empty cell of a kind is the same item again.
            this.tagCompound.removeTag(CELL_ID_MOST_TAG);
            this.tagCompound.removeTag(CELL_ID_LEAST_TAG);
            this.tagCompound.removeTag(ITEM_TYPE_TAG);
            this.tagCompound.removeTag(ITEM_COUNT_TAG);
            this.tagCompound.removeTag(AMOUNT_BY_TYPE_TAG);
            this.tagCompound.removeTag(PREVIEW_TAG);
            this.followedIdTag = null;
            return;
        }

        if (!stored.getId().equals(this.readId())) {
            this.writeId(stored.getId());
        }

        this.tagCompound.setInteger(ITEM_TYPE_TAG, stored.getTypes());
        this.tagCompound.setLong(ITEM_COUNT_TAG, stored.getTotal());

        // One key type leaves nothing to split the count between.
        if (this.keyTypes.size() > 1) {
            final NBTTagCompound byType = new NBTTagCompound();
            for (final AEKeyType type : this.keyTypes) {
                if (stored.getTotal(type) > 0) {
                    byType.setLong(type.getId().toString(), stored.getTotal(type));
                }
            }
            this.tagCompound.setTag(AMOUNT_BY_TYPE_TAG, byType);
        } else {
            this.tagCompound.removeTag(AMOUNT_BY_TYPE_TAG);
        }

        this.tagCompound.setTag(PREVIEW_TAG, writePreview(stored));
    }

    /** The largest entries, largest first. */
    private static NBTTagList writePreview(final CellContents stored) {
        final AEKey[] keys = new AEKey[PREVIEW_SIZE];
        final long[] amounts = new long[PREVIEW_SIZE];
        int size = 0;

        for (final Object2LongMap.Entry<AEKey> entry : stored.amounts().object2LongEntrySet()) {
            final long amount = entry.getLongValue();
            if (size == PREVIEW_SIZE && amount <= amounts[PREVIEW_SIZE - 1]) {
                continue;
            }

            int at = size < PREVIEW_SIZE ? size++ : PREVIEW_SIZE - 1;
            while (at > 0 && amounts[at - 1] < amount) {
                keys[at] = keys[at - 1];
                amounts[at] = amounts[at - 1];
                at--;
            }
            keys[at] = entry.getKey();
            amounts[at] = amount;
        }

        final NBTTagList list = new NBTTagList();
        for (int n = 0; n < size; n++) {
            final NBTTagCompound entryTag = new NBTTagCompound();
            GenericStack.writeTag(entryTag, new GenericStack(previewKey(keys[n]), amounts[n]));
            list.appendTag(entryTag);
        }
        return list;
    }

    /** Anything that is a storage of its own, say, would bring its whole contents back onto this item. */
    private static AEKey previewKey(final AEKey key) {
        if (key instanceof AEItemKey itemKey && itemKey.getTag() != null
                && sizeOf(itemKey.getTag()) > PREVIEW_TAG_LIMIT) {
            return itemKey.dropSecondary();
        }
        return key;
    }

    private static long sizeOf(final NBTTagCompound tag) {
        final CountingOutputStream counter = new CountingOutputStream(ByteStreams.nullOutputStream());
        try {
            CompressedStreamTools.write(tag, new DataOutputStream(counter));
        } catch (final IOException e) {
            return Long.MAX_VALUE;
        }
        return counter.getCount();
    }

    private CellContents contents() {
        if (this.contents == null) {
            this.contents = this.resolveContents();
            this.followedIdTag = this.tagCompound.getTag(CELL_ID_MOST_TAG);

            // Resolving had to change the item: an old list moved to a file, or something unreadable in it dropped.
            if (!this.isPersisted) {
                this.notifyChanged();
            }
        }

        return this.contents;
    }

    private CellContents resolveContents() {
        final CellContentsStore store = CellContentsStore.current();
        final UUID id = this.readId();
        final boolean hasList = this.tagCompound.hasKey(ITEMS_TAG, Constants.NBT.TAG_LIST);

        if (store == null) {
            if (id != null) {
                return this.readSummary();
            }

            final CellContents local = CellContents.detached();
            if (hasList && this.readList(local)) {
                this.isPersisted = false;
            }
            return local;
        }

        if (id != null) {
            final CellContents shared = store.getOrLoad(id);
            if (hasList) {
                // Both at once means the item went back to a version that keeps contents in the item, and was
                // filled there. Neither half is out of date, so they are added together.
                AELog.info("Storage cell %s had contents in its item as well as in its file; they were added together", id);
                this.readList(shared);
                this.tagCompound.removeTag(ITEMS_TAG);
                shared.markDirty();
                this.isPersisted = false;
            }
            return shared;
        }

        if (hasList) {
            final CellContents migrated = CellContents.detached();
            this.readList(migrated);
            store.attach(migrated);
            this.tagCompound.removeTag(ITEMS_TAG);
            this.writeId(migrated.getId());
            migrated.markDirty();
            this.isPersisted = false;
            return migrated;
        }

        // Given an id by the first thing put in, so a cell nothing was ever put into stays a plain item.
        return CellContents.detached();
    }

    private boolean readList(final CellContents into) {
        return CellContentsStore.readList(this.tagCompound.getTagList(ITEMS_TAG, Constants.NBT.TAG_COMPOUND), into,
                String.valueOf(this.i));
    }

    private CellContents readSummary() {
        final Object2LongMap<AEKey> shown = new Object2LongOpenHashMap<>();
        final NBTTagList preview = this.tagCompound.getTagList(PREVIEW_TAG, Constants.NBT.TAG_COMPOUND);
        for (int idx = 0; idx < preview.tagCount(); idx++) {
            try {
                final GenericStack stack = GenericStack.readTag(preview.getCompoundTagAt(idx));
                if (stack != null) {
                    shown.put(stack.what(), stack.amount());
                }
            } catch (final RuntimeException e) {
                // Only a preview; the server decides what happens to an entry that cannot be read.
            }
        }

        final Object2LongMap<AEKeyType> byType = new Object2LongOpenHashMap<>();
        if (this.tagCompound.hasKey(AMOUNT_BY_TYPE_TAG, Constants.NBT.TAG_COMPOUND)) {
            final NBTTagCompound amounts = this.tagCompound.getCompoundTag(AMOUNT_BY_TYPE_TAG);
            for (final String typeId : amounts.getKeySet()) {
                final AEKeyType type = AEKeyTypes.get(new ResourceLocation(typeId));
                if (type != null) {
                    byType.put(type, amounts.getLong(typeId));
                }
            }
        } else if (this.keyTypes.size() == 1) {
            byType.put(this.keyTypes.iterator().next(), this.tagCompound.getLong(ITEM_COUNT_TAG));
        }

        return CellContents.summary(shown, this.tagCompound.getInteger(ITEM_TYPE_TAG), byType);
    }

    /**
     * Follows the item to the contents another inventory on the same item gave it: the first thing put into an
     * empty cell, while this one was already open on it. If both put something in at once - the cell was emptied,
     * which takes its id away, and each gave it a new one - what this one put in goes along.
     */
    private void followTag() {
        final CellContents current = this.contents;
        if (current == null || current.isSummary()) {
            return;
        }

        final NBTBase idTag = this.tagCompound.getTag(CELL_ID_MOST_TAG);
        if (idTag == this.followedIdTag) {
            return;
        }
        this.followedIdTag = idTag;

        final UUID id = this.readId();
        final CellContentsStore store = CellContentsStore.current();
        if (id == null || id.equals(current.getId()) || store == null) {
            return;
        }

        final boolean carried = !current.isEmpty();
        final CellContents moved = store.getOrLoad(id);
        moved.takeAll(current);
        this.contents = moved;

        if (carried) {
            current.markDirty();
            this.markDirty();
        }
    }

    /** The first thing put into a cell gives it an id - or joins the one another inventory on it just gave it. */
    private void attachIfDetached() {
        final CellContents detached = this.contents();
        if (detached.isAttached() || detached.isSummary()) {
            return;
        }

        final CellContentsStore store = CellContentsStore.current();
        if (store == null) {
            return;
        }

        final UUID id = this.readId();
        if (id != null) {
            final CellContents shared = store.getOrLoad(id);
            shared.takeAll(detached);
            this.contents = shared;
            this.followedIdTag = this.tagCompound.getTag(CELL_ID_MOST_TAG);
        } else {
            store.attach(detached);
            this.writeId(detached.getId());
        }
    }

    @Nullable
    private UUID readId() {
        return this.tagCompound.hasUniqueId(CELL_ID_TAG) ? this.tagCompound.getUniqueId(CELL_ID_TAG) : null;
    }

    private void writeId(final UUID id) {
        this.tagCompound.setUniqueId(CELL_ID_TAG, id);
        this.followedIdTag = this.tagCompound.getTag(CELL_ID_MOST_TAG);
    }

    private void saveChanges() {
        this.attachIfDetached();
        this.markDirty();
    }

    private void markDirty() {
        this.isPersisted = false;
        this.contents().markDirty();
        this.notifyChanged();
    }

    private void notifyChanged() {
        if (this.container != null) {
            this.container.saveChanges();
        } else {
            // If there is no ISaveProvider, store to NBT immediately.
            this.persist();
        }
    }
}
