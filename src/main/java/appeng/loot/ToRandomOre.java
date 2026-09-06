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
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraftforge.oredict.OreDictionary;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;


/**
 * Turns the entry into one of whatever the ore dictionary has under the given names, which is the only way
 * to name loot that depends on which mods are installed. The item the entry names is a placeholder.
 */
public class ToRandomOre extends LootFunction {

    private final Collection<String> oreDictNames;

    protected ToRandomOre(final LootCondition[] conditionsIn, final Collection<String> oreDictNames) {
        super(conditionsIn);
        this.oreDictNames = oreDictNames;
    }

    @NotNull
    @Override
    public ItemStack apply(@NotNull final ItemStack placeholder, @NotNull final Random rand,
            @NotNull final LootContext context) {
        final List<ItemStack> candidates = new ArrayList<>();

        for (final String name : this.oreDictNames) {
            candidates.addAll(OreDictionary.getOres(name));
        }

        // None of these names is registered in this pack.
        if (candidates.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // A copy: the list belongs to the ore dictionary, and the functions after this one resize the stack.
        return candidates.get(rand.nextInt(candidates.size())).copy();
    }

    public static class Serializer extends LootFunction.Serializer<ToRandomOre> {

        private static final String RANDOM_ORE_FUNCTION_NAME = "to_random_ore";
        private static final String ORES = "ores";

        public Serializer() {
            super(new ResourceLocation(AppEng.MOD_ID, RANDOM_ORE_FUNCTION_NAME), ToRandomOre.class);
        }

        @Override
        public void serialize(@NotNull final JsonObject object, @NotNull final ToRandomOre function,
                @NotNull final JsonSerializationContext serializationContext) {
            final JsonArray oreDictNames = new JsonArray();

            for (final String s : function.oreDictNames) {
                oreDictNames.add(s);
            }

            object.add(ORES, oreDictNames);
        }

        @NotNull
        @Override
        public ToRandomOre deserialize(@NotNull final JsonObject object,
                @NotNull final JsonDeserializationContext deserializationContext,
                @NotNull final LootCondition[] conditionsIn) {
            final ArrayList<String> oreDictNames = new ArrayList<>();

            for (final JsonElement element : JsonUtils.getJsonArray(object, ORES)) {
                oreDictNames.add(element.getAsString());
            }

            return new ToRandomOre(conditionsIn, oreDictNames);
        }
    }
}
