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

package appeng.parts.p2p;


import appeng.api.implementations.tiles.ICraftingMachine;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.MachineIdentity;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartModel;
import appeng.core.settings.TickRates;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.ItemStackHelper;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.util.InventoryAdaptor;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * Lends an ME Interface's faces to somewhere else. The input stands in front of the interface and is what
 * the interface sees as a machine; every output stands in for the interface beside a machine of its own, so
 * one interface can drive as many machines as there are outputs.
 */
public class PartP2PInterface extends PartP2PTunnel<PartP2PInterface> implements ICraftingMachine, IGridTickable {

    private static final P2PModels MODELS = new P2PModels("part/p2p/p2p_tunnel_interface");
    private static final float POWER_DRAIN = 2.0f;

    /** How many tunnels a chain may be followed back before the interface at its head is given up on. */
    private static final int MAX_CHAIN = 16;

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    /**
     * Output side: what the machine in front has not taken yet. One queue per tunnel rather than one for the
     * interface, so a machine that has backed up does not stop the interface feeding the other outputs.
     */
    private final List<ItemStack> waitingToSend = new ArrayList<>();

    /** Input side: which output to try first, so consecutive pushes spread over the machines. */
    private int nextOutput;

    /** An output may face another tunnel's input; this stops a pair pointed at each other from recursing. */
    private boolean visiting;

    public PartP2PInterface(final ItemStack is) {
        super(is);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }

    public float getPowerDrainPerTick() {
        return POWER_DRAIN;
    }

    // ------------------------------------------------------------------ the interface's side

    @Override
    public boolean acceptsPlans() {
        return !this.isOutput() && this.getProxy().isActive();
    }

    /**
     * Only if every machine that could be reached destroys such a container. The answer covers the whole
     * tunnel while the pattern lands in just one machine, so one output that would hand the container back
     * is enough to refuse - it would mint a bucket out of a fluid on every craft.
     */
    @Override
    public boolean acceptsFabricatedContainers() {
        if (this.isOutput() || this.visiting) {
            return false;
        }

        this.visiting = true;
        try {
            final List<PartP2PInterface> outputs = this.getOutputList();
            if (outputs.isEmpty()) {
                return false;
            }

            for (final PartP2PInterface output : outputs) {
                final ICraftingMachine machine = output.getFacingMachine();
                if (machine == null || !machine.acceptsFabricatedContainers()) {
                    return false;
                }
            }
            return true;
        } finally {
            this.visiting = false;
        }
    }

    @Override
    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table,
            final EnumFacing ejectionDirection) {
        if (this.isOutput() || !this.getProxy().isActive() || this.visiting) {
            return false;
        }

        this.visiting = true;
        try {
            final List<PartP2PInterface> outputs = this.getOutputList();
            final int count = outputs.size();

            for (int i = 0; i < count; i++) {
                final int index = Math.floorMod(this.nextOutput + i, count);
                if (outputs.get(index).accept(patternDetails, table, this.isSourceBlocking())) {
                    // Start at the next one, so a second pattern in the same tick goes to a second machine.
                    this.nextOutput = Math.floorMod(index + 1, count);
                    return true;
                }
            }
            return false;
        } finally {
            this.visiting = false;
        }
    }

    /**
     * Whether the interface being served has blocking mode on. It is the interface's setting rather than the
     * tunnel's, but it is applied per output: a machine that still holds something is skipped and the next
     * one tried, which is the whole point of driving many machines from one interface.
     */
    private boolean isSourceBlocking() {
        final DualityInterface duality = this.getServedInterface();
        return duality != null && duality.isBlocking();
    }

    /**
     * The interface this tunnel's input stands in front of, whichever side of the tunnel this is. Down a
     * chain the input faces the previous tunnel's output rather than the interface itself, so the walk
     * carries on from there; the depth is capped rather than tracked, since a ring has no interface to find
     * however far it is followed.
     */
    @Nullable
    private DualityInterface getServedInterface() {
        return this.getServedInterface(MAX_CHAIN);
    }

    @Nullable
    private DualityInterface getServedInterface(final int remainingHops) {
        if (remainingHops <= 0) {
            return null;
        }

        final PartP2PInterface input = this.isOutput() ? this.getInputTunnel() : this;
        if (input == null) {
            return null;
        }

        final TileEntity te = input.getFacingTile();
        final EnumFacing facing = input.getFacingSide();

        if (te instanceof IInterfaceHost) {
            return ((IInterfaceHost) te).getInterfaceDuality();
        }
        if (te instanceof IPartHost) {
            final IPart part = ((IPartHost) te).getPart(facing);
            if (part instanceof IInterfaceHost) {
                return ((IInterfaceHost) part).getInterfaceDuality();
            }
            if (part instanceof PartP2PInterface) {
                return ((PartP2PInterface) part).getServedInterface(remainingHops - 1);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the machine's side

    /**
     * Takes a pattern the interface has already laid out and hands it to the machine in front, all or
     * nothing. A machine that speaks {@link ICraftingMachine} - including another tunnel's input - is given
     * the table directly; anything else is loaded through the send queue.
     */
    private boolean accept(final ICraftingPatternDetails patternDetails, final InventoryCrafting table,
            final boolean blocking) {
        if (!this.isOutput() || !this.getProxy().isActive() || this.hasItemsToSend()) {
            return false;
        }

        final TileEntity te = this.getFacingTile();
        if (te == null) {
            return false;
        }

        final EnumFacing facing = this.getFacingSide();
        final ICraftingMachine machine = ICraftingMachine.of(te, facing);
        if (machine != null && machine.acceptsPlans()) {
            return machine.pushPattern(patternDetails, table, facing);
        }

        final InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, facing);
        if (ad == null) {
            return false;
        }

        if (blocking && ad.containsItems()) {
            return false;
        }

        if (!DualityInterface.acceptsItems(ad, table)) {
            return false;
        }

        for (int x = 0; x < table.getSizeInventory(); x++) {
            final ItemStack is = table.getStackInSlot(x);
            if (!is.isEmpty()) {
                this.waitingToSend.add(is.copy());
            }
        }

        this.pushItemsOut();
        return true;
    }

    public boolean hasItemsToSend() {
        return !this.waitingToSend.isEmpty();
    }

    /**
     * Drains the queue into the machine in front. Mirrors {@code DualityInterface.pushItemsOut} minus the
     * neighbouring-network case: a tunnel is never pointed at another network's interface, because such an
     * interface answers as a machine and is fed the table whole.
     */
    private void pushItemsOut() {
        if (this.waitingToSend.isEmpty()) {
            return;
        }

        final TileEntity te = this.getFacingTile();
        if (te == null) {
            return;
        }

        final InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, this.getFacingSide());
        if (ad == null) {
            return;
        }

        final Iterator<ItemStack> i = this.waitingToSend.iterator();
        while (i.hasNext()) {
            final ItemStack whatToSend = i.next();
            final ItemStack result = ad.addItems(whatToSend);

            if (result.isEmpty()) {
                i.remove();
            } else {
                whatToSend.setCount(result.getCount());
                whatToSend.setTagCompound(result.getTagCompound());
            }
        }
    }

    /** Whether nothing more can be pushed at the moment - what the served interface reports as being busy. */
    public boolean isBlocked(final boolean blocking) {
        if (this.isOutput() || this.visiting) {
            return true;
        }

        this.visiting = true;
        try {
            for (final PartP2PInterface output : this.getOutputList()) {
                if (output.acceptsMore(blocking)) {
                    return false;
                }
            }
            return true;
        } finally {
            this.visiting = false;
        }
    }

    private boolean acceptsMore(final boolean blocking) {
        if (this.hasItemsToSend() || !this.getProxy().isActive()) {
            return false;
        }

        final TileEntity te = this.getFacingTile();
        if (te == null) {
            return false;
        }

        final EnumFacing facing = this.getFacingSide();
        final ICraftingMachine machine = ICraftingMachine.of(te, facing);
        if (machine instanceof PartP2PInterface) {
            return !((PartP2PInterface) machine).isBlocked(blocking);
        }
        if (machine != null) {
            return machine.acceptsPlans();
        }

        final InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, facing);
        return ad != null && !(blocking && ad.containsItems());
    }

    /**
     * What a terminal should show for the interface behind this tunnel. One kind of machine on every output
     * reads as that machine standing next to the interface, which is what the player built; a mixture can
     * only be named by the tunnel itself.
     */
    public MachineIdentity getRemoteMachineIdentity() {
        if (this.isOutput() || this.visiting) {
            return MachineIdentity.NOTHING;
        }

        this.visiting = true;
        try {
            MachineIdentity found = MachineIdentity.NOTHING;
            int machines = 0;

            for (final PartP2PInterface output : this.getOutputList()) {
                final MachineIdentity identity = output.getFacingMachineIdentity();
                if (identity == MachineIdentity.NOTHING) {
                    continue;
                }

                machines++;
                if (found == MachineIdentity.NOTHING) {
                    found = identity;
                } else if (!found.getName().equals(identity.getName())) {
                    // No ".name" here: a terminal appends it and falls back to the bare key if that misses.
                    final ItemStack self = this.getItemStack();
                    return new MachineIdentity(self.getItem().getTranslationKey(self), self);
                }
            }

            return machines == 0 ? MachineIdentity.NOTHING : found;
        } finally {
            this.visiting = false;
        }
    }

    private MachineIdentity getFacingMachineIdentity() {
        final TileEntity te = this.getFacingTile();
        if (te == null) {
            return MachineIdentity.NOTHING;
        }

        final ICraftingMachine machine = ICraftingMachine.of(te, this.getFacingSide());
        if (machine instanceof PartP2PInterface) {
            return ((PartP2PInterface) machine).getRemoteMachineIdentity();
        }

        return DualityInterface.identifyMachine(this.getTile().getWorld(), this.getTile().getPos(),
                this.getSide().getFacing());
    }

    // ------------------------------------------------------------------ plumbing

    @Nullable
    private ICraftingMachine getFacingMachine() {
        final TileEntity te = this.getFacingTile();
        return te == null ? null : ICraftingMachine.of(te, this.getFacingSide());
    }

    @Nullable
    private TileEntity getFacingTile() {
        final TileEntity host = this.getTile();
        if (host == null || host.getWorld() == null) {
            return null;
        }
        return host.getWorld().getTileEntity(host.getPos().offset(this.getSide().getFacing()));
    }

    /** The side of the neighbour that faces this tunnel. */
    private EnumFacing getFacingSide() {
        return this.getSide().getFacing().getOpposite();
    }

    private List<PartP2PInterface> getOutputList() {
        final List<PartP2PInterface> outputs = new ArrayList<>();
        try {
            for (final PartP2PInterface output : this.getOutputs()) {
                outputs.add(output);
            }
        } catch (final GridAccessException ignored) {
        }
        return outputs;
    }

    @Nullable
    private PartP2PInterface getInputTunnel() {
        try {
            for (final PartP2PInterface input : this.getInputs()) {
                return input;
            }
        } catch (final GridAccessException ignored) {
        }
        return null;
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(TickRates.ItemTunnel.getMin(), TickRates.ItemTunnel.getMax(), false, false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (!this.hasItemsToSend()) {
            return TickRateModulation.SLEEP;
        }

        this.pushItemsOut();
        return this.hasItemsToSend() ? TickRateModulation.SLOWER : TickRateModulation.URGENT;
    }

    @Override
    public void onTunnelNetworkChange() {
        super.onTunnelNetworkChange();
        this.getHost().notifyNeighbors();
    }

    @Override
    public void onTunnelConfigChange() {
        super.onTunnelConfigChange();
        this.getHost().notifyNeighbors();
    }

    /**
     * Only an output hands the served interface's stock to its neighbour, and it is the interface's own
     * item and fluid view rather than network access - a storage bus on an output would otherwise see the
     * whole network without paying for a channel, which is what the ME tunnel is for. An input offers
     * nothing, so the interface never meets its own inventory on the face it pushes into.
     */
    @Override
    public boolean hasCapability(final Capability<?> capabilityClass) {
        // Answered by actually finding the interface rather than by the capability alone: a hopper told
        // there is a handler and then handed null crashes inside Forge's own hook, and an output whose
        // input has been moved off its interface has nothing to offer.
        if (this.getServedStock(capabilityClass) != null) {
            return true;
        }
        return super.hasCapability(capabilityClass);
    }

    @Override
    public <T> T getCapability(final Capability<T> capabilityClass) {
        final T stock = this.getServedStock(capabilityClass);
        if (stock != null) {
            return stock;
        }
        return super.getCapability(capabilityClass);
    }

    @Nullable
    private <T> T getServedStock(final Capability<T> capabilityClass) {
        if (!this.isOutput()
                || (capabilityClass != CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                        && capabilityClass != CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)) {
            return null;
        }

        final DualityInterface duality = this.getServedInterface();
        return duality == null ? null : duality.getCapability(capabilityClass, this.getFacingSide());
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);

        this.nextOutput = data.getInteger("nextOutput");
        this.waitingToSend.clear();

        final NBTTagList waiting = data.getTagList("waitingToSend", Constants.NBT.TAG_COMPOUND);
        for (int x = 0; x < waiting.tagCount(); x++) {
            final ItemStack is = ItemStackHelper.stackFromNBT(waiting.getCompoundTagAt(x));
            if (!is.isEmpty()) {
                this.waitingToSend.add(is);
            }
        }
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);

        data.setInteger("nextOutput", this.nextOutput);

        final NBTTagList waiting = new NBTTagList();
        for (final ItemStack is : this.waitingToSend) {
            waiting.appendTag(ItemStackHelper.stackToNBT(is));
        }
        data.setTag("waitingToSend", waiting);
    }

    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        super.getDrops(drops, wrenched);
        drops.addAll(this.waitingToSend);
    }

    @Override
    public void onNeighborChanged(final net.minecraft.world.IBlockAccess w, final BlockPos pos, final BlockPos neighbor) {
        super.onNeighborChanged(w, pos, neighbor);
        this.getHost().markForUpdate();
    }
}
