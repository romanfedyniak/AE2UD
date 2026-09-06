/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.hooks;


import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCompassRequest;
import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import javax.annotation.Nullable;


/**
 * What the client last heard about where the nearest meteorite is, kept per chunk.
 * <p>
 * A compass has to point somewhere every frame, but the answer lives on the server; so an answer is asked for
 * once and then reused, and while waiting for the first one the compass borrows the nearest answer it already
 * has rather than spinning.
 */
public class CompassManager {

    public static final CompassManager INSTANCE = new CompassManager();

    private static final int REFRESH_CACHE_AFTER = 30000;
    private static final int EXPIRE_CACHE_AFTER = 60000;

    private final Long2ObjectOpenHashMap<CachedResult> requests = new Long2ObjectOpenHashMap<>();

    public void postResult(final ChunkPos requestedPos, @Nullable final BlockPos closestMeteorite) {
        this.requests.put(ChunkPos.asLong(requestedPos.x, requestedPos.z),
                new CachedResult(closestMeteorite, System.currentTimeMillis()));
    }

    public void invalidate(final ChunkPos key) {
        this.requests.remove(ChunkPos.asLong(key.x, key.z));
    }

    /** Something the server knows has changed; every answer here may name it. */
    public void invalidateAll() {
        this.requests.clear();
    }

    @Nullable
    public BlockPos getClosestMeteorite(final BlockPos pos, final boolean prefetch) {
        return this.getClosestMeteorite(new ChunkPos(pos), prefetch);
    }

    @Nullable
    public BlockPos getClosestMeteorite(final ChunkPos chunkPos, final boolean prefetch) {
        final long now = System.currentTimeMillis();

        this.expire(now);

        final long requestKey = ChunkPos.asLong(chunkPos.x, chunkPos.z);
        final CachedResult cached = this.requests.get(requestKey);

        BlockPos result = null;
        boolean request = true;

        if (cached != null) {
            result = cached.closestMeteoritePos();
            request = now - cached.received() > REFRESH_CACHE_AFTER;
        }

        if (result == null) {
            result = this.findClosestKnownResult(chunkPos);
        }

        if (request) {
            this.requests.put(requestKey, new CachedResult(result, now));
            NetworkHandler.instance().sendToServer(new PacketCompassRequest(chunkPos));
        }

        // Ask about the chunks around this one too, so moving does not wait for a round trip each time.
        if (prefetch) {
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    if (i != 0 || j != 0) {
                        this.getClosestMeteorite(new ChunkPos(chunkPos.x + i, chunkPos.z + j), false);
                    }
                }
            }
        }

        return result;
    }

    private void expire(final long now) {
        final ObjectIterator<Long2ObjectMap.Entry<CachedResult>> it = this.entries();

        while (it.hasNext()) {
            if (now - it.next().getValue().received() > EXPIRE_CACHE_AFTER) {
                it.remove();
            }
        }
    }

    /** The fast iterator hands back the same entry each time, which is all these two loops ever need. */
    private ObjectIterator<Long2ObjectMap.Entry<CachedResult>> entries() {
        return this.requests.long2ObjectEntrySet().fastIterator();
    }

    /** Somewhere roughly right beats spinning while the real answer is on its way. */
    @Nullable
    private BlockPos findClosestKnownResult(final ChunkPos chunkPos) {
        final ObjectIterator<Long2ObjectMap.Entry<CachedResult>> it = this.entries();
        long closestDistance = Long.MAX_VALUE;
        BlockPos result = null;

        while (it.hasNext()) {
            final Long2ObjectMap.Entry<CachedResult> entry = it.next();
            final BlockPos closestPos = entry.getValue().closestMeteoritePos();

            if (closestPos != null) {
                final long distance = distanceSquared(chunkPos, entry.getLongKey());

                if (distance < closestDistance) {
                    closestDistance = distance;
                    result = closestPos;
                }
            }
        }

        return result;
    }

    private static long distanceSquared(final ChunkPos pos, final long packed) {
        final long dx = (int) (packed & 0xFFFFFFFFL) - pos.x;
        final long dz = (int) (packed >>> 32 & 0xFFFFFFFFL) - pos.z;

        return dx * dx + dz * dz;
    }

    @Desugar
    private record CachedResult(@Nullable BlockPos closestMeteoritePos, long received) {
    }
}
