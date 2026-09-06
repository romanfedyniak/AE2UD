/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.integration.modules.jei;


import appeng.api.AEApi;
import appeng.api.config.TunnelType;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeRegistryPlugin;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;


/**
 * Answers what attunes a P2P tunnel, and does the work only when asked.
 * <p>
 * The registry answers by three rules in turn - an exact item, a capability the item carries, a mod it came
 * from - so the only way to list what attunes a kind is to offer it every item in the game. That is a
 * capability probe per item, which is not something to spend at load; a registry plugin is asked on demand,
 * the way the facade one already is, so a player who never opens the category never pays for it.
 */
class AttunementRegistryPlugin implements IRecipeRegistryPlugin {

    private final IIngredientRegistry ingredients;
    private final AttunementCategory category;

    private List<InWorldRecipe> recipes;

    AttunementRegistryPlugin(final IIngredientRegistry ingredients, final AttunementCategory category) {
        this.ingredients = ingredients;
        this.category = category;
    }

    private List<InWorldRecipe> recipes() {
        if (this.recipes == null) {
            this.recipes = this.build();
        }
        return this.recipes;
    }

    private List<InWorldRecipe> build() {
        final Map<TunnelType, List<ItemStack>> byType = new EnumMap<>(TunnelType.class);

        for (final ItemStack stack : this.ingredients.getAllIngredients(ItemStack.class)) {
            final TunnelType type = AEApi.instance().registries().p2pTunnel().getTunnelTypeByItem(stack);

            if (type != null && !type.getPartItemStack().isEmpty()) {
                byType.computeIfAbsent(type, ignored -> new ArrayList<>()).add(stack);
            }
        }

        final List<InWorldRecipe> built = new ArrayList<>(byType.size());
        for (final Map.Entry<TunnelType, List<ItemStack>> entry : byType.entrySet()) {
            built.add(this.category.recipe(entry.getValue(), entry.getKey().getPartItemStack()));
        }
        return built;
    }

    @Override
    public <V> List<String> getRecipeCategoryUids(final IFocus<V> focus) {
        return this.matches(focus) ? Collections.singletonList(AttunementCategory.UID) : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends IRecipeWrapper, V> List<T> getRecipeWrappers(final IRecipeCategory<T> category,
            final IFocus<V> focus) {
        if (!AttunementCategory.UID.equals(category.getUid()) || !this.matches(focus)) {
            return Collections.emptyList();
        }
        return (List<T>) this.recipes();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends IRecipeWrapper> List<T> getRecipeWrappers(final IRecipeCategory<T> category) {
        if (!AttunementCategory.UID.equals(category.getUid())) {
            return Collections.emptyList();
        }
        return (List<T>) this.recipes();
    }

    /** Whether the thing being looked up is either something that attunes a tunnel, or a tunnel itself. */
    private boolean matches(final IFocus<?> focus) {
        if (!(focus.getValue() instanceof ItemStack)) {
            return false;
        }

        final ItemStack stack = (ItemStack) focus.getValue();

        if (focus.getMode() == IFocus.Mode.INPUT) {
            return AEApi.instance().registries().p2pTunnel().getTunnelTypeByItem(stack) != null;
        }

        for (final TunnelType type : TunnelType.values()) {
            if (ItemStack.areItemsEqual(type.getPartItemStack(), stack)) {
                return true;
            }
        }
        return false;
    }
}
