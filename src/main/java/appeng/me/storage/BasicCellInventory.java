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

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
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
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
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
 * The contents of a standard storage cell, for any single {@link AEKeyType}.
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
    private final AEKeyType keyType;
    private final IPartitionList partitionList;
    private final IncludeExclude partitionListMode;
    private final boolean sticky;
    private int maxItemTypes;
    private int storedItemTypes;
    private long storedItemCount;
    @Nullable
    private Object2LongMap<AEKey> storedAmounts;
    private boolean isPersisted = true;

    private BasicCellInventory(final IBasicCellItem cellType, final ItemStack o, @Nullable final ISaveProvider container) {
        this.i = o;
        this.cellType = cellType;
        this.keyType = cellType.getKeyType();
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

        for (int x = 0; x < upgrades.getSlots(); x++) {
            final ItemStack is = upgrades.getStackInSlot(x);
            if (!is.isEmpty() && is.getItem() instanceof IUpgradeModule) {
                final Upgrades u = ((IUpgradeModule) is.getItem()).getType(is);
                if (u == Upgrades.FUZZY) {
                    hasFuzzy = true;
                } else if (u == Upgrades.INVERTER) {
                    hasInverter = true;
                } else if (u == Upgrades.STICKY) {
                    hasSticky = true;
                }
            }
        }
        this.sticky = hasSticky;

        final IPartitionList.Builder builder = IPartitionList.builder();
        for (int x = 0; x < config.getSlots(); x++) {
            final ItemStack is = config.getStackInSlot(x);
            if (!is.isEmpty()) {
                final AEItemKey key = AEItemKey.of(is);
                builder.add(key);
            }
        }

        if (hasFuzzy) {
            builder.fuzzyMode(cellType.getFuzzyMode(o));
        }

        this.partitionListMode = hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;
        this.partitionList = builder.build();
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

    public int getBytesPerType() {
        return this.cellType.getBytesPerType(this.i);
    }

    public boolean canHoldNewItem() {
        final long bytesFree = this.getFreeBytes();
        return (bytesFree > this.getBytesPerType() || (bytesFree == this.getBytesPerType() && this.getUnusedItemCount() > 0))
                && this.getRemainingItemTypes() > 0;
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

    public long getStoredItemTypes() {
        return this.storedItemTypes;
    }

    public long getRemainingItemTypes() {
        final long basedOnStorage = this.getFreeBytes() / this.getBytesPerType();
        final long baseOnTotal = this.getTotalItemTypes() - this.getStoredItemTypes();
        return Math.min(basedOnStorage, baseOnTotal);
    }

    public long getUsedBytes() {
        final long bytesForItemCount = (this.getStoredItemCount() + this.getUnusedItemCount()) / this.keyType.getAmountPerByte();
        return this.getStoredItemTypes() * this.getBytesPerType() + bytesForItemCount;
    }

    public long getRemainingItemCount() {
        final long remaining = this.getFreeBytes() * this.keyType.getAmountPerByte() + this.getUnusedItemCount();
        return remaining > 0 ? remaining : 0;
    }

    public int getUnusedItemCount() {
        final int div = (int) (this.getStoredItemCount() % this.keyType.getAmountPerByte());

        if (div == 0) {
            return 0;
        }

        return this.keyType.getAmountPerByte() - div;
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
        if (this.getRemainingItemCount() > 0) {
            return CellState.TYPES_FULL;
        }
        return CellState.FULL;
    }

    @Override
    public double getIdleDrain() {
        return this.cellType.getIdleDrain();
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (amount <= 0 || !this.keyType.contains(what)) {
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
        long remainingItemCount = this.getRemainingItemCount();

        if (currentAmount <= 0) {
            if (!this.canHoldNewItem()) {
                // No room for a new type.
                return 0;
            }

            remainingItemCount -= (long) this.getBytesPerType() * this.keyType.getAmountPerByte();
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
        for (final Object2LongMap.Entry<AEKey> entry : Object2LongMaps.fastIterable(this.getCellItems())) {
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

        for (final Object2LongMap.Entry<AEKey> entry : Object2LongMaps.fastIterable(this.getCellItems())) {
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

        long count = 0;
        for (final Object2LongMap.Entry<AEKey> entry : Object2LongMaps.fastIterable(this.getCellItems())) {
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
