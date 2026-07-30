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
import appeng.api.stacks.GenericStack;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketJEIRecipe;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.util.Platform;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.transfer.RecipeTransferErrorInternal;
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
import java.util.List;
import java.util.Map;

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

            for (final Slot slot : container.inventorySlots) {
                if (slot instanceof SlotCraftingMatrix || slot instanceof SlotFakeCraftingMatrix) {
                    if (slot.getSlotIndex() == slotIndex) {
                        final NBTTagList tags = new NBTTagList();
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
        // which is why "Move Items" silently skipped every fluid in a recipe. Only the pattern terminal can
        // take them: its matrix slots are fake, so they can hold a wrapped key. A real crafting matrix
        // cannot, and a placeholder pushed into one would be an item that does not exist.
        if (container instanceof ContainerPatternEncoder) {
            this.transferFluids(container, recipeLayout, recipe, outputs);
        }

        recipe.setTag("outputs", outputs);

        try {
            NetworkHandler.instance().sendToServer(new PacketJEIRecipe(recipe));
        } catch (IOException e) {
            AELog.debug(e);
        }

        return null;
    }

    /**
     * Writes the layout's fluid ingredients into the recipe alongside the items: inputs into whichever matrix
     * slots the item pass left empty, outputs appended to the output list. Each one travels as the same
     * wrapped placeholder {@code ItemStack} the pattern slots already store, so the server needs no new case -
     * {@code AppEngInternalAEInventory} unwraps it back into a key on the way in.
     */
    private void transferFluids(final T container, final IRecipeLayout recipeLayout, final NBTTagCompound recipe,
            final NBTTagList outputs) {
        final Map<Integer, ? extends IGuiIngredient<FluidStack>> fluids = recipeLayout.getFluidStacks().getGuiIngredients();
        if (fluids.isEmpty()) {
            return;
        }

        // Matrix slots the item pass did not claim, in slot order.
        final List<Integer> freeSlots = new ArrayList<>();
        for (final Slot slot : container.inventorySlots) {
            if (slot instanceof SlotCraftingMatrix || slot instanceof SlotFakeCraftingMatrix) {
                if (!recipe.hasKey("#" + slot.getSlotIndex())) {
                    freeSlots.add(slot.getSlotIndex());
                }
            }
        }

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
