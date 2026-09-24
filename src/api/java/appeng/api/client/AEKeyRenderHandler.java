/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.client;


import appeng.api.stacks.AEKey;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;


/**
 * How a key type draws itself wherever a key stands in for an item - a terminal slot, a filter, a tooltip,
 * the crafting tree, an item frame. Register one with {@link AEKeyRendering}; a type without one is drawn as
 * the model of its {@link appeng.api.stacks.AEKeyType#getButtonIcon()}, which is the fallback every type has.
 */
@SideOnly(Side.CLIENT)
public interface AEKeyRenderHandler {

    /**
     * The model to draw the key with, or null to fall back to the button icon. Called on the render thread
     * for every key drawn, so anything expensive belongs behind {@link AEKeyModelContext}'s caches.
     */
    @Nullable
    IBakedModel getModel(AEKey what, AEKeyModelContext context);

    /**
     * ARGB multiplier laid over the model, for a greyscale texture that carries its colour separately - the
     * way a fluid's still texture does. White leaves the model as it is.
     */
    default int getTint(AEKey what) {
        return 0xFFFFFFFF;
    }

    /**
     * Whether {@link #draw(AEKey)} draws this key instead of {@link #getModel}, for a picture that is not atlas
     * quads and a tint: a texture that was never stitched, blending the item renderer does not do, anything
     * that changes per frame.
     */
    default boolean drawsItself(AEKey what) {
        return false;
    }

    /**
     * Draws the key into the unit cube from 0 to 1 on each axis, facing +Z, with the transforms of wherever it
     * is shown already applied. Called on the render thread for every key drawn. Whatever texture this binds,
     * the block atlas is bound again and the colour reset to white afterwards.
     */
    default void draw(AEKey what) {
    }
}
