/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.loot;


import appeng.core.AEConfig;
import appeng.core.AppEng;
import appeng.core.features.AEFeature;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Random;


/**
 * Drops a pool or an entry when the features it needs are switched off in the config. The loot-table
 * counterpart of {@link appeng.recipes.factories.conditions.Features}.
 */
public class FeatureEnabled implements LootCondition {

    private final Collection<AEFeature> features;

    public FeatureEnabled(final Collection<String> featureNames) {
        this.features = new ArrayList<>(featureNames.size());

        for (final String name : featureNames) {
            this.features.add(AEFeature.valueOf(name.toUpperCase(Locale.ENGLISH)));
        }
    }

    @Override
    public boolean testCondition(@NotNull final Random rand, @NotNull final LootContext context) {
        for (final AEFeature feature : this.features) {
            if (!AEConfig.instance().isFeatureEnabled(feature)) {
                return false;
            }
        }

        return true;
    }

    public static class Serializer extends LootCondition.Serializer<FeatureEnabled> {

        private static final String FEATURE_ENABLED_NAME = "feature_enabled";
        private static final String FEATURES_KEY = "features";

        public Serializer() {
            super(new ResourceLocation(AppEng.MOD_ID, FEATURE_ENABLED_NAME), FeatureEnabled.class);
        }

        @Override
        public void serialize(@NotNull final JsonObject json, @NotNull final FeatureEnabled value,
                @NotNull final JsonSerializationContext context) {
            final JsonArray featureNames = new JsonArray();

            for (final AEFeature feature : value.features) {
                featureNames.add(feature.name());
            }

            json.add(FEATURES_KEY, featureNames);
        }

        @NotNull
        @Override
        public FeatureEnabled deserialize(@NotNull final JsonObject json,
                @NotNull final JsonDeserializationContext context) {
            final ArrayList<String> featureNames = new ArrayList<>();

            // One feature may be written as a bare string rather than a list of one.
            if (JsonUtils.isJsonArray(json, FEATURES_KEY)) {
                for (final JsonElement element : JsonUtils.getJsonArray(json, FEATURES_KEY)) {
                    featureNames.add(element.getAsString());
                }
            } else {
                featureNames.add(JsonUtils.getString(json, FEATURES_KEY));
            }

            return new FeatureEnabled(featureNames);
        }
    }
}
