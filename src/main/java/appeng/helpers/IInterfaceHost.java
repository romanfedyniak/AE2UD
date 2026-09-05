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

package appeng.helpers;


import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.IPatternContainer;
import appeng.api.networking.crafting.MachineIdentity;
import appeng.api.stacks.GenericStack;
import appeng.api.util.DimensionalCoord;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.IItemHandler;

import java.util.EnumSet;


public interface IInterfaceHost extends ICraftingProvider, IUpgradeableHost, ICraftingRequester,
        ICraftPriorityTarget, IPatternContainer {

    DualityInterface getInterfaceDuality();

    EnumSet<EnumFacing> getTargets();

    TileEntity getTileEntity();

    void saveChanges();

    default void onStackReturnNetwork(GenericStack stack) {
        getInterfaceDuality().onStackReturnedToNetwork(stack);
    }

    /** The container is opened on the host rather than on the duality, so the host has to answer for it. */
    @Override
    default int getCraftPriority() {
        return getInterfaceDuality().getCraftPriority();
    }

    @Override
    default void setCraftPriority(final int priority) {
        getInterfaceDuality().setCraftPriority(priority);
    }

    // An interface is a pattern container, and answers for one out of its duality like everything else here.

    @Override
    default boolean isVisibleInTerminal() {
        return getInterfaceDuality().getConfigManager().getSetting(Settings.PATTERN_ACCESS_TERMINAL) == YesNo.YES;
    }

    @Override
    default IItemHandler getTerminalPatternInventory() {
        return getInterfaceDuality().getPatterns();
    }

    @Override
    default int getUsablePatternSlots() {
        return getInterfaceDuality().getUsablePatternSlots();
    }

    @Override
    default boolean canAccept(final ItemStack pattern, final ICraftingPatternDetails details) {
        return getInterfaceDuality().canAcceptPattern(details);
    }

    @Override
    default MachineIdentity getTerminalIdentity() {
        return getInterfaceDuality().getMachineIdentity();
    }

    @Override
    default MachineIdentity getTerminalIdentity(final ItemStack pattern, final ICraftingPatternDetails details) {
        return getInterfaceDuality().identifyFor(details);
    }

    @Override
    default DimensionalCoord getTerminalLocation() {
        return getInterfaceDuality().getLocation();
    }

    @Override
    default long getTerminalSortOrder() {
        return getInterfaceDuality().getSortValue();
    }

    @Override
    default boolean isFakeCrafting() {
        return getInterfaceDuality().isFakeCrafting();
    }
}
