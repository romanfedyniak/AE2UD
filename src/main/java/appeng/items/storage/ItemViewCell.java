/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.items.storage;


import appeng.api.config.FuzzyMode;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.items.AEBaseItem;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellUpgrades;
import appeng.util.Platform;
import appeng.util.prioritylist.IPartitionList;
import appeng.util.prioritylist.MergedPriorityList;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;


public class ItemViewCell extends AEBaseItem implements ICellWorkbenchItem {
    public ItemViewCell() {
        this.setMaxStackSize(1);
    }

    /**
     * Builds the combined filter for a set of view-cell slots (a terminal's or storage bus's view-cell
     * inventory row).
     * <p/>
     * Replaces the old {@code IPartitionList<IAEItemStack>} return type. The return type is
     * {@link AEKeyFilter} - the frozen, type-erased predicate over {@link AEKey} - rather than the
     * {@code appeng.util.prioritylist.IPartitionList} used internally to build it, because a view cell
     * must be able to filter keys of any registered {@link appeng.api.stacks.AEKeyType}, not just items,
     * and {@code AEKeyFilter} is the api-level type meant for exactly that role (see its javadoc:
     * "Replaces the various per-channel filter interfaces"). Internally the merge/fuzzy/inverter logic
     * is unchanged - it is still built out of {@link IPartitionList}/{@link MergedPriorityList}, exactly
     * as {@code appeng.me.storage.BasicCellInventory} builds a cell's own partition list - and the final
     * result is exposed as an {@code AEKeyFilter} via a method reference ({@code list::isListed} already
     * has the {@code (AEKey) -> boolean} shape {@code AEKeyFilter.matches} requires).
     * <p/>
     * <b>Never returns null.</b> If no view cell in {@code list} configures anything (including an empty
     * or all-{@code null}/{@link ItemStack#EMPTY} array), this returns {@link AEKeyFilter#all()} - i.e.
     * "no filtering" - which is the same behaviour the old null return produced at every call site
     * ({@code if (filter != null && !filter.isListed(x)) skip}), just without forcing callers to
     * null-check. {@code appeng.util.Platform.extractItemsByRecipe} now takes an {@code AEKeyFilter}
     * parameter as well, so its three call sites ({@code SlotCraftingTerm},
     * {@code ContainerPatternEncoder}, {@code PacketJEIRecipe}) pass this method's result straight in.
     */
    public static AEKeyFilter createFilter(final ItemStack[] list) {
        MergedPriorityList myMergedList = null;

        for (final ItemStack currentViewCell : list) {
            if (currentViewCell == null || currentViewCell.isEmpty()) {
                continue;
            }

            if (currentViewCell.getItem() instanceof ItemViewCell) {
                final IPartitionList.Builder builder = IPartitionList.builder();

                final ICellWorkbenchItem vc = (ICellWorkbenchItem) currentViewCell.getItem();
                final IItemHandler upgrades = vc.getUpgradesInventory(currentViewCell);
                final IItemHandler config = vc.getConfigInventory(currentViewCell);
                final FuzzyMode fzMode = vc.getFuzzyMode(currentViewCell);

                boolean hasInverter = false;
                boolean hasFuzzy = false;

                for (int x = 0; x < upgrades.getSlots(); x++) {
                    final ItemStack is = upgrades.getStackInSlot(x);
                    if (!is.isEmpty() && is.getItem() instanceof IUpgradeModule) {
                        final Upgrades u = ((IUpgradeModule) is.getItem()).getType(is);
                        if (u != null) {
                            switch (u) {
                                case FUZZY:
                                    hasFuzzy = true;
                                    break;
                                case INVERTER:
                                    hasInverter = true;
                                    break;
                                default:
                            }
                        }
                    }
                }

                boolean any = false;
                for (int x = 0; x < config.getSlots(); x++) {
                    final ItemStack is = config.getStackInSlot(x);
                    if (!is.isEmpty()) {
                        final AEKey key = keyOf(is);
                        if (key != null) {
                            builder.add(key);
                            any = true;
                        }
                    }
                }

                if (hasFuzzy) {
                    builder.fuzzyMode(fzMode);
                }

                if (any) {
                    if (myMergedList == null) {
                        myMergedList = new MergedPriorityList();
                    }
                    myMergedList.addNewList(builder.build(), !hasInverter);
                }
            }
        }

        return myMergedList == null ? AEKeyFilter.all() : myMergedList::isListed;
    }

    /**
     * Resolves a view-cell config slot's {@link ItemStack} back into the {@link AEKey} it stands for:
     * the item itself for ordinary item stacks, or the wrapped key for a {@link GenericStack} placeholder
     * (fluids and any other non-item type). Mirrors {@code appeng.core.api.ApiClientHelper#keyOf}, which
     * does the same translation for the cell tooltip.
     */
    private static AEKey keyOf(final ItemStack is) {
        if (GenericStack.isWrapped(is)) {
            final GenericStack wrapped = GenericStack.unwrapItemStack(is);
            return wrapped == null ? null : wrapped.what();
        }
        return AEItemKey.of(is);
    }

    @Override
    public boolean isEditable(final ItemStack is) {
        return true;
    }

    @Override
    public IItemHandler getUpgradesInventory(final ItemStack is) {
        return new CellUpgrades(is, 2);
    }

    @Override
    public IItemHandler getConfigInventory(final ItemStack is) {
        return new CellConfig(is);
    }

    @Override
    public FuzzyMode getFuzzyMode(final ItemStack is) {
        final String fz = Platform.openNbtData(is).getString("FuzzyMode");
        try {
            return FuzzyMode.valueOf(fz);
        } catch (final Throwable t) {
            return FuzzyMode.IGNORE_ALL;
        }
    }

    @Override
    public void setFuzzyMode(final ItemStack is, final FuzzyMode fzMode) {
        Platform.openNbtData(is).setString("FuzzyMode", fzMode.name());
    }
}
