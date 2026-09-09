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


import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.visualiser.INetworkVisualiserProvider;
import appeng.api.networking.visualiser.IVisualiserSink;
import appeng.api.networking.visualiser.NetworkVisualisers;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridNode;
import appeng.parts.p2p.PartP2PTunnel;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;


/**
 * Walks a network outwards from one node and writes down what can be drawn.
 * <p>
 * The walk follows connections rather than reading {@link appeng.api.networking.IGrid#getNodes()} so that
 * stopping at the cap leaves a connected picture around the block the player bound, instead of whichever
 * nodes a hash map happened to hand over first.
 */
public final class VisualiserGraphBuilder {

    /** No block position encodes to this, because {@link BlockPos#toLong()} never sets the top bit. */
    private static final long NOWHERE = Long.MIN_VALUE;

    private static final int NO_STYLE = -1;

    private final World world;
    private final int maxNodes;

    private final Long2IntOpenHashMap indexByPos = new Long2IntOpenHashMap();
    private final LongArrayList positions = new LongArrayList();
    private final ByteArrayList nodeFlags = new ByteArrayList();
    private final IntArrayList nodeStyle = new IntArrayList();

    private final IntArrayList linkA = new IntArrayList();
    private final IntArrayList linkB = new IntArrayList();
    private final IntArrayList linkUsed = new IntArrayList();
    private final IntArrayList linkCapacity = new IntArrayList();
    private final ShortArrayList linkFrequency = new ShortArrayList();
    private final IntArrayList linkStyle = new IntArrayList();

    private final List<ResourceLocation> styles = new ArrayList<>();
    private final Object2IntOpenHashMap<ResourceLocation> styleIndex = new Object2IntOpenHashMap<>();

    private boolean truncated;

    private VisualiserGraphBuilder(final World world, final int maxNodes) {
        this.world = world;
        this.maxNodes = maxNodes;
        this.indexByPos.defaultReturnValue(VisualiserGraph.NO_NODE);
        this.styleIndex.defaultReturnValue(NO_STYLE);
    }

    public static VisualiserGraph build(final IGridNode start, final World world, final int maxNodes) {
        return new VisualiserGraphBuilder(world, maxNodes).walk(start);
    }

    private VisualiserGraph walk(final IGridNode start) {
        final List<INetworkVisualiserProvider> providers = NetworkVisualisers.providers();

        final Set<IGridNode> seenNodes = Collections.newSetFromMap(new IdentityHashMap<IGridNode, Boolean>());
        final Set<IGridConnection> seenLinks = Collections
                .newSetFromMap(new IdentityHashMap<IGridConnection, Boolean>());
        final Deque<IGridNode> queue = new ArrayDeque<>();

        seenNodes.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            final IGridNode node = queue.poll();

            final long pos = positionOf(node, this.world);
            int index = VisualiserGraph.NO_NODE;

            if (pos != NOWHERE) {
                index = this.indexOf(pos);

                if (index == VisualiserGraph.NO_NODE) {
                    this.truncated = true;
                    continue;
                }

                if (!node.meetsChannelRequirements()) {
                    this.flag(index, VisualiserGraph.FLAG_MISSING_CHANNEL);
                }

                for (final INetworkVisualiserProvider provider : providers) {
                    this.style(this.nodeStyle, index, provider.styleOf(node));
                }
            }

            for (final IGridConnection connection : node.getConnections()) {
                final IGridNode other = connection.getOtherSide(node);

                if (seenNodes.add(other)) {
                    queue.add(other);
                }

                if (index == VisualiserGraph.NO_NODE || !seenLinks.add(connection)) {
                    continue;
                }

                final long otherPos = positionOf(other, this.world);

                if (otherPos == pos) {
                    // Two nodes in one block, which is every cable carrying a part. Nothing to draw.
                    continue;
                }

                if (otherPos == NOWHERE) {
                    if (leavesWorld(other, this.world)) {
                        this.flag(index, VisualiserGraph.FLAG_LEAVES_WORLD);
                        this.addLink(index, VisualiserGraph.NO_NODE, connection.getUsedChannels(),
                                capacityOf(node, other), frequencyOf(node, other), styleOf(providers, connection));
                    }
                    continue;
                }

                final int otherIndex = this.indexByPos.get(otherPos);
                if (otherIndex == VisualiserGraph.NO_NODE) {
                    // Not walked yet, or past the cap. Written down from the far end instead, if it is drawn at all.
                    seenLinks.remove(connection);
                    continue;
                }

                this.addLink(index, otherIndex, connection.getUsedChannels(), capacityOf(node, other),
                        frequencyOf(node, other), styleOf(providers, connection));
            }

            if (index != VisualiserGraph.NO_NODE && !providers.isEmpty()) {
                final IVisualiserSink sink = new Sink(index);

                for (final INetworkVisualiserProvider provider : providers) {
                    provider.contribute(node, sink);
                }
            }
        }

        final boolean styled = !this.styles.isEmpty();

        return new VisualiserGraph(this.positions.toLongArray(), this.nodeFlags.toByteArray(),
                this.linkA.toIntArray(), this.linkB.toIntArray(), this.linkUsed.toIntArray(),
                this.linkCapacity.toIntArray(), this.linkFrequency.toShortArray(), this.truncated,
                this.styles.toArray(new ResourceLocation[0]), styled ? this.nodeStyle.toIntArray() : null,
                styled ? this.linkStyle.toIntArray() : null);
    }

    /** The index of the block at this position, adding it if there is still room. */
    private int indexOf(final long pos) {
        final int existing = this.indexByPos.get(pos);
        if (existing != VisualiserGraph.NO_NODE) {
            return existing;
        }

        if (this.positions.size() >= this.maxNodes) {
            return VisualiserGraph.NO_NODE;
        }

        final int index = this.positions.size();
        this.indexByPos.put(pos, index);
        this.positions.add(pos);
        this.nodeFlags.add((byte) 0);
        this.nodeStyle.add(NO_STYLE);

        return index;
    }

    private void flag(final int index, final byte flag) {
        this.nodeFlags.set(index, (byte) (this.nodeFlags.getByte(index) | flag));
    }

    private void addLink(final int a, final int b, final int used, final int capacity, final short frequency,
            @Nullable final ResourceLocation style) {
        this.linkA.add(a);
        this.linkB.add(b);
        this.linkUsed.add(used);
        this.linkCapacity.add(capacity);
        this.linkFrequency.add(frequency);
        this.linkStyle.add(NO_STYLE);

        this.style(this.linkStyle, this.linkStyle.size() - 1, style);
    }

    /** First claim wins, so that one provider cannot quietly repaint another one's machinery. */
    private void style(final IntArrayList target, final int index, @Nullable final ResourceLocation style) {
        if (style == null || target.getInt(index) != NO_STYLE) {
            return;
        }

        int id = this.styleIndex.getInt(style);
        if (id == NO_STYLE) {
            id = this.styles.size();
            this.styles.add(style);
            this.styleIndex.put(style, id);
        }

        target.set(index, id);
    }

    @Nullable
    private static ResourceLocation styleOf(final List<INetworkVisualiserProvider> providers,
            final IGridConnection connection) {
        for (final INetworkVisualiserProvider provider : providers) {
            final ResourceLocation style = provider.styleOf(connection);
            if (style != null) {
                return style;
            }
        }

        return null;
    }

    /** The narrower of the two sides, the same rule the connection itself uses to refuse a channel. */
    private static int capacityOf(final IGridNode a, final IGridNode b) {
        final int capA = GridNode.maxChannelsOf(a.getGridBlock());
        final int capB = GridNode.maxChannelsOf(b.getGridBlock());

        if (capA < 0) {
            return capB;
        }

        if (capB < 0) {
            return capA;
        }

        return Math.min(capA, capB);
    }

    /** Non-zero only for the virtual link inside a P2P ME tunnel, which is what earns it its own colours. */
    private static short frequencyOf(final IGridNode a, final IGridNode b) {
        if (a.getGridBlock().getMachine() instanceof PartP2PTunnel
                && b.getGridBlock().getMachine() instanceof PartP2PTunnel) {
            final short freqA = ((PartP2PTunnel<?>) a.getGridBlock().getMachine()).getFrequency();
            final short freqB = ((PartP2PTunnel<?>) b.getGridBlock().getMachine()).getFrequency();

            if (freqA != 0 && freqA == freqB) {
                return freqA;
            }
        }

        return 0;
    }

    private static boolean leavesWorld(final IGridNode node, final World world) {
        final IGridBlock block = node.getGridBlock();
        final DimensionalCoord location = block.getLocation();

        return block.isWorldAccessible() && location != null && !location.isInWorld(world);
    }

    /** {@link BlockPos#toLong()} of a node that can be drawn, or {@link #NOWHERE}. */
    private static long positionOf(final IGridNode node, final World world) {
        final IGridBlock block = node.getGridBlock();
        if (!block.isWorldAccessible()) {
            return NOWHERE;
        }

        final DimensionalCoord location = block.getLocation();
        if (location == null || !location.isInWorld(world)) {
            return NOWHERE;
        }

        return new BlockPos(location.x, location.y, location.z).toLong();
    }

    /** What an addon draws with. Everything it adds hangs off the node it was asked about. */
    private final class Sink implements IVisualiserSink {

        private final int from;

        private Sink(final int from) {
            this.from = from;
        }

        @Override
        public void node(final BlockPos pos, @Nullable final ResourceLocation style) {
            final int index = VisualiserGraphBuilder.this.indexOf(pos.toLong());

            if (index == VisualiserGraph.NO_NODE) {
                VisualiserGraphBuilder.this.truncated = true;
                return;
            }

            VisualiserGraphBuilder.this.style(VisualiserGraphBuilder.this.nodeStyle, index, style);
        }

        @Override
        public void link(final BlockPos to, final int used, final int capacity,
                @Nullable final ResourceLocation style) {
            final int index = VisualiserGraphBuilder.this.indexOf(to.toLong());

            if (index == VisualiserGraph.NO_NODE) {
                VisualiserGraphBuilder.this.truncated = true;
                return;
            }

            VisualiserGraphBuilder.this.addLink(this.from, index, used, capacity, (short) 0, style);
        }
    }
}
