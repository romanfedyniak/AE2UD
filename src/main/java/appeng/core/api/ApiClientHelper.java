/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.api;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.IncludeExclude;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.IPatternInput;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.StorageCell;
import appeng.api.util.IClientHelper;
import appeng.client.ActionKey;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import appeng.me.storage.BasicCellInventory;
import appeng.util.Platform;

/**
 * The storage cell tooltip: how full the cell is, what its cards do to it, and the few things in it that
 * there is most of.
 * <p/>
 * It shows {@value #PREVIEW_ROWS} rows and no more. A cell holding all sixty-three of its types used to
 * print all sixty-three, which ran off the top and the bottom of the screen; the window the view key opens
 * is where the whole list belongs, and this says only what the cell is mostly full of.
 * <p/>
 * Only {@link BasicCellInventory} carries the byte and type accounting this shows;
 * {@link appeng.me.storage.CreativeCellInventory} never reaches this method - {@code ItemCreativeStorageCell}
 * renders its own, simpler tooltip.
 */
public class ApiClientHelper implements IClientHelper {

    /** How many of a cell's contents a tooltip names before it says how many more there are. */
    public static final int PREVIEW_ROWS = BasicCellInventory.PREVIEW_SIZE;

    /**
     * The same for a pattern's ingredients, and its own number rather than the cell's: what a cell puts in
     * its summary is written into the cell's tag, and a tooltip's length must not be tied to that.
     */
    public static final int PATTERN_ROWS = 5;

    @Override
    public void addCellInformation(final StorageCell handler, final List<String> lines) {
        if (handler == null) {
            return;
        }

        if (!(handler instanceof BasicCellInventory)) {
            // Creative cells, and any future cell that is not a BasicCellInventory, carry no byte or type
            // accounting - but they can still be looked into.
            addViewHint(lines);
            return;
        }

        final BasicCellInventory cellInventory = (BasicCellInventory) handler;

        lines.add(Tooltips.bytesUsed(cellInventory.getUsedBytes(), cellInventory.getTotalBytes()).getFormattedText());
        lines.add(Tooltips.typesUsed(cellInventory.getStoredItemTypes(), cellInventory.getTotalItemTypes()).getFormattedText());

        // The share, not the free space, is what stops a cell taking more once the card is in - and it is
        // not something the two lines above can be read to mean. The divisor is named as well as the share,
        // since every question about this number is really a question about what it was divided by.
        if (cellInventory.isEqualDistribution()) {
            lines.add(I18n.format(GuiText.EqualDistributionOf.getUnlocalized(),
                    NumberFormat.getInstance().format(cellInventory.getBytesPerShare()),
                    cellInventory.getShares(), cellInventory.getTotalItemTypes()));
        }

        // Red where it is dangerous. On a partitioned cell the card destroys the overflow of the types
        // the player named; on an unpartitioned one it destroys the overflow of all sixty-three the cell
        // happens to have picked up, which is not what anyone means to set up.
        if (cellInventory.isVoidOverflow()) {
            final String line = GuiText.OverflowDestruction.getLocal();
            lines.add(cellInventory.isPreformatted() ? line : TextFormatting.RED + line);
        }

        if (cellInventory.isPreformatted()) {
            final String list = (cellInventory.getPartitionListMode() == IncludeExclude.WHITELIST
                    ? GuiText.Included : GuiText.Excluded).getLocal();
            final String match = (cellInventory.isFuzzy() ? GuiText.Fuzzy : GuiText.Precise).getLocal();

            lines.add("[" + GuiText.Partitioned.getLocal() + "] - " + list + ' ' + match);

            if (cellInventory.isSticky()) {
                lines.add(GuiText.Sticky.getLocal());
            }
        }

        addContents(cellInventory, lines);
        addViewHint(lines);
    }

    /**
     * A pattern's tooltip: what it makes, what it takes, whether it substitutes, who wrote it, and how to
     * open the view that draws it.
     */
    @Override
    public void addPatternInformation(final ICraftingPatternDetails details, final ItemStack stack,
            final List<String> lines) {
        if (details == null) {
            return;
        }

        final boolean isCrafting = details.isCraftable();

        final String label = (isCrafting ? GuiText.Crafts.getLocal() : GuiText.Creates.getLocal()) + ": ";
        final String with = GuiText.With.getLocal() + ": ";

        addStacks(label, Arrays.asList(details.getCondensedOutputs()), lines);
        addStacks(with, displayInputs(details), lines);

        if (isCrafting) {
            lines.add(GuiText.Substitute.getLocal() + ' '
                    + (details.canSubstitute() ? GuiText.Yes : GuiText.No).getLocal());

            if (details.canSubstituteFluids()) {
                lines.add(GuiText.UsesFluidsDirectly.getLocal());
            }
        }

        addAuthor(stack, lines);
        addViewHint(lines);
    }

    /**
     * One line per stack under a heading, and no more than {@value #PATTERN_ROWS} of them. A pattern from a
     * bench nine squares across has up to eighty-one ingredients, which ran off the top and the bottom of
     * the screen; the view the key opens draws the whole recipe, and this says what it mostly takes.
     */
    private static void addStacks(final String heading, final List<GenericStack> stacks,
            final List<String> lines) {
        final String and = ' ' + GuiText.And.getLocal() + ' ';

        int shown = 0;
        int more = 0;

        for (final GenericStack stack : stacks) {
            if (stack == null) {
                continue;
            }

            if (shown == PATTERN_ROWS) {
                more++;
                continue;
            }

            lines.add((shown == 0 ? heading : and) + describe(stack));
            shown++;
        }

        if (more > 0) {
            lines.add(TextFormatting.DARK_GRAY + I18n.format(GuiText.AndMoreTypes.getUnlocalized(), more));
        }
    }

    /** Who encoded the pattern, if the terminal wrote a name into it. */
    public static void addAuthor(final ItemStack stack, final List<String> lines) {
        if (!stack.hasTagCompound()) {
            return;
        }

        final String author = stack.getTagCompound().getString("author");

        if (!author.isEmpty()) {
            lines.add(TextFormatting.LIGHT_PURPLE + I18n.format(GuiText.EncodedBy.getUnlocalized(), author));
        }
    }

    /**
     * The ingredients as the pattern will actually ask for them: a slot the network fills in shows its
     * contents rather than the container, because the container is never taken out of storage.
     */
    public static List<GenericStack> displayInputs(final ICraftingPatternDetails details) {
        final GenericStack[] sparse = details.getInputs();
        final Map<AEKey, GenericStack> merged = new LinkedHashMap<>();

        for (int x = 0; x < sparse.length; x++) {
            if (sparse[x] == null) {
                continue;
            }

            GenericStack shown = sparse[x];

            final IPatternInput input = details.getPatternInputs().get(x);

            if (input.isFabricated()) {
                final GenericStack supplied = input.getSupplied();
                shown = new GenericStack(supplied.what(), supplied.amount() * sparse[x].amount());
            }

            merged.merge(shown.what(), shown, GenericStack::sum);
        }

        return new ArrayList<>(merged.values());
    }

    /**
     * Amounts go through the key type, so a thousand millibuckets reads as one bucket. Printing the raw
     * number was only ever tolerable while patterns held items.
     */
    private static String describe(final GenericStack stack) {
        return stack.what().formatAmount(stack.amount(), AmountFormat.FULL) + ' '
                + Platform.getItemDisplayName(stack.what());
    }

    /**
     * How to open the window that holds the whole list. Only said when the key is actually bound - an
     * unbound key has no name to print, and telling the player to press nothing is worse than saying
     * nothing.
     */
    @SideOnly(Side.CLIENT)
    public static void addViewHint(final List<String> lines) {
        final String key = AppEng.proxy.getActionKeyName(ActionKey.VIEW_PATTERN);

        if (key != null) {
            lines.add(TextFormatting.DARK_GRAY + I18n.format(GuiText.ViewPatternHint.getUnlocalized(),
                    TextFormatting.GRAY + key + TextFormatting.DARK_GRAY));
        }
    }

    /**
     * The few keys there is most of, largest first. What a partitioned cell is set to accept is deliberately
     * not listed here instead: the filter is a setting, and this line answers what is in the cell - which
     * used to be silently replaced by the filter, with nothing on screen saying which of the two was shown.
     * <p/>
     * On the client a cell may know no more than those few, so how many are left over is counted from its
     * type total and not from the list.
     */
    private static void addContents(final BasicCellInventory cell, final List<String> lines) {
        final List<Object2LongMap.Entry<AEKey>> stored = new ArrayList<>();

        for (final Object2LongMap.Entry<AEKey> entry : cell.getAvailableStacks()) {
            stored.add(entry);
        }

        stored.sort(Comparator.comparingLong(Object2LongMap.Entry<AEKey>::getLongValue).reversed());

        final int shown = Math.min(PREVIEW_ROWS, stored.size());
        for (int i = 0; i < shown; i++) {
            final AEKey what = stored.get(i).getKey();

            // Unformatted: see WrappedGenericStack.getItemStackDisplayName - the trailing RESET that
            // getFormattedText() adds would recolour the rest of this line.
            lines.add(what.getDisplayName().getUnformattedText() + ": "
                    + what.formatAmount(stored.get(i).getLongValue(), AmountFormat.FULL));
        }

        final long more = cell.getStoredItemTypes() - shown;
        if (more > 0) {
            lines.add(TextFormatting.DARK_GRAY + I18n.format(GuiText.AndMoreTypes.getUnlocalized(), more));
        }
    }
}
