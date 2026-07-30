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

package appeng.core.sync.packets;


import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.me.SlotDisconnected;
import appeng.container.AEBaseContainer;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.container.implementations.ContainerInterfaceConfigurationTerminal;
import appeng.container.implementations.ContainerInterfaceConfigurationTerminal.ConfigTracker;
import appeng.container.slot.IJEITargetSlot;
import appeng.container.slot.SlotFake;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.core.sync.network.NetworkHandler;
import appeng.helpers.InventoryAction;
import appeng.util.Platform;
import appeng.util.helpers.ItemHandlerUtil;
import appeng.util.inv.WrapperRangeItemHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;


/**
 * A grab-bag of container inventory clicks, autocrafting requests and JEI/HEI ghost-item drops. Ported
 * from {@code IAEItemStack} to {@code GenericStack} (the carried payload -- see CONTRACT.md's wave 4
 * prerequisites for the two pinned constructors).
 * <p/>
 * Fork-specific mechanics preserved here (CONTRACT.md §10), none of which exist upstream:
 * <ul>
 *   <li>{@link InventoryAction#AUTO_CRAFT} -- opens the craft-amount GUI and forwards the container's
 *       target stack into it.</li>
 *   <li>{@link InventoryAction#PLACE_JEI_GHOST_ITEM} into three destinations: a
 *       {@link ContainerFluidConfigurable} fluid config slot (with the 1000 mB/bucket default it applies),
 *       {@link ContainerInterfaceConfigurationTerminal} (via {@code ConfigTracker}/{@code getSlotByID} and
 *       {@link WrapperRangeItemHandler}), and a plain {@link SlotFake}.</li>
 *   <li>The {@link InventoryAction#UPDATE_HAND} echo back to the sender after a ghost item is cleared.</li>
 * </ul>
 * The old code detected "this ghost item is really a fluid" by unwrapping
 * {@code slotItem.getDefinition().getTagCompound()} through {@code AEFluidStack.fromNBT} -- i.e. the fluid
 * was smuggled inside a dummy item's NBT. A {@link GenericStack} payload carries the fluid as an
 * {@link AEFluidKey} directly, so that unwrap is replaced by a plain {@code instanceof AEFluidKey} test.
 */
public class PacketInventoryAction extends AppEngPacket {

    private final InventoryAction action;
    private final int slot;
    private final long id;
    @Nullable
    private final GenericStack slotItem;

    // automatic.
    public PacketInventoryAction(final ByteBuf stream) throws IOException {
        this.action = InventoryAction.values()[stream.readInt()];
        this.slot = stream.readInt();
        this.id = stream.readLong();
        final boolean hasItem = stream.readBoolean();

        if (hasItem) {
            this.slotItem = GenericStack.readBuffer(stream);
        } else {
            this.slotItem = null;
        }
    }

    // api
    public PacketInventoryAction(final InventoryAction action, final int slot, @Nullable final GenericStack slotItem) throws IOException {
        if (Platform.isClient()) {
            throw new IllegalStateException("invalid packet, client cannot post inv actions with stacks.");
        }

        this.action = action;
        this.slot = slot;
        this.id = 0;
        this.slotItem = slotItem;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeInt(action.ordinal());
        data.writeInt(slot);
        data.writeLong(this.id);

        if (slotItem == null) {
            data.writeBoolean(false);
        } else {
            data.writeBoolean(true);
            GenericStack.writeBuffer(slotItem, data);
        }

        this.configureWrite(data);
    }

    public PacketInventoryAction(final InventoryAction action, final IJEITargetSlot slot, @Nullable final GenericStack slotItem) throws IOException {

        this.action = action;
        if (slot instanceof SlotFake) {
            this.slot = ((SlotFake) slot).slotNumber;
            this.id = 0;
        } else if (slot instanceof SlotDisconnected) {
            this.slot = ((SlotDisconnected) slot).getSlotIndex();
            this.id = ((SlotDisconnected) slot).getSlot().getId();
        } else {
            throw new IllegalArgumentException("Not a slot this packet can address: " + slot);
        }
        this.slotItem = slotItem;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeInt(action.ordinal());
        data.writeInt(this.slot);
        data.writeLong(this.id);

        if (slotItem == null) {
            data.writeBoolean(false);
        } else {
            data.writeBoolean(true);
            GenericStack.writeBuffer(slotItem, data);
        }

        this.configureWrite(data);
    }

    // api
    public PacketInventoryAction(final InventoryAction action, final int slot, final long id) {
        this.action = action;
        this.slot = slot;
        this.id = id;
        this.slotItem = null;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeInt(action.ordinal());
        data.writeInt(slot);
        data.writeLong(id);
        data.writeBoolean(false);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        final EntityPlayerMP sender = (EntityPlayerMP) player;
        if (sender.openContainer instanceof AEBaseContainer) {
            final AEBaseContainer baseContainer = (AEBaseContainer) sender.openContainer;
            if (this.action == InventoryAction.AUTO_CRAFT) {
                final ContainerOpenContext context = baseContainer.getOpenContext();
                if (context != null) {
                    final TileEntity te = context.getTile();
                    Platform.openGUI(sender, te, baseContainer.getOpenContext().getSide(), GuiBridge.GUI_CRAFTING_AMOUNT);

                    if (sender.openContainer instanceof ContainerCraftAmount) {
                        final ContainerCraftAmount cca = (ContainerCraftAmount) sender.openContainer;

                        final AEKey target = baseContainer.getTargetStack();
                        if (target != null) {
                            cca.getCraftingItem().putStack(target.wrapForDisplayOrFilter());
                            // This is the *actual* item that matters, not the display item above
                            cca.setItemToCraft(target);
                        }

                        cca.detectAndSendChanges();
                    }
                }
            } else if (this.action == InventoryAction.PLACE_JEI_GHOST_ITEM) {
                if (sender.openContainer instanceof ContainerInterfaceConfigurationTerminal) {
                    ConfigTracker inv = ((ContainerInterfaceConfigurationTerminal) sender.openContainer).getSlotByID(this.id);
                    final IItemHandler theSlot = new WrapperRangeItemHandler(inv.getServer(), 0, slot + 1);

                    ItemHandlerUtil.setStackInSlot(theSlot, this.slot, GenericStack.wrapInItemStack(this.slotItem));

                } else if (this.slot < sender.openContainer.inventorySlots.size()) {
                    Slot senderSlot = sender.openContainer.inventorySlots.get(this.slot);
                    if (senderSlot instanceof SlotFake) {
                        if (this.slotItem != null) {
                            senderSlot.putStack(GenericStack.wrapInItemStack(this.slotItem));
                            if (senderSlot.getStack().isEmpty() && this.slotItem.what() instanceof AEFluidKey) {
                                // Kept for parity with the pre-port fallback; GenericStack.wrapInItemStack
                                // cannot itself return an empty stack for a non-null input, so this retry is
                                // unreachable today. See the migration report for why it is kept anyway.
                                senderSlot.putStack(GenericStack.wrapInItemStack(this.slotItem));
                            }
                        } else {
                            senderSlot.putStack(ItemStack.EMPTY);
                        }
                        try {
                            NetworkHandler.instance().sendTo(new PacketInventoryAction(InventoryAction.UPDATE_HAND, 0, (GenericStack) null), sender);
                        } catch (final IOException e) {
                            AELog.debug(e);
                        }
                    }
                }
            } else {
                baseContainer.doAction(sender, this.action, this.slot, this.id);
            }
        }
    }

    @Override
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        if (this.action == InventoryAction.UPDATE_HAND) {
            if (this.slotItem == null) {
                AppEng.proxy.getPlayers().get(0).inventory.setItemStack(ItemStack.EMPTY);
            } else {
                AppEng.proxy.getPlayers().get(0).inventory.setItemStack(GenericStack.wrapInItemStack(this.slotItem));
            }
        }
    }
}
