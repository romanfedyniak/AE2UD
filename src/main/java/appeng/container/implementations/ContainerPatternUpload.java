/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.container.implementations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.IPatternContainer;
import appeng.api.networking.crafting.MachineIdentity;
import appeng.api.parts.IPart;
import appeng.container.AEBaseContainer;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCompressedNBT;
import appeng.helpers.IPatternUploadHost;
import appeng.helpers.ISubMenuHost;
import appeng.helpers.PatternContainers;
import appeng.helpers.PatternUpload;
import appeng.util.Platform;

/**
 * Backs the screen that asks where an encoded pattern should go.
 *
 * <p>It has no slots of its own: the pattern stays in the terminal until it is somewhere else, and the rows
 * are drawn from a list the server pushes. That list is rebuilt twice a second and sent only when something
 * a player can see about it has changed, so a container filling up while the screen is open shows.</p>
 *
 * @see appeng.client.gui.implementations.GuiPatternUpload
 */
public class ContainerPatternUpload extends AEBaseContainer {

    private static final int REBUILD_INTERVAL = 10;

    private final IPatternUploadHost host;

    /** Ids are kept per container object, so a rebuilt list does not move a row out from under a click. */
    private final Map<IPatternContainer, Long> ids = new HashMap<>();
    private final Map<Long, IPatternContainer> byId = new HashMap<>();
    private long nextId = 0;

    private String sent = "";
    private int untilRebuild = 0;

    public ContainerPatternUpload(final InventoryPlayer ip, final IPatternUploadHost host) {
        super(ip, (TileEntity) (host instanceof TileEntity ? host : null),
                (IPart) (host instanceof IPart ? host : null),
                (IGuiItemObject) (host instanceof IGuiItemObject ? host : null));
        this.host = host;
    }

    public ISubMenuHost getSubMenuHost() {
        return this.host;
    }

    /**
     * @return the container a row stands for, or null when the network has moved on since it was drawn.
     */
    @Nullable
    public IPatternContainer resolve(final long id) {
        return this.byId.get(id);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isClient()) {
            return;
        }

        // Asking every container whether it would run this pattern means walking the six faces of every
        // interface in the network, which is more than a chooser needs to do sixty times a second. Twice a
        // second is faster than anyone can build the assembler that would change an answer.
        if (this.untilRebuild-- > 0) {
            return;
        }
        this.untilRebuild = REBUILD_INTERVAL;

        final ItemStack pattern = this.host.getEncodedPattern();
        final ICraftingPatternDetails details = PatternUpload.detailsOf(pattern, this.getPlayerInv().player.world);

        final List<IPatternContainer> containers = PatternContainers.visible(this.grid());
        final StringBuilder signature = new StringBuilder();
        final List<Row> rows = new ArrayList<>();

        for (final IPatternContainer container : containers) {
            final Row row = new Row(this.idOf(container), container, pattern, details);
            rows.add(row);
            signature.append(row.id).append(':').append(row.name).append(':').append(row.free).append('/')
                    .append(row.slots).append(row.fits).append(';');
        }

        // Held onto only while they are still in the network, or a broken interface would be kept alive by
        // the screen that once listed it.
        this.ids.keySet().retainAll(containers);

        if (signature.toString().equals(this.sent)) {
            return;
        }

        this.sent = signature.toString();
        this.byId.clear();
        for (final Row row : rows) {
            this.byId.put(row.id, row.container);
        }

        try {
            NetworkHandler.instance().sendTo(new PacketCompressedNBT(encode(rows)),
                    (EntityPlayerMP) this.getPlayerInv().player);
        } catch (final IOException e) {
            // The screen keeps the list it has; the next change sends a whole one again.
        }
    }

    @Nullable
    private IGrid grid() {
        final IGridNode node = this.host.getActionableNode();
        return node == null || !node.isActive() ? null : node.getGrid();
    }

    private long idOf(final IPatternContainer container) {
        return this.ids.computeIfAbsent(container, ignored -> this.nextId++);
    }

    private static NBTTagCompound encode(final List<Row> rows) {
        final NBTTagCompound data = new NBTTagCompound();
        data.setInteger("rows", rows.size());

        for (int i = 0; i < rows.size(); i++) {
            final Row row = rows.get(i);
            final NBTTagCompound tag = new NBTTagCompound();

            tag.setLong("id", row.id);
            tag.setString("un", row.name);
            tag.setInteger("free", row.free);
            tag.setInteger("slots", row.slots);
            tag.setBoolean("fits", row.fits);
            if (!row.icon.isEmpty()) {
                tag.setTag("icon", row.icon.writeToNBT(new NBTTagCompound()));
            }

            data.setTag(Integer.toString(i), tag);
        }

        return data;
    }

    /** One line of the screen, as the server sees it. */
    private static final class Row {

        private final long id;
        private final IPatternContainer container;
        private final String name;
        private final ItemStack icon;
        private final int free;
        private final int slots;
        private final boolean fits;

        private Row(final long id, final IPatternContainer container, final ItemStack pattern,
                @Nullable final ICraftingPatternDetails details) {
            this.id = id;
            this.container = container;
            final MachineIdentity identity = container.getTerminalIdentity(pattern, details);
            this.name = identity.getName();
            this.icon = identity.getIcon();
            this.free = PatternContainers.freeSlots(container);
            this.slots = PatternContainers.usableSlots(container);
            this.fits = container.canAccept(pattern, details);
        }
    }
}
