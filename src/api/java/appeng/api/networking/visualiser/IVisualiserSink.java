/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.networking.visualiser;


import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;


/**
 * What a {@link INetworkVisualiserProvider} draws with.
 * <p>
 * Everything added here hangs off the node the provider was asked about, and is added to the same picture the
 * network's own nodes and links go into - so it is subject to the same cap, and a provider that adds more
 * than there is room for simply has the rest left out.
 */
public interface IVisualiserSink {

    /**
     * A block that belongs in the picture although the grid has no node there.
     *
     * @param style what the client should draw it as, or null for the ordinary node style. A style the
     *              client has never heard of - which is what an addon installed only on the server looks
     *              like - is drawn plainly rather than refused.
     */
    void node(BlockPos pos, @Nullable ResourceLocation style);

    /**
     * A link from the node being asked about to another block, whether or not the grid knows of any
     * connection between them.
     *
     * @param used     channels in use along it, or 0. Only used to write a number beside it.
     * @param capacity what it can carry, or -1 for no limit. Decides how thick it is drawn.
     */
    void link(BlockPos to, int used, int capacity, @Nullable ResourceLocation style);
}
