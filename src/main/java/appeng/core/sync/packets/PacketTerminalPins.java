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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

import appeng.api.stacks.AEKey;
import appeng.api.storage.IPlayerTerminalPins;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.implementations.TerminalCraftingPin;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;

public final class PacketTerminalPins extends AppEngPacket {
    private static final Map<Integer, PacketTerminalPins> PENDING_SNAPSHOTS = new HashMap<>();
    private static final int REQUEST = 0;
    private static final int SNAPSHOT = 1;
    private static final int SET_ROWS = 2;
    private static final int SET_PIN = 3;

    private final int operation;
    private int windowId;
    private boolean showCrafting;
    private boolean showPlayer;
    private int craftingRows;
    private int playerRows;
    private int slot;
    private AEKey key;
    private AEKey[] playerPins;
    private List<TerminalCraftingPin> craftingPins;

    public PacketTerminalPins(ByteBuf data) throws IOException {
        operation = data.readUnsignedByte();
        switch (operation) {
            case REQUEST:
                showCrafting = data.readBoolean();
                showPlayer = data.readBoolean();
                break;
            case SNAPSHOT:
                windowId = data.readUnsignedByte();
                craftingRows = data.readUnsignedByte();
                playerRows = data.readUnsignedByte();
                playerPins = new AEKey[IPlayerTerminalPins.MAX_PINS];
                for (int i = 0; i < playerPins.length; i++) {
                    playerPins[i] = AEKey.readOptionalKey(data);
                }
                int count = data.readUnsignedShort();
                craftingPins = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    craftingPins.add(new TerminalCraftingPin(data));
                }
                break;
            case SET_ROWS:
                craftingRows = data.readUnsignedByte();
                playerRows = data.readUnsignedByte();
                break;
            case SET_PIN:
                slot = data.readUnsignedByte();
                key = AEKey.readOptionalKey(data);
                break;
            default:
                throw new IOException("Unknown terminal pin operation " + operation);
        }
    }

    private PacketTerminalPins(int operation, ByteBuf payload) {
        this.operation = operation;
        ByteBuf data = Unpooled.buffer();
        data.writeInt(getPacketID());
        data.writeByte(operation);
        data.writeBytes(payload);
        configureWrite(data);
    }

    public static PacketTerminalPins request(boolean showCrafting, boolean showPlayer) {
        ByteBuf data = Unpooled.buffer();
        data.writeBoolean(showCrafting);
        data.writeBoolean(showPlayer);
        return new PacketTerminalPins(REQUEST, data);
    }

    public static PacketTerminalPins setRows(int craftingRows, int playerRows) {
        ByteBuf data = Unpooled.buffer();
        data.writeByte(craftingRows);
        data.writeByte(playerRows);
        return new PacketTerminalPins(SET_ROWS, data);
    }

    public static PacketTerminalPins setPin(int slot, AEKey key) throws IOException {
        ByteBuf data = Unpooled.buffer();
        data.writeByte(slot);
        AEKey.writeOptionalKey(data, key);
        return new PacketTerminalPins(SET_PIN, data);
    }

    public static PacketTerminalPins snapshot(int windowId, int craftingRows, int playerRows, AEKey[] playerPins,
            Collection<TerminalCraftingPin> craftingPins) throws IOException {
        ByteBuf data = Unpooled.buffer();
        data.writeByte(windowId);
        data.writeByte(craftingRows);
        data.writeByte(playerRows);
        for (int i = 0; i < IPlayerTerminalPins.MAX_PINS; i++) {
            AEKey.writeOptionalKey(data, playerPins[i]);
        }
        data.writeShort(craftingPins.size());
        for (TerminalCraftingPin pin : craftingPins) {
            pin.writeToPacket(data);
        }
        return new PacketTerminalPins(SNAPSHOT, data);
    }

    @Override
    public void clientPacketData(INetworkInfo network, AppEngPacket packet, EntityPlayer player) {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (operation == SNAPSHOT) {
            ContainerMEMonitorable container = null;
            if (player.openContainer instanceof ContainerMEMonitorable
                    && player.openContainer.windowId == this.windowId) {
                container = (ContainerMEMonitorable) player.openContainer;
            } else if (player.inventoryContainer instanceof ContainerMEMonitorable
                    && player.inventoryContainer.windowId == this.windowId) {
                container = (ContainerMEMonitorable) player.inventoryContainer;
            }
            if (container != null) {
                this.applySnapshot(container);
                if (screen instanceof GuiMEMonitorable && ((GuiMEMonitorable) screen).inventorySlots == container) {
                    ((GuiMEMonitorable) screen).applyTerminalPinSnapshot(true);
                }
            } else {
                PENDING_SNAPSHOTS.put(this.windowId, this);
            }
        }
    }

    public static void applyPendingSnapshot(ContainerMEMonitorable container) {
        PacketTerminalPins pending = PENDING_SNAPSHOTS.remove(container.windowId);
        if (pending != null) {
            pending.applySnapshot(container);
        }
    }

    private void applySnapshot(ContainerMEMonitorable container) {
        container.receiveTerminalPins(this.craftingRows, this.playerRows, this.playerPins, this.craftingPins);
    }

    @Override
    public void serverPacketData(INetworkInfo network, AppEngPacket packet, EntityPlayer player) {
        if (!(player.openContainer instanceof ContainerMEMonitorable)) {
            return;
        }
        ContainerMEMonitorable container = (ContainerMEMonitorable) player.openContainer;
        if (operation == REQUEST) {
            container.requestTerminalPins(showCrafting, showPlayer);
        } else if (operation == SET_ROWS) {
            container.setTerminalPinRows(craftingRows, playerRows);
        } else if (operation == SET_PIN) {
            container.setTerminalPlayerPin(slot, key);
        }
    }
}
