/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.items.contents;


import appeng.api.config.*;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.api.util.IConfigManager;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.me.storage.DelegatingMEInventory;
import appeng.me.storage.NullInventory;
import appeng.util.ConfigManager;
import appeng.util.Platform;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;


/**
 * The portable cell's own inventory/config view: {@link IPortableCell} is {@code ITerminalHost & MEStorage &
 * IEnergySource & IGuiItemObject}, and since {@link StorageCell} is itself an {@link MEStorage}, this class
 * only needs to forward storage calls to the cell it wraps - exactly like {@code appeng.helpers
 * .WirelessTerminalGuiObject} (wave 2) forwards to its network storage - rather than layering a monitor on
 * top of it as the old {@code MEMonitorHandler<IAEItemStack>}-based version did.
 * <p/>
 * <b>Live-update note (CONTRACT.md §10, "Third case"):</b> the old {@code injectItems}/{@code extractItems}
 * overrides here called {@code notifyListenersOfChange} to push deltas to whatever GUI/monitor was watching
 * this object. That mechanism (the {@code IMEMonitor}/{@code MEMonitorHandler} listener layer) no longer
 * exists, and CONTRACT.md §10 explicitly defers its replacement to wave 4 ("Portable cell / view-only cell
 * terminals ... There is no replacement yet ... Wave 4 needs a small push interface on this path"). Per
 * rule 2 this class does not invent one; it matches the precedent wave 2 already set in
 * {@code WirelessTerminalGuiObject}, which forwards {@code insert}/{@code extract} with no notification
 * step either. Reads still work; only client-side live refresh of an open portable-cell terminal is
 * pending wave 4's design.
 */
public class PortableCellViewer extends DelegatingMEInventory implements IPortableCell, IInventorySlotAware {

    private final ItemStack target;
    private final IAEItemPowerStorage ips;
    private final int inventorySlot;

    public PortableCellViewer(final ItemStack is, final int slot) {
        super(resolveCellInventory(is));
        this.ips = (IAEItemPowerStorage) is.getItem();
        this.target = is;
        this.inventorySlot = slot;
    }

    /**
     * @return the item's own {@link StorageCell}, or a permanently-empty {@link NullInventory} if {@code is}
     *         does not (currently) resolve to one, so the constructor stays total instead of throwing - the
     *         same fallback {@code TileChest}/{@code TileDrive} use when a cell slot is unresolvable.
     */
    private static MEStorage resolveCellInventory(final ItemStack is) {
        final StorageCell cell = StorageCells.getCellInventory(is, null);
        return cell != null ? cell : NullInventory.of();
    }

    @Override
    public int getInventorySlot() {
        return this.inventorySlot;
    }

    @Override
    public ItemStack getItemStack() {
        return this.target;
    }

    @Override
    public double extractAEPower(double amt, final Actionable mode, final PowerMultiplier usePowerMultiplier) {
        amt = usePowerMultiplier.multiply(amt);

        if (mode == Actionable.SIMULATE) {
            return usePowerMultiplier.divide(Math.min(amt, this.ips.getAECurrentPower(this.target)));
        }

        return usePowerMultiplier.divide(this.ips.extractAEPower(this.target, amt, Actionable.MODULATE));
    }

    @Override
    public MEStorage getInventory() {
        return this;
    }

    @Override
    public IConfigManager getConfigManager() {
        final ConfigManager out = new ConfigManager((manager, settingName, newValue) ->
        {
            final NBTTagCompound data = Platform.openNbtData(PortableCellViewer.this.target);
            manager.writeToNBT(data);
        });

        out.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        out.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        out.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);

        out.readFromNBT(Platform.openNbtData(this.target).copy());
        return out;
    }
}
