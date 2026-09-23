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

package appeng.client.render.crafting;


import appeng.api.client.CraftingUnitModels;
import appeng.block.crafting.BlockCraftingUnit;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;


/**
 * Draws the crafting units registered through {@link CraftingUnitModels} as AE2's own are drawn.
 */
@SideOnly(Side.CLIENT)
public final class AddonCraftingUnitModels implements ICustomModelLoader {

    public static final AddonCraftingUnitModels INSTANCE = new AddonCraftingUnitModels();

    private AddonCraftingUnitModels() {
    }

    @SubscribeEvent
    public void onModelRegistry(final ModelRegistryEvent event) {
        for (final Block block : CraftingUnitModels.getAll().keySet()) {
            ModelLoader.setCustomStateMapper(block, AddonCraftingUnitModels::mapState);
        }
    }

    private static Map<IBlockState, ModelResourceLocation> mapState(final Block block) {
        final ModelResourceLocation normal = new ModelResourceLocation(block.getRegistryName(), "normal");
        final ModelResourceLocation formed = new ModelResourceLocation(formedModel(block), "normal");

        final Map<IBlockState, ModelResourceLocation> result = new HashMap<>();
        for (final IBlockState state : block.getBlockState().getValidStates()) {
            result.put(state, state.getValue(BlockCraftingUnit.FORMED) ? formed : normal);
        }
        return result;
    }

    private static ResourceLocation formedModel(final Block block) {
        final ResourceLocation name = block.getRegistryName();
        return new ResourceLocation(name.getNamespace(), "models/block/crafting/" + name.getPath() + "/builtin");
    }

    @Override
    public boolean accepts(final ResourceLocation modelLocation) {
        return this.lightFor(modelLocation) != null;
    }

    @Override
    public IModel loadModel(final ResourceLocation modelLocation) {
        return new CraftingCubeModel(this.lightFor(modelLocation));
    }

    private ResourceLocation lightFor(final ResourceLocation modelLocation) {
        for (final Map.Entry<Block, ResourceLocation> entry : CraftingUnitModels.getAll().entrySet()) {
            final ResourceLocation formed = formedModel(entry.getKey());
            if (formed.getNamespace().equals(modelLocation.getNamespace()) && formed.getPath().equals(modelLocation.getPath())) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public void onResourceManagerReload(final IResourceManager resourceManager) {
    }
}
