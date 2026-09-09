/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.client.render.visualiser;


import appeng.me.visualiser.VisualiserGraph;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;


/**
 * The last picture the server sent. Kept apart from the renderer so that arriving packets never touch
 * anything the frame is in the middle of reading.
 */
@SideOnly(Side.CLIENT)
public final class NetworkVisualiserData {

    private static VisualiserGraph graph;

    /** Counts upwards on every change, which is how the renderer knows its buffer is stale. */
    private static int version;

    private NetworkVisualiserData() {
    }

    public static void accept(final byte[] payload) {
        graph = payload.length == 0 ? null : VisualiserGraph.decode(payload);
        version++;
    }

    @Nullable
    public static VisualiserGraph graph() {
        return graph;
    }

    public static int version() {
        return version;
    }
}
