/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.networking.visualiser;


import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;


/**
 * Lets an addon have a say in what the network visualiser draws.
 * <p>
 * Anything that makes a real {@link IGridConnection} is drawn without a provider, because the visualiser
 * walks connections - a wireless connector joining two distant nodes already appears as a long link. A
 * provider is for the two things that walk cannot see: giving those links a look of their own, and drawing
 * machinery that is part of the picture but has no node in the grid.
 * <p>
 * Register with {@link NetworkVisualisers#register}. Every method is asked on the server, while the picture
 * is being built, once per node or link walked - so answer quickly, and answer null for what is not yours.
 */
public interface INetworkVisualiserProvider {

    /**
     * @return the style this node should be drawn in, or null to leave it to whoever else has an opinion.
     */
    @Nullable
    default ResourceLocation styleOf(final IGridNode node) {
        return null;
    }

    /**
     * @return the style this connection should be drawn in, or null.
     */
    @Nullable
    default ResourceLocation styleOf(final IGridConnection connection) {
        return null;
    }

    /**
     * Add whatever belongs beside this node and is not in the grid.
     */
    default void contribute(final IGridNode node, final IVisualiserSink sink) {
    }
}
