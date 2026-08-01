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
import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.ContainerItemStrategy;
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
import appeng.api.stacks.AEFluidKey;
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
import appeng.util.inv.GenericStackInv;
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

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

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

    /**
     * @return what the given stack holds, as a key and an amount, or null if it holds nothing this mod can
     *         name. A fluid container reports its fluid; everything else reports nothing, including an
     *         already-wrapped placeholder, which is handled by the ordinary path.
     */
    @Nullable
    private static GenericStack containedStackOf(final ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        final FluidStack fluid = FluidUtil.getFluidContained(stack);
        if (fluid != null && fluid.amount > 0) {
            return new GenericStack(AEFluidKey.of(fluid), fluid.amount);
        }

        return null;
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

            // Filling or emptying a held container against a stocked slot, rather than against the network.
            // Upstream does the same for any slot backed by a generic inventory; here it is what lets a
            // bucket be filled from an interface's fluid slot, which is otherwise unreachable - the slot
            // rightly refuses to hand the key over as an item.
            if (s instanceof SlotGenericStorage genericSlot
                    && (action == InventoryAction.FILL_ITEM || action == InventoryAction.EMPTY_ITEM)) {
                this.handleSlotContainerItemAction(player, genericSlot, action);
                return;
            }

            if (s instanceof SlotFake) {
                // A filter slot switched off by pulling a capacity card takes no clicks. This used to be
                // enforced inside AppEngSlot.putStack, where it also swallowed the server's own updates.
                if (!((SlotFake) s).isSlotEnabled()) {
                    return;
                }

                final ItemStack hand = player.inventory.getItemStack();

                // A wrapped key carries its amount in NBT, not in the ItemStack's count - a placeholder is
                // always exactly one item and cannot stack. Every amount-changing case below works on
                // getCount(), so on a fluid they grew a number nothing reads while the configured amount
                // stayed put, and the slot ended up claiming a stack size the wrapper is not allowed to
                // have. Handled here instead, in the key type's own unit.
                final GenericStack wrapped = GenericStack.unwrapItemStack(s.getStack());
                if (wrapped != null && this.adjustWrappedAmount(s, wrapped, action, hand)) {
                    return;
                }

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
                            // The slot's own ceiling, like SPLIT_OR_PLACE_SINGLE below. Eight times the
                            // item's stack size is not a limit this slot has: it let a stackable item reach
                            // 512 and stopped a bucket at 8.
                            is.setCount((int) Math.min(this.maxAmountIn(s, AEItemKey.of(is)), is.getCount() + 1L));
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
                                // Up to what the slot holds, not the item's own stack size: a config slot is
                                // a number rather than a stack, and this one stopped at 64 in a slot of 512.
                                is.setCount((int) Math.min(this.maxAmountIn(s, AEItemKey.of(is)), is.getCount() + 1L));
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
                    case EMPTY_ITEM: {
                        // Set the filter to what the held item *contains* rather than to the item.
                        // Backported from upstream's InventoryAction.EMPTY_ITEM on fake slots; it is the
                        // only way to express a fluid filter by hand, since a bucket dropped into a slot
                        // is otherwise just a bucket. Fluids are the only container type this fork knows;
                        // a future key type would extend the lookup here rather than the slot.
                        final GenericStack contained = containedStackOf(hand);
                        if (contained != null) {
                            // Clicking the same contents again adds another helping, like clicking the same
                            // item again does.
                            final GenericStack current = GenericStack.unwrapItemStack(s.getStack());
                            final long already = current != null && current.what().equals(contained.what()) ? current.amount() : 0;
                            s.putStack(GenericStack.wrapInItemStack(contained.what(),
                                    Math.min(this.maxAmountIn(s, contained.what()), already + contained.amount())));
                        }
                        break;
                    }
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

        // Filling or emptying a held container against the network. Handled before the item-only switch
        // below, because these are the two actions whose whole point is a key type the player's inventory
        // cannot hold directly.
        if (action == InventoryAction.FILL_ITEM || action == InventoryAction.EMPTY_ITEM) {
            this.handleContainerItemAction(player, action);
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

    /**
     * Applies an amount-changing fake-slot action to a wrapped key, stepping by the key type's own unit - a
     * bucket per notch for fluids, since a millibucket per notch would mean a thousand notches to fill one.
     * Ctrl (halve/double) is what reaches the amounts in between, which is how the 40mB of a processing
     * recipe gets configured.
     *
     * @return true if the action was consumed here; false to let the ordinary item path run, which is what
     *         happens when the player is placing a different key rather than adjusting this one.
     */
    private boolean adjustWrappedAmount(final Slot s, final GenericStack current, final InventoryAction action,
            final ItemStack hand) {
        if (!hand.isEmpty() && action != InventoryAction.HALVE && action != InventoryAction.DOUBLE) {
            // Holding something means the player is placing a different key, not tuning this one.
            return false;
        }

        final long adjusted = adjustAmount(current.amount(), current.what().getAmountPerUnit(), action);
        if (adjusted < 0) {
            return false;
        }

        s.putStack(GenericStack.wrapInItemStack(current.what(), Math.min(this.maxAmountIn(s, current.what()), adjusted)));
        return true;
    }

    /**
     * Steps a wrapped key's amount for one of the amount-changing slot actions, in the key type's own unit -
     * a bucket per notch for fluids, since a millibucket per notch would be a thousand notches to fill one.
     * Ctrl (halve/double) is what reaches the amounts in between.
     * <p>
     * Shared because the interface configuration terminal carries its own copy of the fake-slot actions,
     * working on {@code ItemStack} counts, and a wrapped key has no count to work on - it is always exactly
     * one item whatever amount it stands for.
     *
     * @return the new amount, or -1 if this action does not change one.
     */
    public static long adjustAmount(final long current, final int amountPerUnit, final InventoryAction action) {
        final long unit = Math.max(1, amountPerUnit);
        long amount = current;

        switch (action) {
            case PLACE_SINGLE:
                // To the next whole unit, not one unit further along. Scrolling up from a hand-tuned 1mB
                // should read 1B, not 1001mB.
                amount = (amount / unit + 1) * unit;
                break;
            case PICKUP_SINGLE:
            case SPLIT_OR_PLACE_SINGLE:
                // Down to the previous whole unit, so an off-grid amount snaps back onto it first.
                amount = amount % unit == 0 ? amount - unit : amount / unit * unit;
                break;
            case HALVE:
                amount /= 2;
                break;
            case DOUBLE:
                // Guard the overflow the item path gets for free from its stack limit.
                amount = amount > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : amount * 2;
                break;
            default:
                return -1;
        }

        // Never empties the slot, matching the item path: PICKUP_SINGLE on a count of one leaves the one.
        // The floor is a single base unit rather than a whole unit, because a pattern may legitimately ask
        // for less than a bucket.
        return Math.max(1, amount);
    }

    /**
     * Fills the held container from one slot of a generic inventory, or empties it into that slot. The
     * network is not involved: this moves what the slot itself is holding, which is what makes an
     * interface's stock reachable by hand.
     */
    private void handleSlotContainerItemAction(final EntityPlayerMP player, final SlotGenericStorage slot,
            final InventoryAction action) {
        final ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) {
            return;
        }

        final GenericStackInv inv = slot.getGenericInv();
        final int index = slot.getGenericSlot();

        if (action == InventoryAction.FILL_ITEM) {
            final AEKey what = inv.getKey(index);
            if (!ContainerItemStrategies.isKeySupported(what)) {
                return;
            }

            final ContainerItemStrategy.Context ctx = ContainerItemStrategies.openContext(held, what.getType());
            if (ctx == null) {
                return;
            }

            final long room = ctx.insert(what, Math.max(1, what.getAmountPerUnit()), Actionable.SIMULATE);
            final long available = inv.extract(index, what, room, Actionable.SIMULATE);
            if (available <= 0) {
                return;
            }

            final long moved = ctx.insert(what, available, Actionable.MODULATE);
            if (moved > 0) {
                inv.extract(index, what, moved, Actionable.MODULATE);
                this.replaceHeldWith(player, held, ctx.getContainer());
            }
            return;
        }

        final ContainerItemStrategy.Context ctx = ContainerItemStrategies.openContext(held, null);
        if (ctx == null) {
            return;
        }

        final GenericStack content = ctx.getExtractableContent();
        if (content == null) {
            return;
        }

        final AEKey what = content.what();
        final long drainable = ctx.extract(what, Math.max(1, what.getAmountPerUnit()), Actionable.SIMULATE);
        final long room = inv.insert(index, what, drainable, Actionable.SIMULATE);
        if (room <= 0) {
            return;
        }

        final long drained = ctx.extract(what, room, Actionable.MODULATE);
        if (drained > 0) {
            inv.insert(index, what, drained, Actionable.MODULATE);
            this.replaceHeldWith(player, held, ctx.getContainer());
        }
    }

    /**
     * The ceiling for an amount typed into a fake slot, in the key's own units.
     * <p>
     * A slot's stack limit is expressed in items - it is what an {@code IItemHandler} reports. A filter slot
     * has a limit of one and therefore no meaningful ceiling, so those are left unbounded.
     */
    public long maxAmountIn(final Slot s, final AEKey what) {
        final int slotLimit = s.getSlotStackLimit();
        return slotLimit <= 1 ? Long.MAX_VALUE : Platform.scaleAmountFromItems(slotLimit, what);
    }

    /**
     * Fills the held container from the network, or empties it into the network, through the
     * {@link ContainerItemStrategy} registered for the key type involved. One unit per click - a bucket for
     * fluids - matching {@link AEKey#getAmountPerUnit()}.
     * <p>
     * Replaces the three near-identical copies of this dance that lived in the fluid-only containers. Nothing
     * here mentions fluids: a key type that registers a strategy gets the interaction for free.
     */
    private void handleContainerItemAction(final EntityPlayerMP player, final InventoryAction action) {
        if (this.getPowerSource() == null || this.getCellInventory() == null) {
            return;
        }

        final ItemStack held = player.inventory.getItemStack();

        if (action == InventoryAction.FILL_ITEM) {
            final AEKey what = this.clientRequestedTargetItem;
            if (!ContainerItemStrategies.isKeySupported(what)) {
                return;
            }

            if (held.isEmpty()) {
                this.fillBorrowedContainer(player, what);
            } else {
                this.fillHeldContainer(player, held, what);
            }
        } else if (!held.isEmpty()) {
            this.emptyHeldContainer(player, held);
        }
    }

    /**
     * Clicking a key with an empty hand: borrow an empty container from the network, fill it, and hand it over -
     * putting it straight back if this key turned out not to fit in it after all. Saves the player fetching a
     * bucket first, which is the whole point of the interaction.
     */
    private void fillBorrowedContainer(final EntityPlayerMP player, final AEKey what) {
        final ItemStack container = ContainerItemStrategies.getEmptyContainerFor(what);
        if (container.isEmpty()) {
            return;
        }

        final AEItemKey containerKey = AEItemKey.of(container);
        if (containerKey == null) {
            return;
        }

        // Unpowered on purpose: the container is a loan, not a withdrawal, and charging for it would mean
        // charging again when it goes back. Upstream makes the same call.
        if (this.getCellInventory().extract(containerKey, 1, Actionable.MODULATE, this.getActionSource()) < 1) {
            return;
        }

        if (!this.fillHeldContainer(player, container, what)) {
            this.getCellInventory().insert(containerKey, 1, Actionable.MODULATE, this.getActionSource());
        }
    }

    /**
     * @return true if anything was actually moved into the container.
     */
    private boolean fillHeldContainer(final EntityPlayerMP player, final ItemStack held, final AEKey what) {
        final ContainerItemStrategy.Context ctx = ContainerItemStrategies.openContext(held, what.getType());
        if (ctx == null) {
            return false;
        }

        // Room in the container first: asking the network for a bucket we cannot hold would charge power
        // for nothing.
        final long room = ctx.insert(what, Math.max(1, what.getAmountPerUnit()), Actionable.SIMULATE);
        if (room <= 0) {
            return false;
        }

        final long available = Platform.poweredExtraction(this.getPowerSource(), this.getCellInventory(), what, room,
                this.getActionSource(), Actionable.SIMULATE);
        if (available <= 0) {
            return false;
        }

        final long extracted = Platform.poweredExtraction(this.getPowerSource(), this.getCellInventory(), what,
                available, this.getActionSource());
        if (extracted <= 0) {
            return false;
        }

        final long inserted = ctx.insert(what, extracted, Actionable.MODULATE);
        if (inserted < extracted) {
            // Put back whatever the container refused rather than voiding it, the same leniency the
            // import strategies use.
            this.getCellInventory().insert(what, extracted - inserted, Actionable.MODULATE, this.getActionSource());
        }

        if (inserted <= 0) {
            return false;
        }

        this.replaceHeldWith(player, held, ctx.getContainer());
        return true;
    }

    private void emptyHeldContainer(final EntityPlayerMP player, final ItemStack held) {
        // No key type asked for: the container decides what comes out of it.
        final ContainerItemStrategy.Context ctx = ContainerItemStrategies.openContext(held, null);
        if (ctx == null) {
            return;
        }

        final GenericStack content = ctx.getExtractableContent();
        if (content == null) {
            return;
        }

        final AEKey what = content.what();
        final long drainable = ctx.extract(what, Math.max(1, what.getAmountPerUnit()), Actionable.SIMULATE);
        if (drainable <= 0) {
            return;
        }

        final long storable = Platform.poweredInsert(this.getPowerSource(), this.getCellInventory(), what, drainable,
                this.getActionSource(), Actionable.SIMULATE);
        if (storable <= 0) {
            return;
        }

        final long drained = ctx.extract(what, storable, Actionable.MODULATE);
        if (drained <= 0) {
            return;
        }

        final long inserted = Platform.poweredInsert(this.getPowerSource(), this.getCellInventory(), what, drained,
                this.getActionSource());
        if (inserted < drained) {
            // The network took less than the simulation promised; hand the rest back to the container.
            ctx.insert(what, drained - inserted, Actionable.MODULATE);
        }

        this.replaceHeldWith(player, held, ctx.getContainer());
    }

    /**
     * Swaps one container out of the held stack for its filled/emptied result. The strategy worked on a copy of
     * size one, so a held stack of several buckets keeps the rest in hand and the changed one goes to the
     * inventory - or on the floor if there is no room.
     */
    private void replaceHeldWith(final EntityPlayerMP player, final ItemStack held, final ItemStack result) {
        if (held.getCount() <= 1) {
            player.inventory.setItemStack(result);
        } else {
            held.shrink(1);
            if (!player.inventory.addItemStackToInventory(result)) {
                player.dropItem(result, false);
            }
        }
        this.updateHeld(player);
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

        // Only one of these applies: the remainder the first leaves goes back where it came from and needs
        // no ceiling. getItemStackLimit, not getSlotStackLimit - a hotbar slot takes exactly one bucket.
        boolean split = false;

        if (!testA.isEmpty() && testA.getCount() > a.getItemStackLimit(testA)) {
            if (!testB.isEmpty()) {
                return;
            }

            final int totalA = testA.getCount();
            testA.setCount(a.getItemStackLimit(testA));
            testB = testA.copy();

            testB.setCount(totalA - testA.getCount());
            split = true;
        }

        if (!split && !testB.isEmpty() && testB.getCount() > b.getItemStackLimit(testB)) {
            if (!testA.isEmpty()) {
                return;
            }

            final int totalB = testB.getCount();
            testB.setCount(b.getItemStackLimit(testB));
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
