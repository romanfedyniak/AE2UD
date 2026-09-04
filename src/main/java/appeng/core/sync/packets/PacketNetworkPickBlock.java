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
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.features.AEFeature;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.WirelessTerminalAccess;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.me.helpers.PlayerSource;
import appeng.util.Platform;

/**
 * What the vanilla pick block key could not find in the player's own inventory, asked of the network a
 * carried wireless terminal reaches.
 *
 * <p>The client has already decided what it wants and where it goes, because it is the side that knows what
 * the crosshair is on and which slot the hand is in. The server checks that the slot can hold the request
 * before it takes anything out, so a hotbar that filled up in the meantime costs the network nothing.</p>
 */
public class PacketNetworkPickBlock extends AppEngPacket {

    @Nullable
    private final AEKey what;
    private final int amount;
    private final int hotbarSlot;

    public PacketNetworkPickBlock(final ByteBuf stream) {
        AEKey read = null;

        try {
            read = AEKey.readOptionalKey(stream);
        } catch (final Exception ex) {
            AELog.debug(ex);
        }

        this.what = read;
        this.amount = stream.readInt();
        this.hotbarSlot = stream.readInt();
    }

    public PacketNetworkPickBlock(final AEItemKey what, final int amount, final int hotbarSlot) {
        this.what = what;
        this.amount = amount;
        this.hotbarSlot = hotbarSlot;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());

        try {
            AEKey.writeOptionalKey(data, what);
        } catch (final Exception ex) {
            AELog.debug(ex);
        }

        data.writeInt(amount);
        data.writeInt(hotbarSlot);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (!AEConfig.instance().isFeatureEnabled(AEFeature.NETWORK_PICK_BLOCK)
                || !(this.what instanceof AEItemKey) || this.amount <= 0
                || !InventoryPlayer.isHotbar(this.hotbarSlot) || player.capabilities.isCreativeMode) {
            return;
        }

        final AEItemKey target = (AEItemKey) this.what;

        WirelessTerminalAccess.run(player, PacketNetworkPickBlock::answersPickBlock,
                terminal -> give(player, terminal, target, this.amount, this.hotbarSlot));
    }

    /** A terminal only answers the key while its own setting says so. */
    private static boolean answersPickBlock(final ItemStack stack) {
        final IWirelessTermHandler handler = AEApi.instance().registries().wireless()
                .getWirelessTerminalHandler(stack);

        return handler != null && handler.getConfigManager(stack).getSetting(Settings.PICK_BLOCK) == YesNo.YES;
    }

    private static boolean give(final EntityPlayer player, final WirelessTerminalGuiObject terminal,
            final AEItemKey what, final int amount, final int hotbarSlot) {
        final ItemStack current = player.inventory.getStackInSlot(hotbarSlot);
        final int room;

        if (current.isEmpty()) {
            room = Math.min(amount, what.getMaxStackSize());
        } else if (what.matches(current)) {
            final int limit = Math.min(current.getMaxStackSize(), what.getMaxStackSize());
            room = Math.min(amount, limit - current.getCount());
        } else {
            // The slot filled up between the click and this packet. Nothing to do, and nothing was spent.
            return false;
        }

        if (room <= 0) {
            return false;
        }

        final long extracted = Platform.poweredExtraction(terminal, terminal.getInventory(), what, room,
                new PlayerSource(player, terminal));

        if (extracted <= 0) {
            return false;
        }

        if (current.isEmpty()) {
            player.inventory.setInventorySlotContents(hotbarSlot, what.toStack((int) extracted));
        } else {
            current.grow((int) extracted);
            player.inventory.setInventorySlotContents(hotbarSlot, current);
        }

        return true;
    }
}
