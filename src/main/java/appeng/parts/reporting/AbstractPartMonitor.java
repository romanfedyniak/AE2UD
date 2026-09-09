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


import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.implementations.parts.IPartStorageMonitor;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.render.TesrRenderHelper;
import appeng.core.sync.GuiBridge;
import appeng.helpers.Reflected;
import appeng.me.GridAccessException;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

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
public abstract class AbstractPartMonitor extends AbstractPartDisplay
        implements IPartStorageMonitor, IStorageWatcherNode, IAEAppEngInventory {

    /**
     * What the monitor watches, as one slot of a config inventory rather than a bare field: a slot is what a
     * screen can show, click on and be dropped into, and this one holds a key of any type rather than an
     * {@link ItemStack}, so a fluid works there with no code of its own.
     */
    private final AppEngInternalAEInventory config = new AppEngInternalAEInventory(this, 1);

    /**
     * The one key this monitor watches, or null while unconfigured. Mirrors the config slot on the server and
     * arrives from the stream on the client. Replaces the old split {@code configuredItem}/{@code configuredFluid}
     * fields - both variants of {@code IAEStack} used to need their own field, but a single type-erased
     * {@link AEKey} covers both (and any future type) uniformly.
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
        this.config.readFromNBT(data, "config");
        this.configuredKey = this.config.getAEStackInSlot(0) == null ? null : this.config.getAEStackInSlot(0).what();

        // A monitor placed before the config slot existed kept its key in a tag of its own. Read it when the
        // slot is empty, or every configured monitor in an existing world comes back blank.
        if (this.configuredKey == null && data.hasKey("configuredKey")) {
            this.setConfiguredKey(AEKey.fromTagGeneric(data.getCompoundTag("configuredKey")));
        }
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);

        data.setBoolean("isLocked", this.isLocked);
        this.config.writeToNBT(data, "config");
    }

    @Override
    public void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);

        data.writeBoolean(this.isLocked);
        AEKey.writeOptionalKey(data, this.configuredKey);
        data.writeLong(this.configuredAmount);
    }

    @Override
    public boolean readFromStream(final ByteBuf data) throws IOException {
        boolean needRedraw = super.readFromStream(data);

        final boolean isLocked = data.readBoolean();
        needRedraw |= this.isLocked != isLocked;

        this.isLocked = isLocked;
        this.configuredKey = AEKey.readOptionalKey(data);
        // Not part of needRedraw: the amount is drawn by the dynamic renderer, and a monitor on a busy
        // network would rebuild its chunk on every change.
        this.configuredAmount = data.readLong();

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

            if (eq.isEmpty()) {
                this.setConfiguredKey(null);
            } else if (AEItemKey.matches(this.configuredKey, eq)) {
                // The container is already on the monitor, so this click asks for what is inside it instead.
                // Whether anything is depends on the registered strategies, so a key type an addon brings
                // works here with no code of its own.
                final GenericStack contained = ContainerItemStrategies.getContainedStack(eq);

                if (contained != null) {
                    this.setConfiguredKey(contained.what());
                }
            } else {
                this.setConfiguredKey(AEItemKey.of(eq));
            }
        } else {
            return super.onPartActivate(player, hand, pos);
        }

        return true;
    }

    /**
     * The lock used to live on this gesture alone, which left a monitor with one setting and no room for
     * another. It is a button in the window now, and the gesture opens the window.
     */
    @Override
    public boolean onPartShiftActivate(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (Platform.isClient()) {
            return true;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        if (player.getHeldItem(hand).isEmpty()) {
            // The permission is checked by the bridge on the way in, and again on every tick the window is
            // open, which the check that used to stand here could not do.
            Platform.openGUI(player, this.getHost().getTile(), this.getSide(), GuiBridge.GUI_MONITOR);
        }

        return true;
    }

    public void setLocked(final boolean locked) {
        if (this.isLocked == locked) {
            return;
        }

        this.isLocked = locked;
        this.getHost().markForSave();
        this.getHost().markForUpdate();
    }

    public AppEngInternalAEInventory getConfigInventory() {
        return this.config;
    }

    protected void setConfiguredKey(@Nullable final AEKey key) {
        this.config.setStackInSlot(0, key == null ? ItemStack.EMPTY : GenericStack.wrapInItemStack(key, 1));
    }

    @Override
    public void saveChanges() {
        this.getHost().markForSave();
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        final GenericStack configured = this.config.getAEStackInSlot(0);
        this.configuredKey = configured == null ? null : configured.what();

        this.configureWatchers();
        this.getHost().markForSave();
        this.getHost().markForUpdate();
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
        TesrRenderHelper.renderKey2dWithAmount(key, this.configuredAmount, 0.8f, 0.17f);
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
