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

package appeng.items.misc;


import appeng.bootstrap.IItemRendering;
import appeng.bootstrap.ItemRenderingCustomizer;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;


/**
 * A processing pattern wears the same weave in violet, so a wall of interface slots can be read without
 * hovering over every one of them. The two are one item and differ only in what they encode.
 */
public class ItemEncodedPatternRendering extends ItemRenderingCustomizer {

    private static final ModelResourceLocation MODEL_CRAFTING = new ModelResourceLocation("appliedenergistics2:encoded_pattern");
    private static final ModelResourceLocation MODEL_PROCESSING = new ModelResourceLocation("appliedenergistics2:encoded_pattern_processing");

    @Override
    public void customize(IItemRendering rendering) {
        rendering.variants(MODEL_CRAFTING, MODEL_PROCESSING);
        rendering.meshDefinition(is -> ItemEncodedPattern.isCraftingPattern(is) ? MODEL_CRAFTING : MODEL_PROCESSING);
    }
}
