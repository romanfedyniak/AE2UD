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

package appeng.client.render;


import appeng.api.client.AEKeyModelContext;
import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.items.misc.WrappedGenericStack;
import com.google.common.collect.ImmutableList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ItemLayerModel;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;


/**
 * Draws {@link appeng.items.misc.WrappedGenericStack} as whatever it is wrapping, the way
 * the deleted fluid-only placeholder's own model did. Without this the
 * wrapper has no model at all and every non-item key in a vanilla slot renders as a missing texture.
 * <p>
 * The choice of what to draw follows the same rule as everything else about a key type — <b>the type decides,
 * not the renderer</b>. A type says so through an {@link AEKeyRenderHandler} of its own; failing that, an item
 * key draws its own item model, and anything else draws the model of
 * {@link appeng.api.stacks.AEKeyType#getButtonIcon()} — the stand-in the type already had to supply for the
 * terminal's type-switcher button, so a new key type gets a usable placeholder with no client-side code at all.
 */
public class WrappedGenericStackDispatcherBakedModel extends DelegateBakedModel implements AEKeyModelContext {
    private final VertexFormat format;
    private final Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter;

    /**
     * Baking a sprite walks every pixel of every frame of it, so it is done once per texture rather than once
     * per item drawn. Held on the model instance: a resource reload bakes a new one and this goes with it.
     */
    private final Map<ResourceLocation, IBakedModel> spriteModels = new HashMap<>();

    private final ItemOverrideList overrides = new ItemOverrideList(Collections.emptyList()) {
        @Override
        public IBakedModel handleItemState(IBakedModel originalModel, ItemStack stack, World world, EntityLivingBase entity) {
            // Asks the item, not GenericStack's static wrapper: the wrapper is installed during
            // FMLInitializationEvent, which is after model baking, and nothing here should care.
            if (!(stack.getItem() instanceof WrappedGenericStack wrapper)) {
                return originalModel;
            }

            final GenericStack wrapped = wrapper.unwrap(stack);
            if (wrapped == null) {
                return originalModel;
            }

            final AEKey what = wrapped.what();
            final AEKeyRenderHandler handler = AEKeyRendering.get(what);

            if (handler != null) {
                final IBakedModel model = handler.getModel(what, WrappedGenericStackDispatcherBakedModel.this);
                if (model != null) {
                    return model;
                }
            }

            final ItemStack icon = what instanceof AEItemKey itemKey
                    ? itemKey.getReadOnlyStack()
                    : what.getType().getButtonIcon();

            // A key type whose button icon is itself a wrapper would recurse forever. Nothing in this mod
            // does that, but an addon supplies its own getButtonIcon() and this is a render-thread loop.
            if (icon.isEmpty() || icon.getItem() == stack.getItem()) {
                return originalModel;
            }

            return WrappedGenericStackDispatcherBakedModel.this.getItemModel(icon);
        }
    };

    public WrappedGenericStackDispatcherBakedModel(IBakedModel baseModel, VertexFormat format, Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        super(baseModel);
        this.format = format;
        this.bakedTextureGetter = bakedTextureGetter;
    }

    // This is never used. See the item override list above.
    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        return Collections.emptyList();
    }

    @Override
    public boolean isGui3d() {
        return this.getBaseModel().isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @Override
    public ItemOverrideList getOverrides() {
        return this.overrides;
    }

    @Override
    public VertexFormat getFormat() {
        return this.format;
    }

    @Nullable
    @Override
    public TextureAtlasSprite getSprite(final ResourceLocation texture) {
        return this.bakedTextureGetter.apply(texture);
    }

    @Override
    public IBakedModel getSpriteModel(final ResourceLocation texture) {
        IBakedModel model = this.spriteModels.get(texture);

        if (model == null) {
            final TextureAtlasSprite sprite = this.getSprite(texture);
            model = new SpriteBakedModel(sprite == null
                    ? ImmutableList.of()
                    : ItemLayerModel.getQuadsForSprite(0, sprite, this.format, Optional.empty()));
            this.spriteModels.put(texture, model);
        }

        return model;
    }

    @Override
    public IBakedModel getItemModel(final ItemStack stack) {
        return Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getItemModel(stack);
    }
}
