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
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;
import org.jetbrains.annotations.NotNull;

import java.util.Random;


/**
 * Marks that an entry has been rolled, which is what {@link CheckTally} counts.
 */
public class Tally extends LootFunction {

    private final String name;
    private final int contextId;

    protected Tally(final LootCondition[] conditionsIn, final String name, final int contextId) {
        super(conditionsIn);
        this.name = name;
        this.contextId = contextId;
    }

    @NotNull
    @Override
    public ItemStack apply(@NotNull final ItemStack stack, @NotNull final Random rand,
            @NotNull final LootContext context) {
        final ILootTallyer tallyer = TallyingLootContext.functionHasContext(context, Serializer.TALLY_NAME);

        if (tallyer != null) {
            tallyer.tally(this.name.isEmpty() ? defaultName(stack) : this.name, this.contextId);
        }

        return stack;
    }

    /** Registry name and metadata, so two items of one id are counted apart. */
    private static String defaultName(final ItemStack stack) {
        final ResourceLocation registryName = stack.getItem().getRegistryName();
        return registryName == null ? "" : registryName + ":" + stack.getItemDamage();
    }

    public static class Serializer extends LootFunction.Serializer<Tally> {

        private static final String TALLY_NAME = "tally";
        private static final String ITEM_NAME_KEY = "id";
        private static final String CONTEXT_ID_KEY = "context_id";

        public Serializer() {
            super(new ResourceLocation(AppEng.MOD_ID, TALLY_NAME), Tally.class);
        }

        @Override
        public void serialize(@NotNull final JsonObject object, @NotNull final Tally function,
                @NotNull final JsonSerializationContext serializationContext) {
            object.addProperty(ITEM_NAME_KEY, function.name);
            object.addProperty(CONTEXT_ID_KEY, function.contextId);
        }

        @NotNull
        @Override
        public Tally deserialize(@NotNull final JsonObject object,
                @NotNull final JsonDeserializationContext deserializationContext,
                @NotNull final LootCondition[] conditionsIn) {
            return new Tally(conditionsIn,
                    JsonUtils.getString(object, ITEM_NAME_KEY, ""),
                    JsonUtils.getInt(object, CONTEXT_ID_KEY, 0));
        }
    }
}
