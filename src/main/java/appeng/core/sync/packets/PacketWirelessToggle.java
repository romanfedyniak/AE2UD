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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import appeng.api.features.WirelessTerminalToggle;
import appeng.api.features.WirelessTerminalToggles;
import appeng.container.interfaces.IWirelessTerminalContainer;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;

/**
 * Sets an addon's toggle on the wireless terminal whose screen is open.
 */
public class PacketWirelessToggle extends AppEngPacket {

    private final ResourceLocation id;
    private final boolean on;

    // automatic.
    public PacketWirelessToggle(final ByteBuf stream) {
        this.id = new ResourceLocation(ByteBufUtils.readUTF8String(stream));
        this.on = stream.readBoolean();
    }

    // api
    public PacketWirelessToggle(final ResourceLocation id, final boolean on) {
        this.id = id;
        this.on = on;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        ByteBufUtils.writeUTF8String(data, id.toString());
        data.writeBoolean(on);
        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        final WirelessTerminalToggle toggle = WirelessTerminalToggles.get(this.id);
        if (toggle == null || !(player.openContainer instanceof IWirelessTerminalContainer container)) {
            return;
        }
        final ItemStack terminal = container.getTerminal();
        if (!terminal.isEmpty()) {
            toggle.set(terminal, this.on);
        }
    }
}
