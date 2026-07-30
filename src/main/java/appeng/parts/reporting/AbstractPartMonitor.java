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

package appeng.parts.reporting;


import appeng.api.implementations.parts.IPartStorageMonitor;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.render.TesrRenderHelper;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.Reflected;
import appeng.me.GridAccessException;
import appeng.util.IWideReadableNumberConverter;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.io.IOException;


/**
 * A basic subclass for any item monitor like display with an item icon and an amount.
 * <p>
 * It can also be used to extract items from somewhere and spawned into the world.
 *
 * @author AlgorithmX2
 * @author thatsIch
 * @author yueh
 * @version rv3
 * @since rv3
 */
public abstract class AbstractPartMonitor extends AbstractPartDisplay implements IPartStorageMonitor, IStorageWatcherNode {
    private static final IWideReadableNumberConverter NUMBER_CONVERTER = ReadableNumberConverter.INSTANCE;

    /**
     * The one key this monitor watches, or null while unconfigured. Replaces the old split
     * {@code configuredItem}/{@code configuredFluid} fields - both variants of {@code IAEStack} used to need their
     * own field, but a single type-erased {@link AEKey} covers both (and any future type) uniformly.
     */
    @Nullable
    private AEKey configuredKey;
    private long configuredAmount;
    private boolean isLocked;
    private IStackWatcher myWatcher;

    @Reflected
    public AbstractPartMonitor(final ItemStack is) {
        super(is);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);

        this.isLocked = data.getBoolean("isLocked");
        this.configuredKey = AEKey.fromTagGeneric(data.getCompoundTag("configuredKey"));
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);

        data.setBoolean("isLocked", this.isLocked);

        final NBTTagCompound keyTag = new NBTTagCompound();
        if (this.configuredKey != null) {
            this.configuredKey.toTagGeneric(keyTag);
        }
        data.setTag("configuredKey", keyTag);
    }

    @Override
    public void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);

        data.writeBoolean(this.isLocked);
        AEKey.writeOptionalKey(data, this.configuredKey);
    }

    @Override
    public boolean readFromStream(final ByteBuf data) throws IOException {
        boolean needRedraw = super.readFromStream(data);

        final boolean isLocked = data.readBoolean();
        needRedraw |= this.isLocked != isLocked;

        this.isLocked = isLocked;
        this.configuredKey = AEKey.readOptionalKey(data);

        return needRedraw;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        if (Platform.isClient()) {
            return true;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        if (!Platform.hasPermissions(this.getLocation(), player)) {
            return false;
        }

        if (!this.isLocked) {
            final ItemStack eq = player.getHeldItem(hand);
            FluidStack fluidInTank = null;

            if (eq.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
                IFluidHandlerItem fluidHandlerItem = (eq.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null));
                fluidInTank = fluidHandlerItem.drain(Integer.MAX_VALUE, false);
            }

            if (fluidInTank != null && fluidInTank.amount > 0) {
                this.configuredKey = AEFluidKey.of(fluidInTank);
            } else if (!eq.isEmpty()) {
                this.configuredKey = AEItemKey.of(eq);
            } else {
                this.configuredKey = null;
            }

            this.configureWatchers();
            this.getHost().markForSave();
            this.getHost().markForUpdate();
        } else {
            return super.onPartActivate(player, hand, pos);
        }

        return true;
    }

    @Override
    public boolean onPartShiftActivate(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (Platform.isClient()) {
            return true;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        if (!Platform.hasPermissions(this.getLocation(), player)) {
            return false;
        }

        if (player.getHeldItem(hand).isEmpty()) {
            this.isLocked = !this.isLocked;
            player.sendMessage((this.isLocked ? PlayerMessages.isNowLocked : PlayerMessages.isNowUnlocked).get());
            this.getHost().markForSave();
            this.getHost().markForUpdate();
        }

        return true;
    }

    // update the system...
    protected void configureWatchers() {
        if (this.myWatcher != null) {
            this.myWatcher.reset();
        }

        try {
            if (this.configuredKey != null) {
                if (this.myWatcher != null) {
                    this.myWatcher.add(this.configuredKey);
                }

                this.configuredAmount = this.getProxy().getStorage().getCachedInventory().get(this.configuredKey);
            }
        } catch (final GridAccessException e) {
            // >.>
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderDynamic(double x, double y, double z, float partialTicks, int destroyStage) {

        if ((this.getClientFlags() & (PartPanel.POWERED_FLAG | PartPanel.CHANNEL_FLAG)) != (PartPanel.POWERED_FLAG | PartPanel.CHANNEL_FLAG)) {
            return;
        }

        final AEKey key = this.configuredKey;

        if (key == null) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);

        EnumFacing facing = this.getSide().getFacing();

        TesrRenderHelper.moveToFace(facing);
        TesrRenderHelper.rotateToFace(facing, this.getSpin());
        // NOTE for wave 4 (appeng.client.render is not this wave's scope): TesrRenderHelper still has the old
        // (IAEItemStack)/(IAEFluidStack) signatures. These two calls are written against what it needs to become -
        // (AEItemKey/AEFluidKey, long amount, float, float) - exactly like the other forward references CONTRACT.md
        // §9 already lists as debt for the wave that owns the callee.
        if (key instanceof AEItemKey itemKey) {
            TesrRenderHelper.renderItem2dWithAmount(itemKey, this.configuredAmount, 0.8f, 0.17f);
        } else if (key instanceof AEFluidKey fluidKey) {
            TesrRenderHelper.renderFluid2dWithAmount(fluidKey, this.configuredAmount, 0.8f, 0.17f);
        }
        GlStateManager.popMatrix();

    }

    @Override
    public boolean requireDynamicRender() {
        return true;
    }

    @Override
    public GenericStack getDisplayed() {
        return this.configuredKey == null ? null : new GenericStack(this.configuredKey, this.configuredAmount);
    }

    /**
     * @return the raw key this monitor is configured for, or null. Used by subclasses (e.g.
     *         {@link PartConversionMonitor}) that need the key itself rather than the key+amount pair
     *         {@link #getDisplayed()} returns.
     */
    @Nullable
    protected final AEKey getConfiguredKey() {
        return this.configuredKey;
    }

    @Override
    public boolean isLocked() {
        return this.isLocked;
    }

    @Override
    public void updateWatcher(final IStackWatcher newWatcher) {
        this.myWatcher = newWatcher;
        this.configureWatchers();
    }

    @MENetworkEventSubscribe
    public void powerStatusChange(final MENetworkPowerStatusChange ev) {
        if (this.getProxy().isPowered()) {
            this.configureWatchers();
        }
    }

    @MENetworkEventSubscribe
    public void channelChanged(final MENetworkChannelsChanged c) {
        if (this.getProxy().isPowered()) {
            this.configureWatchers();
        }
    }

    @Override
    public void onStackChange(final AEKey what, final long amount) {
        this.configuredAmount = amount;
        this.getHost().markForUpdate();
    }

    @Override
    public boolean showNetworkInfo(final RayTraceResult where) {
        return false;
    }

    protected IPartModel selectModel(IPartModel off, IPartModel on, IPartModel hasChannel, IPartModel lockedOff, IPartModel lockedOn, IPartModel lockedHasChannel) {
        if (this.isActive()) {
            if (this.isLocked()) {
                return lockedHasChannel;
            } else {
                return hasChannel;
            }
        } else if (this.isPowered()) {
            if (this.isLocked()) {
                return lockedOn;
            } else {
                return on;
            }
        } else {
            if (this.isLocked()) {
                return lockedOff;
            } else {
                return off;
            }
        }
    }

}
