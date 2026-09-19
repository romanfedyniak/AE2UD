/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers.encoding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.patterns.IPatternEncodingHost;
import appeng.api.patterns.PatternEncodingMode;
import appeng.api.patterns.PatternEncodingModes;
import appeng.api.patterns.PatternGrid;
import appeng.api.patterns.RecipePlacement;
import appeng.core.localization.GuiText;
import appeng.helpers.PatternHelper;

import static appeng.helpers.PatternHelper.PROCESSING_INPUT_LIMIT;
import static appeng.helpers.PatternHelper.PROCESSING_OUTPUT_LIMIT;

/** Any inputs to any outputs, for a machine the network pushes a job at. The mode every unclaimed recipe goes to. */
public final class ProcessingEncodingMode extends PatternEncodingMode {

    public static final String INPUTS = "processing";
    public static final String OUTPUTS = "output";

    public ProcessingEncodingMode() {
        super(PatternEncodingModes.PROCESSING, Arrays.asList(
                PatternGrid.of(INPUTS, PROCESSING_INPUT_LIMIT, PatternGrid.Role.INPUT),
                PatternGrid.of(OUTPUTS, PROCESSING_OUTPUT_LIMIT, PatternGrid.Role.OUTPUT)));
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Blocks.FURNACE);
    }

    @Override
    public String getTranslationKey() {
        return GuiText.ProcessingPattern.getUnlocalized();
    }

    /**
     * The compact side reaches only eight slots, so a recipe with more outputs than that has to arrive with the
     * terminal already turned round.
     */
    @Override
    public void beforeRecipePlaced(final IPatternEncodingHost host, final RecipePlacement placement) {
        if (host instanceof IProcessingEncodingHost processing) {
            processing.setInverted(PatternHelper.shouldInvert(placement.count(INPUTS), placement.count(OUTPUTS)));
        }
    }

    @Override
    public boolean isPattern(final ItemStack stack) {
        return EncodedPatterns.isEncodedPattern(stack, false);
    }

    @Override
    public boolean load(final IPatternEncodingHost host, final ItemStack pattern) {
        final ICraftingPatternDetails details = EncodedPatterns.decode(pattern, host.getEncodingWorld());
        if (details == null) {
            return false;
        }
        host.setSubstitution(details.canSubstitute());
        host.setFluidSubstitution(details.canSubstituteFluids());
        // Before decoding, never after: turning round empties the side the orientation cannot reach, and would
        // take the pattern about to be laid out with it.
        if (host instanceof IProcessingEncodingHost processing) {
            processing.setInverted(PatternHelper.shouldInvert(details.getInputs(), details.getOutputs()));
        }
        EncodedPatterns.decodeInto(details.getInputs(), host.getEncodingGrid(this, INPUTS));
        EncodedPatterns.decodeInto(details.getOutputs(), host.getEncodingGrid(this, OUTPUTS));
        return true;
    }

    @Override
    public ItemStack encode(final IPatternEncodingHost host, final EntityPlayer player) {
        final IItemHandler inputs = host.getEncodingGrid(this, INPUTS);
        final ItemStack[] in = new ItemStack[inputs.getSlots()];
        boolean any = false;
        for (int x = 0; x < in.length; x++) {
            in[x] = inputs.getStackInSlot(x);
            any |= !in[x].isEmpty();
        }
        if (!any) {
            return ItemStack.EMPTY;
        }

        final IItemHandler outputs = host.getEncodingGrid(this, OUTPUTS);
        final List<ItemStack> out = new ArrayList<>();
        for (int x = 0; x < outputs.getSlots(); x++) {
            final ItemStack stack = outputs.getStackInSlot(x);
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        if (out.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return EncodedPatterns.encode(in, out.toArray(new ItemStack[0]), false, host, player);
    }
}
