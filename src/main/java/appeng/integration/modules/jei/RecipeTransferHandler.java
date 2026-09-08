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


import appeng.container.implementations.ContainerCraftingTerm;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.implementations.ContainerWirelessCraftingTerminal;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.container.slot.SlotFakePatternGrid;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketJEIRecipe;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.PatternHelper;
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
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static appeng.helpers.ItemStackHelper.stackFromNBT;
import static appeng.helpers.ItemStackHelper.stackToNBT;


class RecipeTransferHandler<T extends Container> implements IRecipeTransferHandler<T> {

    private final Class<T> containerClass;

    RecipeTransferHandler(Class<T> containerClass) {
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
            if (recipeType.equals(VanillaRecipeCategoryUid.CRAFTING) && (container instanceof ContainerCraftingTerm || container instanceof ContainerWirelessCraftingTerminal)) {
                JEIMissingItem error = new JEIMissingItem(container, recipeLayout);
                if (error.errored())
                    return error;
            }
            return null;
        }

        if (container instanceof ContainerPatternEncoder) {
            try {
                if (!((ContainerPatternEncoder) container).isCraftingMode()) {
                    if (recipeType.equals(VanillaRecipeCategoryUid.CRAFTING)) {
                        NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.CraftMode", "1"));
                    }
                } else if (!recipeType.equals(VanillaRecipeCategoryUid.CRAFTING)) {

                    NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.CraftMode", "0"));
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Map<Integer, ? extends IGuiIngredient<ItemStack>> ingredients = recipeLayout.getItemStacks().getGuiIngredients();

        final NBTTagCompound recipe = new NBTTagCompound();
        final NBTTagList outputs = new NBTTagList();


        // A crafting recipe is a shape, so an empty square in it has to stay an empty square and still take
        // its place in the grid. A processing recipe is not: the recipe screen's own layout may leave gaps
        // between the slots a machine happens to draw, and carrying those gaps into the pattern scattered
        // the ingredients about. Bound for the processing grid, an ingredient takes the next free slot.
        //
        // Decided by the recipe rather than by the terminal's current mode: the switch above is a packet
        // the server has not answered yet, so isCraftingMode() here is still the mode being left behind.
        final boolean packed = container instanceof ContainerPatternEncoder
                && !recipeType.equals(VanillaRecipeCategoryUid.CRAFTING);

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

            if (packed && list.isEmpty()) {
                continue;
            }

            for (final Slot slot : container.inventorySlots) {
                if (slot instanceof SlotCraftingMatrix || slot instanceof SlotFakePatternGrid) {
                    if (slot.getSlotIndex() == slotIndex) {
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

        // Fluid ingredients live in their own IGuiIngredientGroup, which the item loop above never sees -
        // which is why "Move Items" silently skipped every fluid in a recipe. Only the processing grid can
        // take them: its slots are fake, so they can hold a wrapped key. A crafting recipe is matched
        // against items, whether the grid it is typed into is real or fake, and `packed` is true for
        // exactly the recipes bound for the other grid. The server writes this one through the inventory
        // rather than through the slots, so the slot's own filter never sees it.
        if (packed) {
            this.transferFluids(container, recipeLayout, recipe, outputs);
        }

        // The grid the recipe is about to land in only reaches eight slots on its compact side, so a
        // recipe that needs more outputs than that has to arrive with the terminal already turned round.
        if (container instanceof ContainerPatternEncoder && !recipeType.equals(VanillaRecipeCategoryUid.CRAFTING)) {
            final boolean invert = PatternHelper.shouldInvert(recipe.getKeySet().size(), outputs.tagCount());

            if (invert != ((ContainerPatternEncoder) container).isInverted()) {
                try {
                    NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Invert", invert ? "1" : "0"));
                } catch (IOException e) {
                    AELog.debug(e);
                }
            }
        }

        recipe.setTag("outputs", promoteFocused(recipeLayout, outputs));

        // Ctrl+Move Items: craft whatever this recipe is missing instead of refusing the transfer.
        // Ctrl+Shift additionally starts that craft right away instead of opening the confirm screen.
        if (GuiScreen.isCtrlKeyDown() && (container instanceof ContainerCraftingTerm || container instanceof ContainerWirelessCraftingTerminal)) {
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

    private static Set<Integer> collectFreeSlots(final Container container, final NBTTagCompound recipe) {
        final Set<Integer> free = new LinkedHashSet<>();

        for (final Slot slot : container.inventorySlots) {
            if (slot instanceof SlotCraftingMatrix || slot instanceof SlotFakePatternGrid) {
                if (!recipe.hasKey("#" + slot.getSlotIndex())) {
                    free.add(slot.getSlotIndex());
                }
            }
        }

        return free;
    }

    /**
     * Writes the layout's fluid ingredients into the recipe alongside the items: inputs into whichever matrix
     * slots the item pass left empty, outputs appended to the output list. Each one travels as the same
     * wrapped placeholder {@code ItemStack} the pattern slots already store, so the server needs no new case -
     * {@code AppEngInternalAEInventory} unwraps it back into a key on the way in.
     */
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
     * @return what the object is, as the network names it, or null for something neither items nor fluids.
     */
    @Nullable
    private static AEKey keyOf(@Nullable final Object what) {
        if (what instanceof ItemStack stack) {
            return stack.isEmpty() ? null : GenericStack.resolveItemStack(stack).what();
        }

        if (what instanceof FluidStack fluid) {
            return AEFluidKey.of(fluid);
        }

        return null;
    }

    private void transferFluids(final T container, final IRecipeLayout recipeLayout, final NBTTagCompound recipe,
            final NBTTagList outputs) {
        final Map<Integer, ? extends IGuiIngredient<FluidStack>> fluids = recipeLayout.getFluidStacks().getGuiIngredients();
        if (fluids.isEmpty()) {
            return;
        }

        // Matrix slots the item pass did not claim, in slot order. A set, not a list: the crafting matrix
        // and the processing grid are both made of the same slot class and both number from zero, so the
        // low indices turn up twice and two fluids would otherwise be handed the same one.
        final List<Integer> freeSlots = new ArrayList<>(collectFreeSlots(container, recipe));

        int nextFree = 0;
        for (final IGuiIngredient<FluidStack> ingredient : fluids.values()) {
            final FluidStack displayed = ingredient.getDisplayedIngredient();
            if (displayed == null || displayed.amount <= 0) {
                continue;
            }

            final ItemStack wrapped = GenericStack.wrapInItemStack(AEFluidKey.of(displayed), displayed.amount);
            if (wrapped.isEmpty()) {
                continue;
            }

            if (ingredient.isInput()) {
                if (nextFree >= freeSlots.size()) {
                    // More fluid inputs than the pattern has room for; the rest are simply not transferred,
                    // the same thing that happens to a recipe with more item inputs than slots.
                    continue;
                }
                final NBTTagList tags = new NBTTagList();
                tags.appendTag(stackToNBT(wrapped));
                recipe.setTag("#" + freeSlots.get(nextFree++), tags);
            } else {
                outputs.appendTag(stackToNBT(wrapped));
            }
        }
    }
}
