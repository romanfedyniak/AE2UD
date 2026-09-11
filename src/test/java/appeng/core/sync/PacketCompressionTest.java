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


import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public final class PacketCompressionTest {

    @Test
    public void whatGoesInComesOutAndTheBufferIsLeftAlone() throws IOException {
        final byte[] raw = new byte[100_000];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (i % 7);
        }
        final ByteBuf body = Unpooled.wrappedBuffer(raw);

        final byte[] compressed = PacketCompression.compress(body);

        assertEquals(raw.length, body.readableBytes());
        assertArrayEquals(raw, PacketCompression.decompress(compressed, raw.length));
    }

    @Test
    public void expandingPastTheLimitIsRefused() throws IOException {
        final byte[] compressed = PacketCompression.compress(Unpooled.wrappedBuffer(new byte[100_000]));

        assertThrows(IOException.class, () -> PacketCompression.decompress(compressed, 99_999));
    }
}
