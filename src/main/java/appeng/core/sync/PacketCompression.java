/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.sync;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.netty.buffer.ByteBuf;


/**
 * GZIP for a packet body that is built whole before it is sent. Decompressing stops at a limit, so a packet
 * cannot make the side reading it allocate whatever it claims to expand to.
 */
public final class PacketCompression {

    private PacketCompression() {
    }

    /** The readable bytes of {@code body}, compressed. The buffer itself is left as it was. */
    public static byte[] compress(final ByteBuf body) throws IOException {
        final byte[] raw = new byte[body.readableBytes()];
        body.getBytes(body.readerIndex(), raw);

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(raw.length / 2 + 32);
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(raw);
        }
        return bytes.toByteArray();
    }

    /**
     * @throws IOException if the data is not GZIP, or expands past {@code maxBytes}
     */
    public static byte[] decompress(final byte[] compressed, final int maxBytes) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(compressed.length * 4);
        final byte[] chunk = new byte[8192];

        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            int read;
            while ((read = in.read(chunk)) > 0) {
                if (bytes.size() + read > maxBytes) {
                    throw new IOException("A compressed packet expands past " + maxBytes + " bytes");
                }
                bytes.write(chunk, 0, read);
            }
        }

        return bytes.toByteArray();
    }
}
