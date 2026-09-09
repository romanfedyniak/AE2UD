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


import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;


/**
 * The wire format, which is the part of the visualiser that can be wrong in a way nobody sees until a link
 * is drawn to the wrong place: node indices and capacities are shifted to keep them out of a varint's
 * negatives, and a stub carries no far end at all.
 */
public class VisualiserGraphTest {

    @Test
    public void aGraphSurvivesTheWire() {
        final VisualiserGraph sent = new VisualiserGraph(
                new long[] {
                        new BlockPos(0, 0, 0).toLong(),
                        new BlockPos(-4096, 255, 30000000).toLong(),
                        new BlockPos(17, 64, -9).toLong()
                },
                new byte[] {0, VisualiserGraph.FLAG_MISSING_CHANNEL, VisualiserGraph.FLAG_LEAVES_WORLD},
                new int[] {0, 1, 2},
                new int[] {1, 2, VisualiserGraph.NO_NODE},
                new int[] {0, 32, 3},
                new int[] {8, 32, VisualiserGraph.UNLIMITED},
                new short[] {0, (short) 0xBEEF, 0},
                true);

        final VisualiserGraph received = VisualiserGraph.decode(sent.encode());

        assertThat(received.nodeCount(), equalTo(3));
        assertThat(received.linkCount(), equalTo(3));
        assertThat(received.truncated, equalTo(true));

        for (int i = 0; i < 3; i++) {
            assertThat(received.positions[i], equalTo(sent.positions[i]));
            assertThat(received.nodeFlags[i], equalTo(sent.nodeFlags[i]));
            assertThat(received.linkA[i], equalTo(sent.linkA[i]));
            assertThat(received.linkB[i], equalTo(sent.linkB[i]));
            assertThat(received.linkUsed[i], equalTo(sent.linkUsed[i]));
            assertThat(received.linkCapacity[i], equalTo(sent.linkCapacity[i]));
            assertThat(received.linkFrequency[i], equalTo(sent.linkFrequency[i]));
        }
    }

    /**
     * A P2P frequency uses the whole range of a short, including the half a signed read would turn negative.
     */
    @Test
    public void everyFrequencyComesBackAsItWent() {
        final int count = 0x10000;
        final short[] frequencies = new short[count];
        for (int i = 0; i < count; i++) {
            frequencies[i] = (short) i;
        }

        final VisualiserGraph received = VisualiserGraph.decode(new VisualiserGraph(new long[] {0}, new byte[] {0},
                new int[count], new int[count], new int[count], new int[count], frequencies, false).encode());

        for (int i = 0; i < count; i++) {
            assertThat(received.linkFrequency[i], equalTo((short) i));
        }
    }

    /**
     * A style claimed by an addon travels as a name, and costs nothing at all on a network where no addon
     * claimed anything.
     */
    @Test
    public void stylesTravelAndCostNothingWhenThereAreNone() {
        final ResourceLocation wireless = new ResourceLocation("aewireless", "link");

        final VisualiserGraph styled = new VisualiserGraph(new long[] {1, 2}, new byte[2], new int[] {0},
                new int[] {1}, new int[] {4}, new int[] {8}, new short[1], false,
                new ResourceLocation[] {wireless}, new int[] {-1, 0}, new int[] {0});

        final VisualiserGraph received = VisualiserGraph.decode(styled.encode());

        assertThat(received.styleOfNode(0), nullValue());
        assertThat(received.styleOfNode(1), equalTo(wireless));
        assertThat(received.styleOfLink(0), equalTo(wireless));

        final VisualiserGraph plain = new VisualiserGraph(new long[] {1, 2}, new byte[2], new int[] {0},
                new int[] {1}, new int[] {4}, new int[] {8}, new short[1], false);

        assertThat(VisualiserGraph.decode(plain.encode()).styleOfNode(0), nullValue());
        assertThat(plain.encode().length, equalTo(styled.encode().length - "aewireless:link".length() - 4));
    }

    /**
     * What the cap is really for: a network of sixteen thousand blocks has to fit in something worth sending
     * to everyone looking at it. Links point at neighbours, the way the walk actually numbers them, since
     * that is what the distance-encoded far end is there to exploit.
     */
    @Test
    public void aFullSizedNetworkFitsInAPacketWorthSending() {
        final int nodes = 16384;
        final Random random = new Random(1);

        final long[] positions = new long[nodes];
        for (int i = 0; i < nodes; i++) {
            positions[i] = new BlockPos(random.nextInt(512) - 256, random.nextInt(255), random.nextInt(512) - 256)
                    .toLong();
        }

        final int links = nodes - 1;
        final int[] a = new int[links];
        final int[] b = new int[links];
        final int[] used = new int[links];
        final int[] capacity = new int[links];
        for (int i = 0; i < links; i++) {
            a[i] = i;
            b[i] = i + 1;
            used[i] = random.nextInt(32);
            capacity[i] = 32;
        }

        final byte[] encoded = new VisualiserGraph(positions, new byte[nodes], a, b, used, capacity,
                new short[links], false).encode();

        System.out.println("a " + nodes + " node network encodes to " + encoded.length + " bytes");

        assertThat(encoded.length, lessThan(256 * 1024));
        assertThat(VisualiserGraph.decode(encoded).nodeCount(), equalTo(nodes));
    }
}
