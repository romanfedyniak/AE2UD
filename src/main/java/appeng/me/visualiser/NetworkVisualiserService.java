/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.me.visualiser;


import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.util.AEPartLocation;
import appeng.core.AEConfig;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketNetworkVisualiser;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;


/**
 * Keeps the network visualiser fed, and costs nothing at all while nobody is holding one.
 * <p>
 * A held visualiser says so once a tick; everything else happens here, at the end of the server tick. The
 * picture is built per network rather than per player, so five people looking at one base pay for one walk.
 */
public final class NetworkVisualiserService {

    public static final NetworkVisualiserService INSTANCE = new NetworkVisualiserService();

    private final Map<EntityPlayerMP, Watcher> watchers = new HashMap<>();
    private final Map<SnapshotKey, Snapshot> snapshots = new HashMap<>();

    private long tick;

    private NetworkVisualiserService() {
    }

    /**
     * Called every tick by a visualiser that is being held. Resolving the network is left until the end of
     * the tick, so that this stays a single map write.
     */
    public void watch(final EntityPlayerMP player, final World world, final BlockPos pos,
            final AEPartLocation side) {
        final Watcher watcher = this.watchers.computeIfAbsent(player, key -> new Watcher());

        if (!Objects.equals(watcher.pos, pos) || watcher.side != side || watcher.world != world) {
            watcher.world = world;
            watcher.pos = pos;
            watcher.side = side;
            watcher.sentVersion = -1;
            watcher.complaint = null;
        }

        watcher.lastSeen = this.tick;
    }

    /** Clears the picture on that player's screen, whether they put the tool away or unbound it. */
    public void stopWatching(final EntityPlayerMP player) {
        if (this.watchers.remove(player) != null && canSend(player)) {
            NetworkHandler.instance().sendTo(PacketNetworkVisualiser.clear(), player);
        }
    }

    public void onServerTick() {
        if (this.watchers.isEmpty()) {
            this.snapshots.clear();
        } else {
            this.dropStaleWatchers();
            this.serveWatchers();
            this.snapshots.values().removeIf(snapshot -> snapshot.watchers == 0);
        }

        this.tick++;
    }

    /** Everything is thrown away between worlds; a grid from the last one must not keep a walk alive. */
    public void onServerStopped() {
        this.watchers.clear();
        this.snapshots.clear();
    }

    private void dropStaleWatchers() {
        final Iterator<Map.Entry<EntityPlayerMP, Watcher>> it = this.watchers.entrySet().iterator();

        while (it.hasNext()) {
            final Map.Entry<EntityPlayerMP, Watcher> entry = it.next();

            if (entry.getValue().lastSeen != this.tick) {
                it.remove();

                if (canSend(entry.getKey())) {
                    NetworkHandler.instance().sendTo(PacketNetworkVisualiser.clear(), entry.getKey());
                }
            }
        }
    }

    private void serveWatchers() {
        for (final Snapshot snapshot : this.snapshots.values()) {
            snapshot.watchers = 0;
        }

        for (final Map.Entry<EntityPlayerMP, Watcher> entry : this.watchers.entrySet()) {
            final EntityPlayerMP player = entry.getKey();
            final Watcher watcher = entry.getValue();

            if (!canSend(player)) {
                continue;
            }

            final IGridNode node = resolve(watcher);
            if (node == null) {
                this.complain(player, watcher, "chat.appliedenergistics2.VisualiserGone");
                continue;
            }

            final IGrid grid = node.getGrid();
            if (grid == null) {
                this.complain(player, watcher, "chat.appliedenergistics2.VisualiserGone");
                continue;
            }

            if (!isAllowed(grid, player)) {
                this.complain(player, watcher, "chat.appliedenergistics2.VisualiserDenied");
                continue;
            }

            watcher.complaint = null;

            final Snapshot snapshot = this.snapshotFor(grid, node, watcher);
            snapshot.watchers++;

            if (watcher.sentVersion != snapshot.version) {
                watcher.sentVersion = snapshot.version;
                NetworkHandler.instance().sendTo(new PacketNetworkVisualiser(snapshot.data), player);

                if (snapshot.truncated) {
                    player.sendStatusMessage(new TextComponentTranslation(
                            "chat.appliedenergistics2.VisualiserTruncated", snapshot.nodes), false);
                }
            }
        }
    }

    private Snapshot snapshotFor(final IGrid grid, final IGridNode start, final Watcher watcher) {
        // Keyed by where the walk begins as well as by the network: with the cap in play, two players bound to
        // opposite ends of one huge network are owed two different pictures.
        final SnapshotKey key = new SnapshotKey(grid, watcher.pos);
        final Snapshot existing = this.snapshots.get(key);

        final int interval = Math.max(1, AEConfig.instance().getVisualiserUpdateInterval());
        if (existing != null && this.tick - existing.builtAt < interval) {
            return existing;
        }

        final VisualiserGraph graph = VisualiserGraphBuilder.build(start, watcher.world,
                AEConfig.instance().getVisualiserMaxNodes());
        final byte[] data = graph.encode();

        if (existing != null) {
            existing.builtAt = this.tick;

            // A network that did not change costs nothing further: the same bytes keep the same version,
            // and nobody is sent a packet.
            if (!Arrays.equals(existing.data, data)) {
                existing.data = data;
                existing.version++;
                existing.nodes = graph.nodeCount();
                existing.truncated = graph.truncated;
            }

            return existing;
        }

        final Snapshot snapshot = new Snapshot(data, this.tick, graph.nodeCount(), graph.truncated);
        this.snapshots.put(key, snapshot);
        return snapshot;
    }

    private void complain(final EntityPlayerMP player, final Watcher watcher, final String message) {
        if (watcher.sentVersion != -1) {
            watcher.sentVersion = -1;
            NetworkHandler.instance().sendTo(PacketNetworkVisualiser.clear(), player);
        }

        if (!message.equals(watcher.complaint)) {
            watcher.complaint = message;
            player.sendStatusMessage(new TextComponentTranslation(message), true);
        }
    }

    /** A player who has logged out is still in the map for one tick, and has nothing left to send to. */
    private static boolean canSend(final EntityPlayerMP player) {
        return !player.isDead && player.connection != null && player.connection.netManager.isChannelOpen();
    }

    private static boolean isAllowed(final IGrid grid, final EntityPlayerMP player) {
        final ISecurityGrid security = grid.getCache(ISecurityGrid.class);

        return security == null || security.hasPermission(player, SecurityPermissions.BUILD);
    }

    @Nullable
    private static IGridNode resolve(final Watcher watcher) {
        if (watcher.world == null || !watcher.world.isBlockLoaded(watcher.pos)) {
            return null;
        }

        final TileEntity te = watcher.world.getTileEntity(watcher.pos);
        if (!(te instanceof IGridHost)) {
            return null;
        }

        final IGridNode node = ((IGridHost) te).getGridNode(watcher.side);

        return node != null ? node : ((IGridHost) te).getGridNode(AEPartLocation.INTERNAL);
    }

    private static final class Watcher {

        private World world;
        private BlockPos pos;
        private AEPartLocation side = AEPartLocation.INTERNAL;

        private long lastSeen;
        private int sentVersion = -1;

        /** The last thing said to this player, so that a lost network is reported once and not every tick. */
        private String complaint;

    }

    private static final class Snapshot {

        private byte[] data;
        private long builtAt;
        private int version;
        private int nodes;
        private boolean truncated;

        /** How many people wanted it this tick; none for a whole tick and it is thrown away. */
        private int watchers;

        private Snapshot(final byte[] data, final long builtAt, final int nodes, final boolean truncated) {
            this.data = data;
            this.builtAt = builtAt;
            this.nodes = nodes;
            this.truncated = truncated;
        }
    }

    private static final class SnapshotKey {

        private final IGrid grid;
        private final BlockPos start;

        private SnapshotKey(final IGrid grid, final BlockPos start) {
            this.grid = grid;
            this.start = start;
        }

        @Override
        public boolean equals(final Object obj) {
            if (!(obj instanceof SnapshotKey)) {
                return false;
            }

            final SnapshotKey other = (SnapshotKey) obj;
            return this.grid == other.grid && this.start.equals(other.start);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this.grid) * 31 + this.start.hashCode();
        }
    }
}
