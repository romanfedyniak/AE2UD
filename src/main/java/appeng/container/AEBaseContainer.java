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

package appeng.container;


import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.definitions.IItemDefinition;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.parts.IPart;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.client.me.SlotME;
import appeng.container.guisync.GuiSync;
import appeng.container.guisync.SyncData;
import appeng.container.implementations.ContainerInterface;
import appeng.container.slot.*;
import appeng.container.slot.SlotRestrictedInput.PlacableItemType;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketTargetItemStack;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.InventoryAction;
import appeng.me.helpers.PlayerSource;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.AdaptorItemHandler;
import appeng.util.inv.WrapperCursorItemHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;


public abstract class AEBaseContainer extends Container {
    private final InventoryPlayer invPlayer;
    private final IActionSource mySrc;
    private final HashSet<Integer> locked = new HashSet<>();
    private final TileEntity tileEntity;
    private final IPart part;
    protected final IGuiItemObject obj;
    private final HashMap<Integer, SyncData> syncData = new HashMap<>();
    private boolean isContainerValid = true;
    private String customName;
    private ContainerOpenContext openContext;
    private MEStorage cellInv;
    private IEnergySource powerSrc;
    private boolean sentCustomName;
    private int ticksSinceCheck = 900;
    private AEKey clientRequestedTargetItem = null;

    public AEBaseContainer(final InventoryPlayer ip, final TileEntity myTile, final IPart myPart) {
        this(ip, myTile, myPart, null);
    }

    public AEBaseContainer(final InventoryPlayer ip, final TileEntity myTile, final IPart myPart, final IGuiItemObject gio) {
        this.invPlayer = ip;
        this.tileEntity = myTile;
        this.part = myPart;
        this.obj = gio;
        this.mySrc = new PlayerSource(ip.player, this.getActionHost());
        this.prepareSync();
    }

    protected IActionHost getActionHost() {
        if (this.obj instanceof IActionHost) {
            return (IActionHost) this.obj;
        }

        if (this.tileEntity instanceof IActionHost) {
            return (IActionHost) this.tileEntity;
        }

        if (this.part instanceof IActionHost) {
            return (IActionHost) this.part;
        }

        return null;
    }

    private void prepareSync() {
        for (final Field f : this.getClass().getFields()) {
            if (f.isAnnotationPresent(GuiSync.class)) {
                final GuiSync annotation = f.getAnnotation(GuiSync.class);
                if (this.syncData.containsKey(annotation.value())) {
                    AELog.warn("Channel already in use: " + annotation.value() + " for " + f.getName());
                } else {
                    this.syncData.put(annotation.value(), new SyncData(this, f, annotation));
                }
            }
        }
    }

    public AEBaseContainer(final InventoryPlayer ip, final Object anchor) {
        this.invPlayer = ip;
        this.tileEntity = anchor instanceof TileEntity ? (TileEntity) anchor : null;
        this.part = anchor instanceof IPart ? (IPart) anchor : null;
        this.obj = anchor instanceof IGuiItemObject ? (IGuiItemObject) anchor : null;

        if (this.tileEntity == null && this.part == null && this.obj == null) {
            throw new IllegalArgumentException("Must have a valid anchor, instead " + anchor + " in " + ip);
        }

        this.mySrc = new PlayerSource(ip.player, this.getActionHost());

        this.prepareSync();
    }

    @Nullable
    public AEKey getTargetStack() {
        return this.clientRequestedTargetItem;
    }

    public void setTargetStack(@Nullable final AEKey stack) {
        // client doesn't need to re-send, makes for lower overhead rapid packets.
        if (Platform.isClient()) {
            if (stack == null && this.clientRequestedTargetItem == null) {
                return;
            }
            // AEKey carries no amount (unlike GenericStack, see CONTRACT.md §9.1), so a plain equals() is
            // already the size-insensitive identity check the old isSameType() was.
            if (stack != null && stack.equals(this.clientRequestedTargetItem)) {
                return;
            }

            NetworkHandler.instance().sendToServer(new PacketTargetItemStack(stack));
        }

        this.clientRequestedTargetItem = stack;
    }

    public IActionSource getActionSource() {
        return this.mySrc;
    }

    public void verifyPermissions(final SecurityPermissions security, final boolean requirePower) {
        if (Platform.isClient()) {
            return;
        }

        this.ticksSinceCheck++;
        if (this.ticksSinceCheck < 20) {
            return;
        }

        this.ticksSinceCheck = 0;
        this.setValidContainer(this.isValidContainer() && this.hasAccess(security, requirePower));
    }

    protected boolean hasAccess(final SecurityPermissions perm, final boolean requirePower) {
        final IActionHost host = this.getActionHost();

        if (host != null) {
            final IGridNode gn = host.getActionableNode();
            if (gn != null) {
                final IGrid g = gn.getGrid();
                if (g != null) {
                    if (requirePower) {
                        final IEnergyGrid eg = g.getCache(IEnergyGrid.class);
                        if (!eg.isNetworkPowered()) {
                            return false;
                        }
                    }

                    final ISecurityGrid sg = g.getCache(ISecurityGrid.class);
                    return sg.hasPermission(this.getInventoryPlayer().player, perm);
                }
            }
        }

        return false;
    }

    public void lockPlayerInventorySlot(final int idx) {
        this.locked.add(idx);
    }

    public Object getTarget() {
        if (this.tileEntity != null) {
            return this.tileEntity;
        }
        if (this.part != null) {
            return this.part;
        }
        return this.obj;
    }

    public InventoryPlayer getPlayerInv() {
        return this.getInventoryPlayer();
    }

    public TileEntity getTileEntity() {
        return this.tileEntity;
    }

    public final void updateFullProgressBar(final int idx, final long value) {
        if (this.syncData.containsKey(idx)) {
            this.syncData.get(idx).update(value);
            return;
        }

        this.updateProgressBar(idx, (int) value);
    }

    public void stringSync(final int idx, final String value) {
        if (this.syncData.containsKey(idx)) {
            this.syncData.get(idx).update(value);
        }
    }

    protected void bindPlayerInventory(final InventoryPlayer inventoryPlayer, final int offsetX, final int offsetY) {
        IItemHandler ih = new PlayerInvWrapper(inventoryPlayer);

        // bind player inventory
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                if (this.locked.contains(j + i * 9 + 9)) {
                    this.addSlotToContainer(new SlotDisabled(ih, j + i * 9 + 9, 8 + j * 18 + offsetX, offsetY + i * 18));
                } else {
                    this.addSlotToContainer(new SlotPlayerInv(ih, j + i * 9 + 9, 8 + j * 18 + offsetX, offsetY + i * 18));
                }
            }
        }

        // bind player hotbar
        for (int i = 0; i < 9; i++) {
            if (this.locked.contains(i)) {
                this.addSlotToContainer(new SlotDisabled(ih, i, 8 + i * 18 + offsetX, 58 + offsetY));
            } else {
                this.addSlotToContainer(new SlotPlayerHotBar(ih, i, 8 + i * 18 + offsetX, 58 + offsetY));
            }
        }
    }

    @Override
    protected Slot addSlotToContainer(final Slot newSlot) {
        if (newSlot instanceof AppEngSlot) {
            final AppEngSlot s = (AppEngSlot) newSlot;
            s.setContainer(this);
            return super.addSlotToContainer(newSlot);
        } else {
            throw new IllegalArgumentException("Invalid Slot [" + newSlot + "] for AE Container instead of AppEngSlot.");
        }
    }

    @Override
    public void detectAndSendChanges() {
        this.sendCustomName();

        if (Platform.isServer()) {
            if (this.tileEntity != null && this.tileEntity.getWorld().getTileEntity(this.tileEntity.getPos()) != this.tileEntity) {
                this.setValidContainer(false);
            }

            for (final IContainerListener listener : this.listeners) {
                for (final SyncData sd : this.syncData.values()) {
                    sd.tick(listener);
                }
            }
        }

        super.detectAndSendChanges();
    }

    @Override
    public ItemStack transferStackInSlot(final EntityPlayer p, final int idx) {
        if (Platform.isClient()) {
            return ItemStack.EMPTY;
        }

        final AppEngSlot clickSlot = (AppEngSlot) this.inventorySlots.get(idx); // require AE SLots!

        if (clickSlot instanceof SlotDisabled || clickSlot instanceof SlotInaccessible) {
            return ItemStack.EMPTY;
        }
        if (clickSlot != null && clickSlot.getHasStack()) {
            ItemStack tis = clickSlot.getStack();
  
            if (tis.isEmpty()) {
                return ItemStack.EMPTY;
            }
            
            IItemDefinition expansionCard = AEApi.instance().definitions().materials().cardPatternExpansion();
            ContainerInterface casted;

            final List<Slot> selectedSlots = new ArrayList<>();

            /**
             * Gather a list of valid destinations.
             */
            if (clickSlot.isPlayerSide()) {
                tis = this.transferStackToContainer(tis);

                if (!tis.isEmpty()) {
                    if (this instanceof ContainerInterface && expansionCard.isSameAs(tis) && (casted = (ContainerInterface) this).getPatternUpgrades() == casted.availableUpgrades() - 1) {
                        return ItemStack.EMPTY; // Don't insert more pattern expansions than maximum useful
                    }

                    // target slots in the container...
                    for (final Object inventorySlot : this.inventorySlots) {
                        final AppEngSlot cs = (AppEngSlot) inventorySlot;

                        if (!(cs.isPlayerSide()) && !(cs instanceof SlotFake) && !(cs instanceof SlotCraftingMatrix)) {
                            if (cs.isItemValid(tis)) {
                                selectedSlots.add(cs);
                            }
                        }
                    }
                }
            } else {
                tis = tis.copy();

                // target slots in the container...
                for (final Object inventorySlot : this.inventorySlots) {
                    final AppEngSlot cs = (AppEngSlot) inventorySlot;

                    if ((cs.isPlayerSide()) && !(cs instanceof SlotFake) && !(cs instanceof SlotCraftingMatrix)) {
                        if (cs.isItemValid(tis)) {
                            selectedSlots.add(cs);
                        }
                    }
                }
            }

            /**
             * Handle Fake Slot Shift clicking.
             */
            if (selectedSlots.isEmpty() && clickSlot.isPlayerSide()) {
                if (!tis.isEmpty()) {
                    // target slots in the container...
                    for (final Object inventorySlot : this.inventorySlots) {
                        final AppEngSlot cs = (AppEngSlot) inventorySlot;
                        final ItemStack destination = cs.getStack();

                        if (!(cs.isPlayerSide()) && cs instanceof SlotFake) {
                            if (Platform.itemComparisons().isSameItem(destination, tis)) {
                                break;
                            } else if (destination.isEmpty()) {
                                cs.putStack(tis.copy());
                                this.updateSlot(cs);
                                break;
                            }
                        }
                    }
                }
            }

            if (!tis.isEmpty()) {
                // find partials..
                for (final Slot d : selectedSlots) {
                    if (d instanceof SlotDisabled || d instanceof SlotME) {
                        continue;
                    }

                    if (d.isItemValid(tis)) {
                        if (d.getHasStack()) {
                            final ItemStack t = d.getStack().copy();

                            if (Platform.itemComparisons().isSameItem(tis, t)) // t.isItemEqual(tis))
                            {
                                if (d instanceof SlotRestrictedInput && ((SlotRestrictedInput) d).getPlaceableItemType() == PlacableItemType.ENCODED_PATTERN) {
                                    return ItemStack.EMPTY; // don't insert duplicate encoded patterns to interfaces
                                }

                                final int maxSize;
                                if (d instanceof SlotOversized slotOversized) {
                                    maxSize = slotOversized.getSlotStackLimit();
                                } else {
                                    maxSize = Math.min(tis.getMaxStackSize(), d.getSlotStackLimit());
                                }

                                int placeAble = maxSize - t.getCount();
                                if (placeAble <= 0) {
                                    continue;
                                }

                                if (tis.getCount() < placeAble) {
                                    placeAble = tis.getCount();
                                }

                                t.setCount(t.getCount() + placeAble);
                                tis.setCount(tis.getCount() - placeAble);

                                d.putStack(t);

                                if (tis.getCount() <= 0) {
                                    clickSlot.putStack(ItemStack.EMPTY);
                                    d.onSlotChanged();

                                    this.updateSlot(clickSlot);
                                    this.updateSlot(d);
                                    return ItemStack.EMPTY;
                                } else {
                                    this.updateSlot(d);
                                }
                            }
                        }
                    }
                }

                // any match..
                for (final Slot d : selectedSlots) {
                    if (d instanceof SlotDisabled || d instanceof SlotME) {
                        continue;
                    }

                    if (d.isItemValid(tis)) {
                        if (!d.getHasStack()) {
                            int maxSize = Math.min(tis.getMaxStackSize(), d.getSlotStackLimit());

                            final ItemStack tmp = tis.copy();
                            if (tmp.getCount() > maxSize) {
                                tmp.setCount(maxSize);
                            }

                            tis.setCount(tis.getCount() - tmp.getCount());
                            d.putStack(tmp);

                            if (tis.getCount() <= 0) {
                                clickSlot.putStack(ItemStack.EMPTY);
                                d.onSlotChanged();

                                this.updateSlot(clickSlot);
                                this.updateSlot(d);
                                return ItemStack.EMPTY;
                            } else {
                                this.updateSlot(d);
                                
                                if (
                                    (d instanceof SlotRestrictedInput && ((SlotRestrictedInput) d).getPlaceableItemType() == PlacableItemType.ENCODED_PATTERN) ||
                                    (this instanceof ContainerInterface && expansionCard.isSameAs(tis) && (casted = (ContainerInterface) this).getPatternUpgrades() == casted.availableUpgrades() - 1)
                                    ) {
                                    break; // Only insert one pattern when shift-clicking into interfaces, and don't insert more pattern expansions than maximum useful
                                }
                            }
                        }
                    }
                }
            }

            clickSlot.putStack(!tis.isEmpty() ? tis : ItemStack.EMPTY);
        }

        this.updateSlot(clickSlot);
        return ItemStack.EMPTY;
    }

    @Override
    public final void updateProgressBar(final int idx, final int value) {
        if (this.syncData.containsKey(idx)) {
            this.syncData.get(idx).update((long) value);
        }
    }

    @Override
    public boolean canInteractWith(final EntityPlayer entityplayer) {
        if (this.isValidContainer()) {
            if (this.tileEntity instanceof IInventory) {
                return ((IInventory) this.tileEntity).isUsableByPlayer(entityplayer);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean canDragIntoSlot(final Slot s) {
        return ((AppEngSlot) s).isDraggable();
    }

    public void doAction(final EntityPlayerMP player, final InventoryAction action, final int slot, final long id) {
        if (slot >= 0 && slot < this.inventorySlots.size()) {
            final Slot s = this.getSlot(slot);

            if (s instanceof SlotCraftingTerm) {
                switch (action) {
                    case CRAFT_SHIFT:
                    case CRAFT_ITEM:
                    case CRAFT_STACK:
                        ((SlotCraftingTerm) s).doClick(action, player);
                        this.updateHeld(player);
                    default:
                }
            }

            if (s instanceof SlotFake) {
                final ItemStack hand = player.inventory.getItemStack();

                switch (action) {
                    case PICKUP_OR_SET_DOWN:
                        if (hand.isEmpty()) {
                            s.putStack(ItemStack.EMPTY);
                        } else {
                            s.putStack(hand.copy());
                        }
                        break;
                    case PLACE_SINGLE:
                        if (!hand.isEmpty()) {
                            final ItemStack is = hand.copy();
                            is.setCount(1);
                            s.putStack(is);
                        } else {
                            final ItemStack is = s.getStack().copy();
                            if (is.getCount() < is.getMaxStackSize() * 8)
                                is.grow(1);
                            s.putStack(is);
                        }
                        break;
                    case PICKUP_SINGLE:
                        if (hand.isEmpty()) {
                            final ItemStack is = s.getStack().copy();
                            if (is.getCount() > 1)
                                is.shrink(1);
                            s.putStack(is);
                        }
                        break;
                    case SPLIT_OR_PLACE_SINGLE:
                        ItemStack is = s.getStack();
                        if (!is.isEmpty()) {
                            if (hand.isEmpty()) {
                                is.setCount(Math.max(1, is.getCount() - 1));
                            } else if (hand.isItemEqual(is)) {
                                is.setCount(Math.min(is.getMaxStackSize(), is.getCount() + 1));
                            } else {
                                is = hand.copy();
                                is.setCount(1);
                            }
                            s.putStack(is);
                        } else if (!hand.isEmpty()) {
                            is = hand.copy();
                            is.setCount(1);
                            s.putStack(is);
                        }
                        break;
                    case HALVE:
                        if (s.getStack().getCount() > 1) {
                            ItemStack halved = s.getStack().copy();
                            halved.setCount(s.getStack().getCount() / 2);
                            s.putStack(halved);
                        }
                        break;
                    case DOUBLE:
                        ItemStack doubled = s.getStack().copy();
                        if (s.getStack().getCount() * 2 > 0) {
                            doubled.setCount(Math.min(s.getSlotStackLimit(), s.getStack().getCount() * 2));
                            s.putStack(doubled);
                        }
                        break;
                    case CREATIVE_DUPLICATE:
                    case MOVE_REGION:
                    case SHIFT_CLICK:
                    default:
                        break;
                }
            }

            if (action == InventoryAction.MOVE_REGION) {
                final List<Slot> from = new ArrayList<>();

                for (final Slot j : this.inventorySlots) {
                    if (j != null && j.getClass() == s.getClass() && !(j instanceof SlotCraftingTerm)) {
                        from.add(j);
                    }
                }

                for (final Slot fr : from) {
                    this.transferStackInSlot(player, fr.slotNumber);
                }
            }

            return;
        }

        // Get the targeted key. AEKey carries no amount, so `slotItemKey` is identity only, exactly as the
        // pinned AEBaseContainer#getTargetStack() javadoc says ("only identity + display were ever read").
        // The whole switch below is inherently item-only (it moves stacks into/out of the player's vanilla
        // inventory, which cannot hold anything else), matching what the pre-migration code already did for
        // every one of these actions -- mirrors upstream MEStorageMenu#handleNetworkInteraction's own
        // `if (!(clickedKey instanceof AEItemKey clickedItem)) return;` guard for the identical action set.
        final AEKey slotItem = this.clientRequestedTargetItem;
        final AEItemKey slotItemKey = slotItem instanceof AEItemKey ? (AEItemKey) slotItem : null;

        switch (action) {
            case SHIFT_CLICK:
                if (this.getPowerSource() == null || this.getCellInventory() == null) {
                    return;
                }

                if (slotItemKey != null) {
                    final InventoryAdaptor adp = InventoryAdaptor.getAdaptor(player);
                    ItemStack myItem = slotItemKey.toStack(slotItemKey.getMaxStackSize());
                    myItem = adp.simulateAdd(myItem);

                    final long toExtract = slotItemKey.getMaxStackSize() - myItem.getCount();

                    final long extracted = Platform.poweredExtraction(this.getPowerSource(), this.getCellInventory(), slotItemKey, toExtract, this.getActionSource());
                    if (extracted > 0) {
                        adp.addItems(slotItemKey.toStack((int) extracted));
                    }
                }
                break;
            case ROLL_DOWN:
                if (this.getPowerSource() == null || this.getCellInventory() == null) {
                    return;
                }

                final ItemStack isg = player.inventory.getItemStack();

                if (!isg.isEmpty()) {
                    final AEItemKey what = AEItemKey.of(isg);

                    final long inserted = Platform.poweredInsert(this.getPowerSource(), this.getCellInventory(), what, 1, this.getActionSource());
                    if (inserted > 0) {
                        final InventoryAdaptor ia = new AdaptorItemHandler(new WrapperCursorItemHandler(player.inventory));

                        // Take the 1 unit we just committed to the network off the cursor. removeItems()
                        // returns what it actually removed (not a "failure"): an empty result here means the
                        // cursor could not actually give up 1 unit after all, so the insert is rolled back.
                        final ItemStack removed = ia.removeItems(1, what.getReadOnlyStack(), null);
                        if (removed.isEmpty()) {
                            this.getCellInventory().extract(what, 1, Actionable.MODULATE, this.getActionSource());
                        }

                        this.updateHeld(player);
                    }
                }

                break;
            case ROLL_UP:
            case PICKUP_SINGLE:
                if (this.getPowerSource() == null || this.getCellInventory() == null) {
                    return;
                }

                if (slotItemKey != null) {
                    boolean canLift = true;
                    final ItemStack item = player.inventory.getItemStack();

                    if (!item.isEmpty()) {
                        if (item.getCount() >= item.getMaxStackSize()) {
                            canLift = false;
                        }
                        if (!slotItemKey.matches(item)) {
                            canLift = false;
                        }
                    }

                    if (canLift) {
                        final long extracted = Platform.poweredExtraction(this.getPowerSource(), this.getCellInventory(), slotItemKey, 1, this.getActionSource());
                        if (extracted > 0) {
                            final InventoryAdaptor ia = new AdaptorItemHandler(new WrapperCursorItemHandler(player.inventory));

                            final ItemStack fail = ia.addItems(slotItemKey.toStack((int) extracted));
                            if (!fail.isEmpty()) {
                                this.getCellInventory().insert(slotItemKey, extracted, Actionable.MODULATE, this.getActionSource());
                            }

                            this.updateHeld(player);
                        }
                    }
                }
                break;
            case PICKUP_OR_SET_DOWN:
                if (this.getPowerSource() == null || this.getCellInventory() == null) {
                    return;
                }

                if (player.inventory.getItemStack().isEmpty()) {
                    if (slotItemKey != null) {
                        final long extracted = Platform.poweredExtraction(this.getPowerSource(), this.getCellInventory(), slotItemKey, slotItemKey.getMaxStackSize(), this.getActionSource());
                        if (extracted > 0) {
                            player.inventory.setItemStack(slotItemKey.toStack((int) extracted));
                        } else {
                            player.inventory.setItemStack(ItemStack.EMPTY);
                        }
                        this.updateHeld(player);
                    }
                } else {
                    final ItemStack held = player.inventory.getItemStack();
                    final AEItemKey what = AEItemKey.of(held);
                    final long inserted = Platform.poweredInsert(this.getPowerSource(), this.getCellInventory(), what, held.getCount(), this.getActionSource());
                    final long remaining = held.getCount() - inserted;
                    if (remaining > 0) {
                        player.inventory.setItemStack(what.toStack((int) remaining));
                    } else {
                        player.inventory.setItemStack(ItemStack.EMPTY);
                    }
                    this.updateHeld(player);
                }

                break;
            case SPLIT_OR_PLACE_SINGLE:
                if (this.getPowerSource() == null || this.getCellInventory() == null) {
                    return;
                }

                if (player.inventory.getItemStack().isEmpty()) {
                    if (slotItemKey != null) {
                        final long maxSize = slotItemKey.getMaxStackSize();
                        final long simulated = this.getCellInventory().extract(slotItemKey, maxSize, Actionable.SIMULATE, this.getActionSource());

                        long extracted = 0;
                        if (simulated > 0) {
                            final long half = (Math.min(maxSize, simulated) + 1) >> 1;
                            extracted = Platform.poweredExtraction(this.getPowerSource(), this.getCellInventory(), slotItemKey, half, this.getActionSource());
                        }

                        if (extracted > 0) {
                            player.inventory.setItemStack(slotItemKey.toStack((int) extracted));
                        } else {
                            player.inventory.setItemStack(ItemStack.EMPTY);
                        }
                        this.updateHeld(player);
                    }
                } else {
                    final AEItemKey what = AEItemKey.of(player.inventory.getItemStack());
                    final long inserted = Platform.poweredInsert(this.getPowerSource(), this.getCellInventory(), what, 1, this.getActionSource());
                    if (inserted > 0) {
                        final ItemStack is = player.inventory.getItemStack();
                        is.setCount(is.getCount() - 1);
                        if (is.getCount() <= 0) {
                            player.inventory.setItemStack(ItemStack.EMPTY);
                        }
                        this.updateHeld(player);
                    }
                }

                break;
            case CREATIVE_DUPLICATE:
                if (player.capabilities.isCreativeMode && slotItemKey != null) {
                    player.inventory.setItemStack(slotItemKey.toStack(slotItemKey.getMaxStackSize()));
                    this.updateHeld(player);
                }
                break;
            case MOVE_REGION:

                if (this.getPowerSource() == null || this.getCellInventory() == null) {
                    return;
                }

                if (slotItemKey != null) {
                    final int playerInv = 9 * 4;
                    for (int slotNum = 0; slotNum < playerInv; slotNum++) {
                        final InventoryAdaptor adp = InventoryAdaptor.getAdaptor(player);
                        ItemStack myItem = slotItemKey.toStack(slotItemKey.getMaxStackSize());
                        myItem = adp.simulateAdd(myItem);

                        final long toExtract = slotItemKey.getMaxStackSize() - myItem.getCount();

                        final long extracted = Platform.poweredExtraction(this.getPowerSource(), this.getCellInventory(), slotItemKey, toExtract, this.getActionSource());
                        if (extracted > 0) {
                            adp.addItems(slotItemKey.toStack((int) extracted));
                        } else {
                            return;
                        }
                    }
                }

                break;
            default:
                break;
        }
    }

    protected void updateHeld(final EntityPlayerMP p) {
        if (Platform.isServer()) {
            try {
                NetworkHandler.instance()
                        .sendTo(
                                new PacketInventoryAction(InventoryAction.UPDATE_HAND, 0, GenericStack.fromItemStack(p.inventory.getItemStack())),
                                p);
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    protected ItemStack transferStackToContainer(final ItemStack input) {
        return this.shiftStoreItem(input);
    }

    private ItemStack shiftStoreItem(final ItemStack input) {
        if (this.getPowerSource() == null || this.getCellInventory() == null) {
            return input;
        }

        final AEItemKey what = AEItemKey.of(input);
        if (what == null) {
            return input;
        }

        final long inserted = Platform.poweredInsert(this.getPowerSource(), this.getCellInventory(), what, input.getCount(), this.getActionSource());
        final long remaining = input.getCount() - inserted;

        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }
        return what.toStack((int) remaining);
    }

    private void updateSlot(final Slot clickSlot) {
        // ???
        this.detectAndSendChanges();
    }

    private void sendCustomName() {
        if (!this.sentCustomName) {
            this.sentCustomName = true;
            if (Platform.isServer()) {
                ICustomNameObject name = null;

                if (this.part instanceof ICustomNameObject) {
                    name = (ICustomNameObject) this.part;
                }

                if (this.tileEntity instanceof ICustomNameObject) {
                    name = (ICustomNameObject) this.tileEntity;
                }

                if (this.obj instanceof ICustomNameObject) {
                    name = (ICustomNameObject) this.obj;
                }

                if (this instanceof ICustomNameObject) {
                    name = (ICustomNameObject) this;
                }

                if (name != null) {
                    if (name.hasCustomInventoryName()) {
                        this.setCustomName(name.getCustomInventoryName());
                    }

                    if (this.getCustomName() != null) {
                        try {
                            NetworkHandler.instance()
                                    .sendTo(new PacketValueConfig("CustomName", this.getCustomName()),
                                            (EntityPlayerMP) this.getInventoryPlayer().player);
                        } catch (final IOException e) {
                            AELog.debug(e);
                        }
                    }
                }
            }
        }
    }

    public void swapSlotContents(final int slotA, final int slotB) {
        final Slot a = this.getSlot(slotA);
        final Slot b = this.getSlot(slotB);

        // NPE protection...
        if (a == null || b == null) {
            return;
        }

        final ItemStack isA = a.getStack();
        final ItemStack isB = b.getStack();

        // something to do?
        if (isA.isEmpty() && isB.isEmpty()) {
            return;
        }

        // can take?

        if (!isA.isEmpty() && !a.canTakeStack(this.getInventoryPlayer().player)) {
            return;
        }

        if (!isB.isEmpty() && !b.canTakeStack(this.getInventoryPlayer().player)) {
            return;
        }

        // swap valid?

        if (!isB.isEmpty() && !a.isItemValid(isB)) {
            return;
        }

        if (!isA.isEmpty() && !b.isItemValid(isA)) {
            return;
        }

        ItemStack testA = isB.isEmpty() ? ItemStack.EMPTY : isB.copy();
        ItemStack testB = isA.isEmpty() ? ItemStack.EMPTY : isA.copy();

        // can put some back?
        if (!testA.isEmpty() && testA.getCount() > a.getSlotStackLimit()) {
            if (!testB.isEmpty()) {
                return;
            }

            final int totalA = testA.getCount();
            testA.setCount(a.getSlotStackLimit());
            testB = testA.copy();

            testB.setCount(totalA - testA.getCount());
        }

        if (!testB.isEmpty() && testB.getCount() > b.getSlotStackLimit()) {
            if (!testA.isEmpty()) {
                return;
            }

            final int totalB = testB.getCount();
            testB.setCount(b.getSlotStackLimit());
            testA = testB.copy();

            testA.setCount(totalB - testA.getCount());
        }

        a.putStack(testA);
        b.putStack(testB);
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, @NotNull EntityPlayer player) {
        if (slotId >= 0) {
            final var slot = this.getSlot(slotId);
            if (slot instanceof SlotDisabled) {
                return ItemStack.EMPTY;
            }

            if (slot instanceof AppEngSlot appEngSlot && clickTypeIn == ClickType.PICKUP) {
                var slotStack = slot.getStack();
                var draggedStack = this.invPlayer.getItemStack();

                // The default vanilla behavior assumes that slots can't hold more items than the default stack size.
                // Thus, it's possible to underflow the vanilla code when clicking non-empty slots with an item stack.
                if (!draggedStack.isEmpty()) {
                    if (appEngSlot.isItemValid(draggedStack)) {
                        if (slotStack.getItem() == draggedStack.getItem() && slotStack.getMetadata() == draggedStack.getMetadata() && ItemStack.areItemStackTagsEqual(slotStack, draggedStack)) {
                            // Slot size or stack size, whichever is smaller.
                            var maxSize = Math.min(appEngSlot.getSlotStackLimit(), draggedStack.getMaxStackSize());

                            // The maximum number of items that can be inserted into the slot, non-negative.
                            var maxInsertable = Math.min(draggedStack.getCount(),
                                    Math.max(0, maxSize - appEngSlot.getStack().getCount()));

                            if (maxInsertable != 0) {
                                var toInsert = Math.min(maxInsertable, dragType == 0 ? maxInsertable : 1);

                                draggedStack.shrink(toInsert);
                                slotStack.grow(toInsert);

                                slot.putStack(slot.getStack());
                                return ItemStack.EMPTY;
                            }
                        }
                    }
                }
                // Fixes taking and halving issues from oversized slots.
                else if (dragType == 0 || dragType == 1) {
                    if (slot.canTakeStack(player) && !slotStack.isEmpty()) {
                        var result = slotStack.copy();
                        var toTake = Math.min(slotStack.getCount(), slotStack.getMaxStackSize());
                        this.invPlayer.setItemStack(slot.decrStackSize(dragType == 0 ? toTake : (toTake + 1) / 2));

                        slot.putStack(slot.getStack());
                        return result;
                    }
                }

            }
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    public void onUpdate(final String field, final Object oldValue, final Object newValue) {

    }

    public void onSlotChange(final Slot s) {

    }

    public boolean isValidForSlot(final Slot s, final ItemStack i) {
        return true;
    }

    public MEStorage getCellInventory() {
        return this.cellInv;
    }

    public void setCellInventory(final MEStorage cellInv) {
        this.cellInv = cellInv;
    }

    public String getCustomName() {
        return this.customName;
    }

    public void setCustomName(final String customName) {
        this.customName = customName;
    }

    public InventoryPlayer getInventoryPlayer() {
        return this.invPlayer;
    }

    public boolean isValidContainer() {
        return this.isContainerValid;
    }

    public void setValidContainer(final boolean isContainerValid) {
        this.isContainerValid = isContainerValid;
    }

    public ContainerOpenContext getOpenContext() {
        return this.openContext;
    }

    public void setOpenContext(final ContainerOpenContext openContext) {
        this.openContext = openContext;
    }

    public IEnergySource getPowerSource() {
        return this.powerSrc;
    }

    public void setPowerSource(final IEnergySource powerSrc) {
        this.powerSrc = powerSrc;
    }
}
