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

package appeng.helpers;


import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.implementations.IUpgradeableCellHost;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.implementations.tiles.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.me.cluster.IAECluster;
import appeng.me.cluster.implementations.QuantumCluster;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.networking.TileWireless;
import appeng.tile.qnb.TileQuantumBridge;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;


public class WirelessTerminalGuiObject implements IPortableCell, IActionHost, IInventorySlotAware, IViewCellStorage, IAEAppEngInventory, IUpgradeableCellHost {

    private final ItemStack effectiveItem;
    private final IWirelessTermHandler wth;
    private final String encryptionKey;
    private final EntityPlayer myPlayer;
    private final boolean isBaubleSlot;
    private IGrid targetGrid;
    private IStorageService sg;
    private MEStorage networkStorage;
    private IWirelessAccessPoint myWap;
    private double sqRange = Double.MAX_VALUE;
    private double myRange = Double.MAX_VALUE;
    private final int inventorySlot;

    private final AppEngInternalInventory viewCell = new AppEngInternalInventory(this, 5);
    private final UpgradeInventory upgrades;
    private QuantumCluster myQC;


    public WirelessTerminalGuiObject(final IWirelessTermHandler wh, final ItemStack is, final EntityPlayer ep, final World w, final int x, final int y, final int z) {
        this.encryptionKey = wh.getEncryptionKey(is);
        this.effectiveItem = is;
        this.myPlayer = ep;
        this.wth = wh;
        this.inventorySlot = x;
        this.isBaubleSlot = y == 1;

        ILocatable obj = null;

        try {
            final long encKey = Long.parseLong(this.encryptionKey);
            obj = AEApi.instance().registries().locatable().getLocatableBy(encKey);
        } catch (final NumberFormatException err) {
            // :P
        }

        if (obj instanceof IActionHost) {
            final IGridNode n = ((IActionHost) obj).getActionableNode();
            if (n != null) {
                this.targetGrid = n.getGrid();
                if (this.targetGrid != null) {
                    this.sg = this.targetGrid.getCache(IStorageService.class);
                    if (this.sg != null) {
                        this.networkStorage = this.sg.getInventory();
                    }
                }
            }
        }

        upgrades = new StackUpgradeInventory(effectiveItem, this, 2);

        this.loadFromNBT();
    }

    public double getRange() {
        return this.myRange;
    }

    /**
     * The linked network's own inventory, not this wrapper.
     * <p/>
     * The wrapper's own {@link #insert}, {@link #extract} and {@link #getAvailableStacks} are pure
     * delegations to the same object, so handing it out directly changes nothing about storage - but it
     * does let a caller recognise that this terminal shows a <em>network</em> rather than one cell, which
     * {@code ContainerMEMonitorable} decides by comparing against {@link IStorageService#getInventory()}.
     * While this answered {@code this}, that comparison failed and the wireless terminal silently lost its
     * craftable rows.
     * <p/>
     * Falls back to this wrapper when there is no link, so an unbound terminal still opens empty and is
     * closed by the usual range check rather than failing in the container's constructor.
     */
    @Override
    public MEStorage getInventory() {
        return this.networkStorage != null ? this.networkStorage : this;
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (this.networkStorage != null) {
            return this.networkStorage.insert(what, amount, mode, source);
        }
        return 0;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (this.networkStorage != null) {
            return this.networkStorage.extract(what, amount, mode, source);
        }
        return 0;
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        if (this.networkStorage != null) {
            this.networkStorage.getAvailableStacks(out);
        }
    }

    @Override
    public double extractAEPower(final double amt, final Actionable mode, final PowerMultiplier usePowerMultiplier) {
        if (this.wth != null && this.effectiveItem != null) {
            if (mode == Actionable.SIMULATE) {
                return this.wth.hasPower(this.myPlayer, amt, this.effectiveItem) ? amt : 0;
            }
            return this.wth.usePower(this.myPlayer, amt, this.effectiveItem) ? amt : 0;
        }
        return 0.0;
    }

    @Override
    public ItemStack getItemStack() {
        return this.effectiveItem;
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.wth.getConfigManager(this.effectiveItem);
    }

    @Override
    public IGridNode getActionableNode() {
        this.rangeCheck();
        if (this.myWap != null) {
            return this.myWap.getActionableNode();
        } else if (this.myQC != null && this.myQC.getCenter().isPowered()) {
            return this.myQC.getCenter().getActionableNode();
        }
        return null;
    }

    public boolean rangeCheck() {
        this.sqRange = this.myRange = Double.MAX_VALUE;

        if (this.targetGrid != null && this.networkStorage != null) {
            if (this.myWap != null) {
                if (this.myWap.getGrid() == this.targetGrid) {
                    return this.testWap(this.myWap);
                }
                return false;
            }

            IMachineSet tw = this.targetGrid.getMachines(TileWireless.class);

            this.myWap = null;
            this.myQC = null;

            for (final IGridNode n : tw) {
                final IWirelessAccessPoint wap = (IWirelessAccessPoint) n.getMachine();
                if (this.testWap(wap)) {
                    this.myWap = wap;
                }
            }

            if (myWap != null) return true;

            tw = this.targetGrid.getMachines(TileQuantumBridge.class);
            for (final IGridNode n : tw) {
                TileQuantumBridge tqb = (TileQuantumBridge) n.getMachine();
                if (tqb.getCluster() != null) {
                    TileQuantumBridge center = ((QuantumCluster) tqb.getCluster()).getCenter();
                    if (center != null) {
                        if (center.getInternalInventory().getStackInSlot(1).isItemEqual(AEApi.instance().definitions().materials().cardQuantumLink().maybeStack(1).get())) {
                            myQC = (QuantumCluster) tqb.getCluster();
                            myRange = 1;
                            return true;
                        }
                    }
                }
            }

            return this.myWap != null || this.myQC != null;
        }
        return false;
    }

    private boolean testWap(final IWirelessAccessPoint wap) {
        double rangeLimit = wap.getRange();
        rangeLimit *= rangeLimit;

        final DimensionalCoord dc = wap.getLocation();

        if (dc.getWorld() == this.myPlayer.world) {
            final double offX = dc.x - this.myPlayer.posX;
            final double offY = dc.y - this.myPlayer.posY;
            final double offZ = dc.z - this.myPlayer.posZ;

            final double r = offX * offX + offY * offY + offZ * offZ;
            if (r < rangeLimit && this.sqRange > r) {
                if (wap.isActive()) {
                    this.sqRange = r;
                    this.myRange = Math.sqrt(r);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int getInventorySlot() {
        return this.inventorySlot;
    }

    @Override
    public boolean isBaubleSlot() {
        return isBaubleSlot;
    }

    @Override
    public IItemHandler getViewCellStorage() {
        NBTTagCompound data = effectiveItem.getTagCompound();
        if (data != null) {
            viewCell.readFromNBT(data, "viewCell");
        }
        return this.viewCell;
    }

    @Override
    public void saveChanges() {
        NBTTagCompound data = effectiveItem.getTagCompound();
        if (data == null) {
            data = new NBTTagCompound();
        }
        viewCell.writeToNBT(data, "viewCell");
        upgrades.writeToNBT(data, "upgrades");
    }
    
    public void saveChanges(NBTTagCompound data) {
        if (effectiveItem.getTagCompound() != null) {
            effectiveItem.getTagCompound().merge(data);
        } else {
            effectiveItem.setTagCompound(data);
        }
    }

    public void loadFromNBT() {
        NBTTagCompound data = effectiveItem.getTagCompound();
        if (data != null) {
            viewCell.readFromNBT(data);
            upgrades.readFromNBT(data);
        }
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removedStack, ItemStack newStack) {

    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        if (name.equals("upgrades")) {
            return upgrades;
        } else if (name.equals("viewCell")) {
            return viewCell;
        }
        return null;
    }
}
