/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.client;


import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;


/**
 * What an {@link AEKeyRenderHandler} is given to build a model with. Everything here is cached for as long as
 * the texture atlas it came from lives, so a handler may call it on every frame.
 */
@SideOnly(Side.CLIENT)
public interface AEKeyModelContext {

    VertexFormat getFormat();

    /** The stitched sprite for a texture, or null if it was never stitched. */
    @Nullable
    TextureAtlasSprite getSprite(ResourceLocation texture);

    /** A flat model of one sprite, in the shape an item of that texture would have. Never null. */
    IBakedModel getSpriteModel(ResourceLocation texture);

    /** The model an ordinary item stack is drawn with. */
    IBakedModel getItemModel(ItemStack stack);
}
