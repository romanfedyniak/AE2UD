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

package appeng.container.implementations;


import appeng.api.config.*;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.parts.IPart;
import appeng.api.util.IConfigManager;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.*;
import appeng.items.contents.NetworkToolViewer;
import appeng.items.tools.ToolNetworkTool;
import appeng.util.Platform;
import appeng.util.helpers.ItemHandlerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;


public abstract class ContainerUpgradeable extends AEBaseContainer implements IOptionalSlotHost {

    private final IUpgradeableHost upgradeable;
    @GuiSync(0)
    public RedstoneMode rsMode = RedstoneMode.IGNORE;
    @GuiSync(1)
    public FuzzyMode fzMode = FuzzyMode.IGNORE_ALL;
    private int tbSlot;
    private NetworkToolViewer tbInventory;

    public ContainerUpgradeable(final InventoryPlayer ip, final IUpgradeableHost te) {
        super(ip, (TileEntity) (te instanceof TileEntity ? te : null), (IPart) (te instanceof IPart ? te : null));
        this.upgradeable = te;

        World w = null;
        int xCoord = 0;
        int yCoord = 0;
        int zCoord = 0;

        if (te instanceof TileEntity) {
            final TileEntity myTile = (TileEntity) te;
            w = myTile.getWorld();
            xCoord = myTile.getPos().getX();
            yCoord = myTile.getPos().getY();
            zCoord = myTile.getPos().getZ();
        }

        if (te instanceof IPart) {
            final TileEntity mk = te.getTile();
            w = mk.getWorld();
            xCoord = mk.getPos().getX();
            yCoord = mk.getPos().getY();
            zCoord = mk.getPos().getZ();
        }

        final IInventory pi = this.getPlayerInv();
        for (int x = 0; x < pi.getSizeInventory(); x++) {
            final ItemStack pii = pi.getStackInSlot(x);
            if (!pii.isEmpty() && pii.getItem() instanceof ToolNetworkTool) {
                this.lockPlayerInventorySlot(x);
                this.tbSlot = x;
                this.tbInventory = (NetworkToolViewer) ((IGuiItem) pii.getItem()).getGuiObject(pii, w, new BlockPos(xCoord, yCoord, zCoord));
                break;
            }
        }

        if (this.hasToolbox()) {
            for (int v = 0; v < 3; v++) {
                for (int u = 0; u < 3; u++) {
                    this.addSlotToContainer((new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, this.tbInventory
                            .getInternalInventory(), u + v * 3, 186 + u * 18, this.getHeight() - 82 + v * 18, this.getInventoryPlayer())).setPlayerSide());
                }
            }
        }

        this.setupConfig();

        this.bindPlayerInventory(ip, 0, this.getHeight() - /* height of player inventory */82);
    }

    public boolean hasToolbox() {
        return this.tbInventory != null;
    }

    protected int getHeight() {
        return 184;
    }

    protected abstract void setupConfig();

    protected void setupUpgrades() {
        final IItemHandler upgrades = this.getUpgradeable().getInventoryByName("upgrades");
        for (int i = 0; i < this.availableUpgrades(); i++) {
            this.addSlotToContainer(
                    (new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, i, 187, 8 + 18 * i, this.getInventoryPlayer()))
                            .setNotDraggable());
        }
    }

    /**
     * The filter grid upstream builds with {@code addExpandableConfigSlots}: {@code rows} rows of
     * {@code cols} slots always visible, then {@code optionalRows} more, each unlocked by one further
     * capacity card through {@link #isSlotEnabled(int)}.
     */
    protected void setupExpandableConfig(final int rows, final int cols, final int optionalRows) {
        final int xo = 8;
        final int yo = 23 + 6;

        final IItemHandler config = this.getUpgradeable().getInventoryByName("config");
        for (int y = 0; y < rows + optionalRows; y++) {
            for (int x = 0; x < cols; x++) {
                final int idx = y * cols + x;
                if (y < rows) {
                    this.addSlotToContainer(new SlotFakeTypeOnly(config, idx, xo + x * 18, yo + y * 18));
                } else {
                    this.addSlotToContainer(new OptionalSlotFakeTypeOnly(config, this, idx, xo, yo, x, y, y - rows));
                }
            }
        }
    }

    public int availableUpgrades() {
        return 4;
    }

    /**
     * Empties the filter. Shared by every screen carrying a clear button - a filter is a filter whatever
     * its host does with the contents.
     */
    public void clear() {
        ItemHandlerUtil.clear(this.getUpgradeable().getInventoryByName("config"));
        this.detectAndSendChanges();
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            final IConfigManager cm = this.getUpgradeable().getConfigManager();
            this.loadSettingsFromHost(cm);
        }

        this.checkToolbox();

        this.standardDetectAndSendChanges();
    }

    /**
     * Tells the client a slot is empty without going through {@code EntityPlayerMP.sendSlotContents}.
     * <p>
     * That method drops every slot packet while {@code isChangingQuantityOnly} is set, which is exactly the
     * tick a capacity card is clicked out of its slot - the tick these rows are emptied. The standard
     * broadcast above updates the container's own record of what the client knows, so the following tick
     * sees no difference and never retries. The client would keep the rows it had until the window is
     * reopened, and putting the card back would show settings the server had already thrown away.
     */
    private void forceSendEmpty(final List<Integer> slotNumbers) {
        for (final IContainerListener listener : this.listeners) {
            if (!(listener instanceof EntityPlayerMP)) {
                continue;
            }
            for (final int slotNumber : slotNumbers) {
                ((EntityPlayerMP) listener).connection.sendPacket(new SPacketSetSlot(this.windowId, slotNumber, ItemStack.EMPTY));
            }
        }
    }

    protected void loadSettingsFromHost(final IConfigManager cm) {
        this.setFuzzyMode((FuzzyMode) cm.getSetting(Settings.FUZZY_MODE));
        this.setRedStoneMode((RedstoneMode) cm.getSetting(Settings.REDSTONE_CONTROLLED));
    }

    protected void checkToolbox() {
        if (this.hasToolbox()) {
            final ItemStack currentItem = this.getPlayerInv().getStackInSlot(this.tbSlot);

            if (currentItem != this.tbInventory.getItemStack()) {
                if (!currentItem.isEmpty()) {
                    if (ItemStack.areItemsEqual(this.tbInventory.getItemStack(), currentItem)) {
                        this.getPlayerInv().setInventorySlotContents(this.tbSlot, this.tbInventory.getItemStack());
                    } else {
                        this.setValidContainer(false);
                    }
                } else {
                    this.setValidContainer(false);
                }
            }
        }
    }

    /**
     * Every subclass routes its broadcast through here, which is why the disabled-slot sweep lives here
     * rather than in {@link #detectAndSendChanges()} - several subclasses replace that method wholesale.
     */
    protected void standardDetectAndSendChanges() {
        List<Integer> cleared = null;

        for (final Slot s : this.inventorySlots) {
            if (s instanceof OptionalSlotFake) {
                final OptionalSlotFake fs = (OptionalSlotFake) s;
                if (!fs.isSlotEnabled() && !fs.getDisplayStack().isEmpty()) {
                    fs.clearStack();
                    if (cleared == null) {
                        cleared = new ArrayList<>();
                    }
                    cleared.add(fs.slotNumber);
                }
            }
        }

        super.detectAndSendChanges();

        if (cleared != null) {
            this.forceSendEmpty(cleared);
        }
    }

    /**
     * No optional slots unless a subclass lays some out.
     */
    @Override
    public boolean isSlotEnabled(final int idx) {
        return false;
    }

    public FuzzyMode getFuzzyMode() {
        return this.fzMode;
    }

    public void setFuzzyMode(final FuzzyMode fzMode) {
        this.fzMode = fzMode;
    }

    public RedstoneMode getRedStoneMode() {
        return this.rsMode;
    }

    public void setRedStoneMode(final RedstoneMode rsMode) {
        this.rsMode = rsMode;
    }

    protected IUpgradeableHost getUpgradeable() {
        return this.upgradeable;
    }
}
