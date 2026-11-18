/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.sync;


import java.io.ByteArrayInputStream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;


public final class AppEngPacketByteArrayTest {

    private static final AppEngPacket PACKET = new AppEngPacket() {
    };

    private static byte[] readAll(final ByteArrayInputStream in) {
        final byte[] out = new byte[in.available()];
        in.read(out, 0, out.length);
        return out;
    }

    /** A heap buffer that is a slice of a bigger array, as a pooled heap allocator hands out. */
    private static ByteBuf sliced(final byte[] body) {
        final byte[] backing = new byte[body.length + 20];
        for (int i = 0; i < backing.length; i++) {
            backing[i] = (byte) 0x7f;
        }
        System.arraycopy(body, 0, backing, 10, body.length);
        return Unpooled.wrappedBuffer(backing).slice(10, body.length);
    }

    @Test
    public void readsTheBodyOfASlicedHeapBuffer() {
        final byte[] body = {1, 2, 3, 4, 5, 6, 7, 8};
        final ByteBuf buf = sliced(body);
        buf.readInt();

        assertArrayEquals(new byte[] {5, 6, 7, 8},
                readAll(PACKET.getPacketByteArray(buf, buf.readerIndex(), buf.readableBytes())));
    }

    @Test
    public void theWholeBufferKeepsItsLastBytes() {
        final byte[] body = {1, 2, 3, 4, 5, 6, 7, 8};
        final ByteBuf direct = Unpooled.directBuffer(16).writeBytes(body);
        direct.readInt();

        final ByteArrayInputStream in = PACKET.getPacketByteArray(direct);
        in.skip(direct.readerIndex());
        assertArrayEquals(new byte[] {5, 6, 7, 8}, readAll(in));

        final ByteBuf heap = sliced(body);
        heap.readInt();
        final ByteArrayInputStream fromHeap = PACKET.getPacketByteArray(heap);
        fromHeap.skip(heap.readerIndex());
        assertArrayEquals(new byte[] {5, 6, 7, 8}, readAll(fromHeap));
    }
}
