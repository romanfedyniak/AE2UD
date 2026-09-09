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


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;


/**
 * What the visualiser draws: one node per block position, and the connections between them.
 * <p>
 * Both sides share this class so that the shape of the packet is written down once. It is deliberately
 * flat arrays rather than objects: a large network is tens of thousands of nodes, and the client walks
 * this every time it rebuilds its buffer.
 */
public final class VisualiserGraph {

    /** Something at this position is asking for a channel it did not get. */
    public static final byte FLAG_MISSING_CHANNEL = 1;

    /** A link from here leaves the world, so only a stub of it can be drawn. */
    public static final byte FLAG_LEAVES_WORLD = 2;

    /** Stands in for the far end of a link that leaves the world. */
    public static final int NO_NODE = -1;

    /** A node imposing no limit of its own, which is what {@code -1} means throughout the grid. */
    public static final int UNLIMITED = -1;

    private static final ResourceLocation[] NO_STYLES = new ResourceLocation[0];

    /** Nothing claimed this node or link, so it is drawn the ordinary way. */
    private static final int NO_STYLE = -1;

    public final long[] positions;
    public final byte[] nodeFlags;

    public final int[] linkA;
    public final int[] linkB;
    public final int[] linkUsed;
    public final int[] linkCapacity;
    public final short[] linkFrequency;

    /** Whether the walk stopped at the cap, so what is drawn is only part of the network. */
    public final boolean truncated;

    /**
     * Styles claimed by addons, and an index into them per node and per link. The arrays are null on the
     * ordinary network where no addon said anything, and then nothing about styles is written at all.
     */
    private final ResourceLocation[] styles;
    private final int[] nodeStyle;
    private final int[] linkStyle;

    public VisualiserGraph(final long[] positions, final byte[] nodeFlags, final int[] linkA, final int[] linkB,
            final int[] linkUsed, final int[] linkCapacity, final short[] linkFrequency, final boolean truncated) {
        this(positions, nodeFlags, linkA, linkB, linkUsed, linkCapacity, linkFrequency, truncated, NO_STYLES, null,
                null);
    }

    public VisualiserGraph(final long[] positions, final byte[] nodeFlags, final int[] linkA, final int[] linkB,
            final int[] linkUsed, final int[] linkCapacity, final short[] linkFrequency, final boolean truncated,
            final ResourceLocation[] styles, @Nullable final int[] nodeStyle, @Nullable final int[] linkStyle) {
        this.styles = styles;
        this.nodeStyle = nodeStyle;
        this.linkStyle = linkStyle;
        this.positions = positions;
        this.nodeFlags = nodeFlags;
        this.linkA = linkA;
        this.linkB = linkB;
        this.linkUsed = linkUsed;
        this.linkCapacity = linkCapacity;
        this.linkFrequency = linkFrequency;
        this.truncated = truncated;
    }

    public int nodeCount() {
        return this.positions.length;
    }

    public int linkCount() {
        return this.linkA.length;
    }

    @Nullable
    public ResourceLocation styleOfNode(final int node) {
        return this.nodeStyle == null || this.nodeStyle[node] == NO_STYLE ? null : this.styles[this.nodeStyle[node]];
    }

    @Nullable
    public ResourceLocation styleOfLink(final int link) {
        return this.linkStyle == null || this.linkStyle[link] == NO_STYLE ? null : this.styles[this.linkStyle[link]];
    }

    public byte[] encode() {
        final PacketBuffer out = new PacketBuffer(Unpooled.buffer());

        out.writeBoolean(this.truncated);

        out.writeVarInt(this.styles.length);
        for (final ResourceLocation style : this.styles) {
            out.writeString(style.toString());
        }

        final boolean styled = this.styles.length > 0;

        out.writeVarInt(this.positions.length);
        for (int i = 0; i < this.positions.length; i++) {
            out.writeLong(this.positions[i]);
            out.writeByte(this.nodeFlags[i]);

            if (styled) {
                out.writeVarInt(this.nodeStyle[i] + 1);
            }
        }

        out.writeVarInt(this.linkA.length);
        for (int i = 0; i < this.linkA.length; i++) {
            out.writeVarInt(this.linkA[i]);

            // The far end as a distance from the near one, because the walk numbers neighbours next to each
            // other and a small number is one byte. Zero is kept for a link with no far end in this world.
            out.writeVarInt(this.linkB[i] == NO_NODE ? 0 : zigzag(this.linkB[i] - this.linkA[i]) + 1);

            out.writeVarInt(this.linkUsed[i]);
            out.writeVarInt(this.linkCapacity[i] + 1);
            out.writeVarInt(this.linkFrequency[i] & 0xFFFF);

            if (styled) {
                out.writeVarInt(this.linkStyle[i] + 1);
            }
        }

        final byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        return bytes;
    }

    /** Folds the sign into the low bit, so that a step backwards is as cheap to write as a step forwards. */
    private static int zigzag(final int value) {
        return (value << 1) ^ (value >> 31);
    }

    private static int unzigzag(final int value) {
        return (value >>> 1) ^ -(value & 1);
    }

    public static VisualiserGraph decode(final byte[] bytes) {
        final ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        final PacketBuffer in = new PacketBuffer(buf);

        final boolean truncated = in.readBoolean();

        final ResourceLocation[] styles = new ResourceLocation[in.readVarInt()];
        for (int i = 0; i < styles.length; i++) {
            styles[i] = new ResourceLocation(in.readString(256));
        }

        final boolean styled = styles.length > 0;

        final int nodes = in.readVarInt();
        final long[] positions = new long[nodes];
        final byte[] nodeFlags = new byte[nodes];
        final int[] nodeStyle = styled ? new int[nodes] : null;
        for (int i = 0; i < nodes; i++) {
            positions[i] = in.readLong();
            nodeFlags[i] = in.readByte();

            if (styled) {
                nodeStyle[i] = in.readVarInt() - 1;
            }
        }

        final int links = in.readVarInt();
        final int[] linkA = new int[links];
        final int[] linkB = new int[links];
        final int[] linkUsed = new int[links];
        final int[] linkCapacity = new int[links];
        final short[] linkFrequency = new short[links];
        final int[] linkStyle = styled ? new int[links] : null;
        for (int i = 0; i < links; i++) {
            linkA[i] = in.readVarInt();

            final int far = in.readVarInt();
            linkB[i] = far == 0 ? NO_NODE : linkA[i] + unzigzag(far - 1);

            linkUsed[i] = in.readVarInt();
            linkCapacity[i] = in.readVarInt() - 1;
            linkFrequency[i] = (short) in.readVarInt();

            if (styled) {
                linkStyle[i] = in.readVarInt() - 1;
            }
        }

        return new VisualiserGraph(positions, nodeFlags, linkA, linkB, linkUsed, linkCapacity, linkFrequency,
                truncated, styles, nodeStyle, linkStyle);
    }
}
