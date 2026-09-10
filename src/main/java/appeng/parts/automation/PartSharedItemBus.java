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

package appeng.parts.automation;

import appeng.api.config.RedstoneMode;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.upgrades.CardTraits;
import appeng.api.util.IConfigManager;
import appeng.me.GridAccessException;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.util.InventoryAdaptor;
import appeng.util.UpgradeSpeedCalculations;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;


public abstract class PartSharedItemBus extends PartUpgradeable implements IGridTickable {

    private final AppEngInternalAEInventory config = new AppEngInternalAEInventory(this, 63);
    private boolean lastRedstone = false;
    /** A rising edge seen in pulse mode, acted on at the next tick rather than inside the block update. */
    private boolean pendingPulse = false;

    public PartSharedItemBus(final ItemStack is) {
        super(is);
    }

    @Override
    protected int getUpgradeSlots() {
        return 5;
    }

    @Override
    public void upgradesChanged() {
        this.updateRedstoneState();
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        super.updateSetting(manager, settingName, newValue);

        // Also called while the part is read from disk, before it has a world.
        if (this.getHost() == null || this.getHost().getTile() == null || this.getHost().getTile().getWorld() == null) {
            return;
        }

        this.lastRedstone = this.getHost().hasRedstone(this.getSide());
        this.updateRedstoneState();
    }

    @Override
    public void readFromNBT(final NBTTagCompound extra) {
        super.readFromNBT(extra);
        this.getConfig().readFromNBT(extra, "config");
        this.pendingPulse = this.isInPulseMode() && extra.getBoolean("pendingPulse");
    }

    @Override
    public void writeToNBT(final NBTTagCompound extra) {
        super.writeToNBT(extra);
        this.getConfig().writeToNBT(extra, "config");
        if (this.isInPulseMode() && this.pendingPulse) {
            extra.setBoolean("pendingPulse", true);
        }
    }

    @Override
    public void addToWorld() {
        super.addToWorld();

        // A pulse is a change, so the level it changes from has to be known from the start.
        this.lastRedstone = this.getHost().hasRedstone(this.getSide());
        if (this.pendingPulse) {
            this.wake();
        }
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        if (name.equals("config")) {
            return this.getConfig();
        }

        return super.getInventoryByName(name);
    }

    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        final boolean powered = this.getHost().hasRedstone(this.getSide());
        if (powered == this.lastRedstone) {
            return;
        }
        this.lastRedstone = powered;

        if (!this.isInPulseMode()) {
            this.updateRedstoneState();
        } else if (powered && !this.pendingPulse) {
            this.pendingPulse = true;
            this.wake();
        }
    }

    @Override
    protected boolean isSleeping() {
        return !(this.isInPulseMode() && this.pendingPulse) && super.isSleeping();
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        // The mode or the signal may have changed between being woken and this tick.
        if (this.isSleeping()) {
            return TickRateModulation.SLEEP;
        }

        this.pendingPulse = false;
        final TickRateModulation worked = this.doBusWork();

        // One tick of work per pulse.
        return this.isSleeping() ? TickRateModulation.SLEEP : worked;
    }

    private boolean isInPulseMode() {
        return this.isInstalled(CardTraits.REDSTONE) && this.getRSMode() == RedstoneMode.SIGNAL_PULSE;
    }

    protected InventoryAdaptor getHandler() {
        final TileEntity self = this.getHost().getTile();
        final TileEntity target = this.getTileEntity(self, self.getPos().offset(this.getSide().getFacing()));

        return InventoryAdaptor.getAdaptor(target, this.getSide().getFacing().getOpposite());
    }

    private TileEntity getTileEntity(final TileEntity self, final BlockPos pos) {
        final World w = self.getWorld();

        if (w.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4) != null) {
            return w.getTileEntity(pos);
        }

        return null;
    }

    /**
     * Two rows of the filter are always live, and each capacity card adds one more.
     */
    protected int availableSlots() {
        return Math.min(18 + this.getInstalledPoints(CardTraits.CAPACITY) * 9, this.getConfig().getSlots());
    }

    protected int calculateItemsToSend() {
        return UpgradeSpeedCalculations.itemBusOperations(this.getInstalledPoints(CardTraits.SPEED));
    }

    /**
     * Checks if the bus can actually do something.
     * <p>
     * Currently this tests if the chunk for the target is actually loaded.
     *
     * @return true, if the the bus should do its work.
     */
    protected boolean canDoBusWork() {
        final TileEntity self = this.getHost().getTile();
        final BlockPos selfPos = self.getPos().offset(this.getSide().getFacing());
        final int xCoordinate = selfPos.getX();
        final int zCoordinate = selfPos.getZ();
        final World world = self.getWorld();

        return world != null && world.getChunkProvider().getLoadedChunk(xCoordinate >> 4, zCoordinate >> 4) != null;
    }

    private void updateRedstoneState() {
        if (!this.isInPulseMode()) {
            this.pendingPulse = false;
        }

        if (this.isSleeping()) {
            try {
                this.getProxy().getTick().sleepDevice(this.getProxy().getNode());
            } catch (final GridAccessException e) {
                // :P
            }
        } else {
            this.wake();
        }
    }

    /**
     * Alerted rather than only woken: that also puts it back on its fastest rate, and a bus that slowed down while
     * idle would otherwise wait out its slow rate and miss a short signal.
     */
    private void wake() {
        try {
            final ITickManager tick = this.getProxy().getTick();
            if (!tick.alertDevice(this.getProxy().getNode())) {
                tick.wakeDevice(this.getProxy().getNode());
            }
        } catch (final GridAccessException e) {
            // :P
        }
    }

    protected abstract TickRateModulation doBusWork();

    AppEngInternalAEInventory getConfig() {
        return this.config;
    }
}
