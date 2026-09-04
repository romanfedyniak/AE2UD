/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.sync.packets;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.container.AEBaseContainer;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.features.AEFeature;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.NetworkIngredientAction;
import appeng.helpers.WirelessTerminalAccess;
import appeng.me.helpers.PlayerSource;
import appeng.util.Platform;

/**
 * What was asked of the network about the ingredient under the cursor in JEI.
 *
 * <p>The network reached is the one the open terminal is on, if a terminal is open; otherwise every wireless
 * terminal the player carries is tried in turn. Which of the two it was decides nothing else: the work and
 * the rules are the same either way.</p>
 */
public class PacketNetworkIngredient extends AppEngPacket {

    private final NetworkIngredientAction action;

    @Nullable
    private final AEKey what;

    /** One rather than a stack. Retrieval only; an amount to craft is asked for on its own screen. */
    private final boolean single;

    public PacketNetworkIngredient(final ByteBuf stream) {
        this.action = NetworkIngredientAction.values()[stream.readInt()];

        AEKey read = null;
        try {
            read = AEKey.readOptionalKey(stream);
        } catch (final Exception ex) {
            AELog.debug(ex);
        }

        this.what = read;
        this.single = stream.readBoolean();
    }

    public PacketNetworkIngredient(final NetworkIngredientAction action, final AEKey what, final boolean single) {
        this.action = action;
        this.what = what;
        this.single = single;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeInt(action.ordinal());

        try {
            AEKey.writeOptionalKey(data, what);
        } catch (final Exception ex) {
            AELog.debug(ex);
        }

        data.writeBoolean(single);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (this.what == null) {
            return;
        }

        if (this.action == NetworkIngredientAction.RETRIEVE) {
            this.retrieve(player);
        } else {
            this.craft(player);
        }
    }

    private void retrieve(final EntityPlayer player) {
        if (!AEConfig.instance().isFeatureEnabled(AEFeature.JEI_RETRIEVE)
                || !(this.what instanceof AEItemKey)) {
            return;
        }

        final AEItemKey target = (AEItemKey) this.what;
        final int wanted = this.single ? 1 : target.getMaxStackSize();

        // Only as much as there is somewhere to put: the network is not asked for what would land on the
        // floor, which is where the player inventory drops whatever it could not take.
        final int room = Math.min(wanted, roomFor(player, target));
        if (room <= 0) {
            player.sendMessage(PlayerMessages.NoRoomForItem.get());
            return;
        }

        final AEBaseContainer open = openTerminal(player);
        if (open != null) {
            final MEStorage storage = open.getCellInventory();
            final long extracted = Platform.poweredExtraction(open.getPowerSource(), storage, target, room,
                    open.getActionSource());

            if (extracted <= 0) {
                player.sendMessage(PlayerMessages.NothingInNetwork.get());
                return;
            }

            giveToPlayer(player, target, extracted, storage, open.getActionSource());
            return;
        }

        // Unlike pick block, this was aimed at one named thing on purpose, so an empty network is worth
        // saying rather than a normal outcome to pass over in silence.
        WirelessTerminalAccess.run(player, stack -> true, terminal -> {
            final MEStorage storage = terminal.getInventory();
            final IActionSource source = new PlayerSource(player, terminal);
            final long extracted = Platform.poweredExtraction(terminal, storage, target, room, source);

            if (extracted <= 0) {
                return false;
            }

            giveToPlayer(player, target, extracted, storage, source);
            return true;
        }, PlayerMessages.NothingInNetwork);
    }

    private void craft(final EntityPlayer player) {
        if (!AEConfig.instance().isFeatureEnabled(AEFeature.JEI_CRAFT_REQUEST)) {
            return;
        }

        // Ordering a job from the screen that asks how much of it to order would be a loop.
        if (player.openContainer instanceof ContainerCraftAmount) {
            return;
        }

        final AEBaseContainer open = openTerminal(player);
        if (open != null) {
            final ContainerOpenContext context = open.getOpenContext();
            final IGridNode node = ((ContainerMEMonitorable) open).getNetworkNode();

            if (context == null || node == null || !isCraftable(player, node.getGrid())) {
                return;
            }

            // The way the terminal's own craft button opens it, which handles a wireless host as well.
            Platform.openGUI(player, context.getTile(), context.getSide(), GuiBridge.GUI_CRAFTING_AMOUNT);
            this.fillAmountScreen(player);
            return;
        }

        WirelessTerminalAccess.run(player, stack -> true, terminal -> {
            final IGridNode node = terminal.getActionableNode();
            if (node == null || !isCraftable(player, node.getGrid())) {
                // Answered, in the sense that the player has been told why not.
                return true;
            }

            Platform.openGUI(player, terminal.getInventorySlot(), GuiBridge.GUI_CRAFTING_AMOUNT,
                    terminal.isBaubleSlot());
            this.fillAmountScreen(player);
            return true;
        });
    }

    /** The screen has just been opened on the same tick, so the container to fill is the player's current one. */
    private void fillAmountScreen(final EntityPlayer player) {
        if (player.openContainer instanceof ContainerCraftAmount) {
            final ContainerCraftAmount amount = (ContainerCraftAmount) player.openContainer;

            amount.getCraftingItem().putStack(this.what.wrapForDisplayOrFilter());
            amount.setItemToCraft(this.what);
            amount.setInitialAmount(this.what.getDefaultCraftAmount());
            amount.detectAndSendChanges();
        }
    }

    private boolean isCraftable(final EntityPlayer player, @Nullable final IGrid grid) {
        if (grid == null) {
            return false;
        }

        final ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);

        if (crafting == null || !crafting.isCraftable(this.what)) {
            player.sendMessage(PlayerMessages.CannotBeCrafted.get());
            return false;
        }

        return true;
    }

    /**
     * @return the open ME terminal, or null when there is none and a carried terminal has to answer instead.
     */
    @Nullable
    private static AEBaseContainer openTerminal(final EntityPlayer player) {
        if (player.openContainer instanceof ContainerMEMonitorable
                && ((ContainerMEMonitorable) player.openContainer).getCellInventory() != null) {
            return (AEBaseContainer) player.openContainer;
        }

        return null;
    }

    /** How much of one key the player's own inventory could take, never more than a stack of it. */
    private static int roomFor(final EntityPlayer player, final AEItemKey what) {
        final int max = what.getMaxStackSize();
        int room = 0;

        for (final ItemStack stack : player.inventory.mainInventory) {
            if (stack.isEmpty()) {
                room += max;
            } else if (what.matches(stack)) {
                room += Math.max(0, Math.min(stack.getMaxStackSize(), max) - stack.getCount());
            }

            if (room >= max) {
                return max;
            }
        }

        return room;
    }

    /**
     * Puts what was taken into the inventory, and puts back anything that would not fit after all rather
     * than dropping it at the player's feet.
     */
    private static void giveToPlayer(final EntityPlayer player, final AEItemKey what, final long extracted,
            final MEStorage storage, final IActionSource source) {
        if (extracted <= 0) {
            return;
        }

        final ItemStack stack = what.toStack((int) extracted);
        player.inventory.addItemStackToInventory(stack);

        if (!stack.isEmpty()) {
            storage.insert(what, stack.getCount(), Actionable.MODULATE, source);
        }
    }
}
