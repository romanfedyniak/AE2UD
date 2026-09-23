/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.integration.modules.jei;


import appeng.container.implementations.ContainerPatternEncoder;
import appeng.api.integrations.hei.ExtraInputProviders;
import appeng.api.integrations.hei.IngredientConverter;
import appeng.api.integrations.hei.IngredientConverters;
import appeng.api.patterns.PatternEncodingMode;
import appeng.api.patterns.PatternEncodingModes;
import appeng.api.patterns.PatternGrid;
import appeng.api.patterns.RecipePlacement;
import appeng.api.patterns.TransferredRecipe;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.helpers.ICraftingGridContainer;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketJEIRecipe;
import appeng.util.Platform;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.transfer.RecipeTransferErrorInternal;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static appeng.helpers.ItemStackHelper.stackFromNBT;
import static appeng.helpers.ItemStackHelper.stackToNBT;


public class RecipeTransferHandler<T extends Container> implements IRecipeTransferHandler<T> {

    private final Class<T> containerClass;

    public RecipeTransferHandler(Class<T> containerClass) {
        this.containerClass = containerClass;
    }

    @Override
    public Class<T> getContainerClass() {
        return this.containerClass;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(@Nonnull T container, IRecipeLayout recipeLayout, @Nonnull EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
        final String recipeType = recipeLayout.getRecipeCategory().getUid();

        if (recipeType.equals(VanillaRecipeCategoryUid.INFORMATION) || recipeType.equals(VanillaRecipeCategoryUid.FUEL)) {
            return RecipeTransferErrorInternal.INSTANCE;
        }

        if (!doTransfer) {
            if (container instanceof ICraftingGridContainer) {
                JEIMissingItem error = new JEIMissingItem(container, recipeLayout);
                if (error.errored())
                    return error;
            }
            return null;
        }

        if (container instanceof ContainerPatternEncoder) {
            this.transferToEncoder(recipeLayout, recipeType);
            return null;
        }

        Map<Integer, ? extends IGuiIngredient<ItemStack>> ingredients = recipeLayout.getItemStacks().getGuiIngredients();

        final NBTTagCompound recipe = new NBTTagCompound();
        final NBTTagList outputs = new NBTTagList();

        int slotIndex = 0;
        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> ingredientEntry : ingredients.entrySet()) {
            IGuiIngredient<ItemStack> ingredient = ingredientEntry.getValue();
            if (!ingredient.isInput()) {
                ItemStack output = ingredient.getDisplayedIngredient();
                if (output != null) {
                    final NBTTagCompound tag = stackToNBT(output);
                    outputs.appendTag(tag);
                }
                continue;
            }

            final List<ItemStack> list = new ArrayList<>();
            final ItemStack displayed = ingredient.getDisplayedIngredient();

            // prefer currently displayed item
            if (displayed != null && !displayed.isEmpty()) {
                list.add(displayed);
            }

            // prefer pure crystals.
            for (ItemStack stack : ingredient.getAllIngredients()) {
                if (stack == null) {
                    continue;
                }
                if (Platform.isRecipePrioritized(stack)) {
                    list.add(0, stack);
                } else {
                    list.add(stack);
                }
            }

            final int gridSlot = this.gridSlotOf(recipeLayout, slotIndex);
            for (final Slot slot : container.inventorySlots) {
                if (slot instanceof SlotCraftingMatrix) {
                    if (slot.getSlotIndex() == gridSlot) {
                        final NBTTagList tags = new NBTTagList();

                        for (final ItemStack is : list) {
                            final NBTTagCompound tag = stackToNBT(is);
                            tags.appendTag(tag);
                        }

                        recipe.setTag("#" + slot.getSlotIndex(), tags);
                        break;
                    }
                }
            }

            slotIndex++;
        }

        recipe.setTag("outputs", promoteFocused(recipeLayout, outputs));

        // Ctrl+Move Items: craft whatever this recipe is missing instead of refusing the transfer.
        // Ctrl+Shift additionally starts that craft right away instead of opening the confirm screen.
        if (GuiScreen.isCtrlKeyDown() && container instanceof ICraftingGridContainer) {
            recipe.setBoolean("craftMissing", true);
            recipe.setBoolean("craftMissingAutoStart", maxTransfer);
        }

        try {
            NetworkHandler.instance().sendToServer(new PacketJEIRecipe(recipe));
        } catch (IOException e) {
            AELog.debug(e);
        }

        return null;
    }

    /**
     * Which square of the crafting grid the recipe's {@code input}th ingredient goes to; a slot no ingredient
     * names is emptied. A grid bigger than the recipe screen's own lays the recipe out here.
     */
    protected int gridSlotOf(final IRecipeLayout recipeLayout, final int input) {
        return input;
    }

    /**
     * Moves a recipe into a pattern terminal, in whichever mode claims its category. The mode lays it out and the
     * server fills the slots, so the switch and the recipe travel in one packet.
     */
    private void transferToEncoder(final IRecipeLayout recipeLayout, final String recipeType) {
        final PatternEncodingMode mode = PatternEncodingModes.forRecipeCategory(recipeType);
        final RecipePlacement placement = new RecipePlacement();
        mode.placeRecipe(readRecipe(recipeLayout, recipeType), placement);

        final NBTTagCompound grids = new NBTTagCompound();
        for (final PatternGrid grid : mode.getGrids()) {
            final NBTTagCompound slots = new NBTTagCompound();
            for (final Map.Entry<Integer, List<GenericStack>> slot : placement.get(grid.getName()).entrySet()) {
                final NBTTagList options = new NBTTagList();
                for (final GenericStack option : slot.getValue()) {
                    final ItemStack stack = toItemStack(option);
                    if (!stack.isEmpty()) {
                        options.appendTag(stackToNBT(stack));
                    }
                }
                slots.setTag("#" + slot.getKey(), options);
            }
            grids.setTag(grid.getName(), slots);
        }

        final NBTTagCompound recipe = new NBTTagCompound();
        recipe.setString("mode", mode.getId().toString());
        recipe.setTag("grids", grids);

        try {
            NetworkHandler.instance().sendToServer(new PacketJEIRecipe(recipe));
        } catch (IOException e) {
            AELog.debug(e);
        }
    }

    /** What the recipe screen shows, before any mode decides where it goes. */
    private static TransferredRecipe readRecipe(final IRecipeLayout recipeLayout, final String recipeType) {
        final List<List<GenericStack>> itemInputs = new ArrayList<>();
        final NBTTagList itemOutputs = new NBTTagList();

        for (final IGuiIngredient<ItemStack> ingredient : recipeLayout.getItemStacks().getGuiIngredients().values()) {
            if (!ingredient.isInput()) {
                final ItemStack output = ingredient.getDisplayedIngredient();
                if (output != null && !output.isEmpty()) {
                    itemOutputs.appendTag(stackToNBT(output));
                }
                continue;
            }

            final List<GenericStack> options = new ArrayList<>();
            final ItemStack displayed = ingredient.getDisplayedIngredient();
            // The one on screen first, pure crystals ahead of it.
            if (displayed != null && !displayed.isEmpty()) {
                addOption(options, displayed, false);
            }
            for (final ItemStack stack : ingredient.getAllIngredients()) {
                if (stack != null && !stack.isEmpty()) {
                    addOption(options, stack, Platform.isRecipePrioritized(stack));
                }
            }
            itemInputs.add(options);
        }

        // Anything that is not an item lives in an IGuiIngredientGroup of its own, which the item loop above never
        // sees. Which kinds there are comes from IngredientConverters, so an addon's key type is carried over as
        // soon as it registers one.
        final List<GenericStack> otherInputs = new ArrayList<>();
        final List<GenericStack> otherOutputs = new ArrayList<>();
        for (final IngredientConverter<?> converter : IngredientConverters.getConverters()) {
            if (converter.getIngredientClass() != ItemStack.class) {
                collectShown(converter, recipeLayout, otherInputs, otherOutputs);
            }
        }

        // What the screen draws but does not list, such as a machine's mana bar.
        final List<GenericStack> shownInputs = new ArrayList<>();
        final List<GenericStack> shownOutputs = new ArrayList<>();
        for (final IngredientConverter<?> converter : IngredientConverters.getConverters()) {
            collectShown(converter, recipeLayout, shownInputs, shownOutputs);
        }
        final List<GenericStack> extraInputs = new ArrayList<>();
        for (final GenericStack stack : ExtraInputProviders.getExtraInputs(recipeType, shownInputs, shownOutputs)) {
            if (stack.amount() > 0) {
                extraInputs.add(stack);
            }
        }

        final NBTTagList allOutputs = itemOutputs.copy();
        for (final GenericStack stack : otherOutputs) {
            final ItemStack wrapped = GenericStack.wrapInItemStack(stack);
            if (!wrapped.isEmpty()) {
                allOutputs.appendTag(stackToNBT(wrapped));
            }
        }
        final List<GenericStack> outputs = new ArrayList<>();
        final NBTTagList promoted = promoteFocused(recipeLayout, allOutputs);
        for (int x = 0; x < promoted.tagCount(); x++) {
            final GenericStack stack = GenericStack.resolveItemStack(stackFromNBT(promoted.getCompoundTagAt(x)));
            if (stack != null) {
                outputs.add(stack);
            }
        }

        return new TransferredRecipe(recipeType, itemInputs, otherInputs, extraInputs, outputs);
    }

    private static void addOption(final List<GenericStack> options, final ItemStack stack, final boolean first) {
        final AEItemKey key = AEItemKey.of(stack);
        if (key != null) {
            final GenericStack option = new GenericStack(key, stack.getCount());
            if (first) {
                options.add(0, option);
            } else {
                options.add(option);
            }
        }
    }

    private static ItemStack toItemStack(final GenericStack stack) {
        if (stack.what() instanceof AEItemKey item) {
            return item.toStack((int) Math.min(Integer.MAX_VALUE, stack.amount()));
        }
        return GenericStack.wrapInItemStack(stack);
    }

    /**
     * Puts the thing the player was actually looking up at the front of the outputs.
     * <p>
     * A recipe screen lists what a machine makes in whatever order the screen draws it, and a pattern's first
     * output is not just decoration: it is what an interface waits for to unlock, and the output the plan
     * settles the others after. Someone who searched for the byproduct of a two-output recipe meant that one,
     * and the pattern should say so. Nothing moves when the player was browsing rather than searching, or was
     * looking up what an ingredient is *used* for.
     */
    private static NBTTagList promoteFocused(final IRecipeLayout recipeLayout, final NBTTagList outputs) {
        final IFocus<?> focus = recipeLayout.getFocus();

        if (focus == null || focus.getMode() != IFocus.Mode.OUTPUT || outputs.tagCount() < 2) {
            return outputs;
        }

        final AEKey wanted = keyOf(focus.getValue());

        if (wanted == null) {
            return outputs;
        }

        for (int x = 1; x < outputs.tagCount(); x++) {
            final AEKey made = keyOf(stackFromNBT(outputs.getCompoundTagAt(x)));

            // By what it is rather than by how much or what is written on it: the recipe screen shows one of
            // a thing and the pattern may name a stack of it.
            if (made == null || !made.getPrimaryKey().equals(wanted.getPrimaryKey())) {
                continue;
            }

            final NBTTagList reordered = new NBTTagList();
            reordered.appendTag(outputs.getCompoundTagAt(x));

            for (int y = 0; y < outputs.tagCount(); y++) {
                if (y != x) {
                    reordered.appendTag(outputs.getCompoundTagAt(y));
                }
            }

            return reordered;
        }

        return outputs;
    }

    /**
     * @return what the object is, as the network names it, or null for an ingredient no converter speaks for.
     */
    @Nullable
    private static AEKey keyOf(@Nullable final Object what) {
        final GenericStack stack = IngredientConverters.toStack(what);
        return stack == null ? null : stack.what();
    }

    private static <I> void collectShown(final IngredientConverter<I> converter, final IRecipeLayout recipeLayout,
            final List<GenericStack> inputs, final List<GenericStack> outputs) {
        final Map<Integer, ? extends IGuiIngredient<I>> group;
        try {
            group = recipeLayout.getIngredientsGroup(converter.getIngredientClass()).getGuiIngredients();
        } catch (final IllegalArgumentException e) {
            return;
        }
        for (final IGuiIngredient<I> ingredient : group.values()) {
            final I displayed = ingredient.getDisplayedIngredient();
            final GenericStack stack = displayed == null ? null : converter.getStackFromIngredient(displayed);
            if (stack != null && stack.amount() > 0) {
                (ingredient.isInput() ? inputs : outputs).add(stack);
            }
        }
    }
}
