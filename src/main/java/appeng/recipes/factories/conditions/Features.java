/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.recipes.factories.conditions;


import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.JsonUtils;
import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.common.crafting.JsonContext;

import java.util.Locale;
import java.util.function.BooleanSupplier;


public class Features implements IConditionFactory {
    private static final String JSON_FEATURES_KEY = "features";

    @Override
    public BooleanSupplier parse(JsonContext jsonContext, JsonObject jsonObject) {
        final boolean result;

        if (JsonUtils.isJsonArray(jsonObject, JSON_FEATURES_KEY)) {
            final JsonArray features = JsonUtils.getJsonArray(jsonObject, JSON_FEATURES_KEY);

            boolean all = true;
            for (final JsonElement feature : features) {
                all &= isEnabled(JsonUtils.getString(feature, JSON_FEATURES_KEY));
            }
            result = all;
        } else if (JsonUtils.isString(jsonObject, JSON_FEATURES_KEY)) {
            result = isEnabled(JsonUtils.getString(jsonObject, JSON_FEATURES_KEY));
        } else {
            result = false;
        }

        return () -> result;
    }

    /** A feature this version does not have is off: the recipe is skipped instead of failing the whole load. */
    private static boolean isEnabled(final String name) {
        final AEFeature feature;
        try {
            feature = AEFeature.valueOf(name.toUpperCase(Locale.ENGLISH));
        } catch (final IllegalArgumentException e) {
            return false;
        }
        return AEConfig.instance().isFeatureEnabled(feature);
    }
}