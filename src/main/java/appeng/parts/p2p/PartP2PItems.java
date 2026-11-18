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

package appeng.parts.p2p;


import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartModel;
import appeng.api.parts.P2PTunnelModels;
import appeng.core.AppEng;
import appeng.core.settings.TickRates;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.me.cache.helpers.TunnelCollection;
import appeng.util.Platform;
import appeng.util.inv.WrapperChainedItemHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;


public class PartP2PItems extends PartP2PTunnel<PartP2PItems> implements IItemHandler, IGridTickable {
    private static final float POWER_DRAIN = 2.0f;
    private static final P2PTunnelModels MODELS = new P2PTunnelModels(new ResourceLocation(AppEng.MOD_ID, "part/p2p/p2p_tunnel_items"));
    // The inputs being asked on this thread. Two pairs facing each other's inputs ask each other in a circle, from
    // every handler method and from building a destination, which sums the slots of what it chains.
    private static final ThreadLocal<Set<PartP2PItems>> ASKING = ThreadLocal.withInitial(
            () -> Collections.newSetFromMap(new IdentityHashMap<>()));

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    private int oldSize = 0;
    private boolean requested;
    private IItemHandler cachedInv;
    // The outputs the cached destination was built from, the tiles they faced then, and the world tick that was
    // last found to still hold.
    private List<PartP2PItems> cachedOutputs = Collections.emptyList();
    private List<TileEntity> cachedTargets = Collections.emptyList();
    private long checkedTick = Long.MIN_VALUE;
    // On an output: the tile in front and its handler, as last handed to an input.
    private TileEntity target;
    private IItemHandler targetHandler;

    public PartP2PItems(final ItemStack is) {
        super(is);
    }

    /**
     * Every inserted stack makes a chest tell its neighbours (the comparator update), so a destination thrown away
     * on every neighbour change was built again for every stack delivered. It is only when what an output faces is
     * no longer the same tile, or the tile no longer hands out the same handler.
     */
    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        if (!this.isOutput() || this.targetUnchanged()) {
            return;
        }
        try {
            for (PartP2PItems input : this.getInputs()) {
                if (input != null) {
                    input.onTunnelNetworkChange();
                }
            }
        } catch (GridAccessException e) {
            e.printStackTrace();
        }
    }

    private boolean targetUnchanged() {
        final TileEntity te = this.getProxy().isActive() ? this.getLoadedFacingTile() : null;
        if (te != this.target) {
            return false;
        }
        final EnumFacing side = this.getSide().getFacing().getOpposite();
        final IItemHandler handler = te != null && te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side)
                ? te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side)
                : null;
        return handler == this.targetHandler;
    }

    private IItemHandler getDestination() {
        this.requested = true;

        if (this.cachedInv != null && this.outputsStillThere()) {
            return this.cachedInv;
        }

        final List<IItemHandler> outs = new ArrayList<IItemHandler>();
        final List<PartP2PItems> used = new ArrayList<>();
        final List<TileEntity> faced = new ArrayList<>();
        final TunnelCollection<PartP2PItems> itemTunnels;

        try {
            itemTunnels = this.getOutputs();
        } catch (final GridAccessException e) {
            return EmptyHandler.INSTANCE;
        }

        for (final PartP2PItems t : itemTunnels) {
            final IItemHandler inv = t.getOutputInv();
            used.add(t);
            faced.add(t.target);
            if (inv != null && inv != this) {
                if (Platform.getRandomInt() % 2 == 0) {
                    outs.add(inv);
                } else {
                    outs.add(0, inv);
                }
            }
        }

        this.cachedOutputs = used;
        this.cachedTargets = faced;
        this.checkedTick = this.getTile().getWorld().getTotalWorldTime();
        return this.cachedInv = new WrapperChainedItemHandler(outs.toArray(new IItemHandler[outs.size()]));
    }

    /**
     * Whether every output still faces the tile it was built with, looked at once a tick: a chest whose chunk
     * unloaded is a stale object, saved already, and what went into it or came out of it would be lost or copied
     * when the chunk loads again.
     */
    private boolean outputsStillThere() {
        final long now = this.getTile().getWorld().getTotalWorldTime();
        if (now == this.checkedTick) {
            return true;
        }
        for (int i = 0; i < this.cachedOutputs.size(); i++) {
            final PartP2PItems output = this.cachedOutputs.get(i);
            final TileEntity facing = output.getProxy().isActive() ? output.getLoadedFacingTile() : null;
            if (facing != this.cachedTargets.get(i)) {
                return false;
            }
        }
        this.checkedTick = now;
        return true;
    }

    private IItemHandler getOutputInv() {
        TileEntity te = null;
        IItemHandler ret = null;
        if (this.getProxy().isActive()) {
            final EnumFacing facing = this.getSide().getFacing();
            te = this.getLoadedFacingTile();

            if (te != null && te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite())) {
                ret = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite());
            }
        }
        this.target = te;
        this.targetHandler = ret;
        return ret;
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(TickRates.ItemTunnel.getMin(), TickRates.ItemTunnel.getMax(), false, false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        final boolean wasReq = this.requested;

        if (this.requested && this.cachedInv != null) {
            ((WrapperChainedItemHandler) this.cachedInv).cycleOrder();
        }

        this.requested = false;
        return wasReq ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    @Override
    public void onTunnelNetworkChange() {
        if (!this.isOutput()) {
            this.cachedInv = null;
            final int olderSize = this.oldSize;
            this.oldSize = this.getSlots();
            if (olderSize != this.oldSize) {
                this.getHost().notifyNeighbors();
            }
        } else {
            try {
                for (PartP2PItems input : this.getInputs()) {
                    if (input != null) {
                        input.getHost().notifyNeighbors();
                    }
                }
            } catch (GridAccessException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean hasCapability(Capability<?> capabilityClass) {
        if (capabilityClass == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }

        return super.hasCapability(capabilityClass);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capabilityClass) {
        if (capabilityClass == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) this;
        }

        return super.getCapability(capabilityClass);
    }

    @Override
    public int getSlots() {
        if (!ASKING.get().add(this)) {
            return 0;
        }
        try {
            return this.getDestination().getSlots();
        } finally {
            ASKING.get().remove(this);
        }
    }

    @Override
    public ItemStack getStackInSlot(final int i) {
        if (!ASKING.get().add(this)) {
            return ItemStack.EMPTY;
        }
        try {
            return this.getDestination().getStackInSlot(i);
        } finally {
            ASKING.get().remove(this);
        }
    }

    @Override
    public ItemStack insertItem(final int slot, final ItemStack stack, boolean simulate) {
        if (!ASKING.get().add(this)) {
            return stack;
        }
        try {
            return this.getDestination().insertItem(slot, stack, simulate);
        } finally {
            ASKING.get().remove(this);
        }
    }

    @Override
    public ItemStack extractItem(final int slot, final int amount, boolean simulate) {
        if (!ASKING.get().add(this)) {
            return ItemStack.EMPTY;
        }
        try {
            return this.getDestination().extractItem(slot, amount, simulate);
        } finally {
            ASKING.get().remove(this);
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        if (!ASKING.get().add(this)) {
            return 0;
        }
        try {
            return this.getDestination().getSlotLimit(slot);
        } finally {
            ASKING.get().remove(this);
        }
    }

    public float getPowerDrainPerTick() {
        return POWER_DRAIN;
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }
}
