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


import appeng.core.AppEng;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import org.jetbrains.annotations.NotNull;

import java.util.Random;


/**
 * Lets an entry be rolled only so many times per container, however many times its pool is rolled.
 */
public class CheckTally implements LootCondition {

    private final String name;
    private final int contextId;
    private final int max;

    public CheckTally(final String name, final int contextId, final int max) {
        this.name = name;
        this.contextId = contextId;
        this.max = max;
    }

    @Override
    public boolean testCondition(@NotNull final Random rand, @NotNull final LootContext context) {
        final ILootTallyer tallyer = TallyingLootContext.conditionHasContext(context, Serializer.CHECK_TALLY_NAME);
        return tallyer == null || tallyer.canRoll(this.max, this.name, this.contextId);
    }

    public static class Serializer extends LootCondition.Serializer<CheckTally> {

        private static final String CHECK_TALLY_NAME = "check_tally";
        private static final String ITEM_NAME_KEY = "id";
        private static final String MAX_KEY = "max";
        private static final String CONTEXT_ID_KEY = "context_id";

        public Serializer() {
            super(new ResourceLocation(AppEng.MOD_ID, CHECK_TALLY_NAME), CheckTally.class);
        }

        @Override
        public void serialize(@NotNull final JsonObject object, @NotNull final CheckTally condition,
                @NotNull final JsonSerializationContext serializationContext) {
            object.addProperty(ITEM_NAME_KEY, condition.name);
            object.addProperty(CONTEXT_ID_KEY, condition.contextId);
            object.addProperty(MAX_KEY, condition.max);
        }

        @NotNull
        @Override
        public CheckTally deserialize(@NotNull final JsonObject object,
                @NotNull final JsonDeserializationContext deserializationContext) {
            return new CheckTally(
                    JsonUtils.getString(object, ITEM_NAME_KEY, ""),
                    JsonUtils.getInt(object, CONTEXT_ID_KEY, 0),
                    JsonUtils.getInt(object, MAX_KEY));
        }
    }
}
