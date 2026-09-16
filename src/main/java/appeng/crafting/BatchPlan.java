/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.crafting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.world.World;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.IPatternInput;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.container.ContainerNull;
import appeng.helpers.PatternHelper;
import appeng.util.Platform;

/**
 * The ingredients of a batch - several copies of one pattern handed to a medium at once - chosen before
 * anything is drawn.
 * <p>
 * Every copy is laid out on the same table, so each slot gets one ingredient for the whole batch. Choosing
 * only reads the inventory, and the batch is then sized to what that choice can supply, so drawing it
 * afterwards cannot come up short and nothing ever has to be rolled back halfway.
 */
public final class BatchPlan {

    private final ICraftingPatternDetails details;
    /** Per slot, what one copy lays on the table, or null for a slot the pattern leaves empty. */
    private final AEKey[] placed;
    /** Per key, what one copy draws. A fabricated slot draws what its container is filled with. */
    private final Map<AEKey, Long> perCopy;
    private final int copies;

    private BatchPlan(final ICraftingPatternDetails details, final AEKey[] placed,
            final Map<AEKey, Long> perCopy, final int copies) {
        this.details = details;
        this.placed = placed;
        this.perCopy = perCopy;
        this.copies = copies;
    }

    /**
     * @param available what the crafting CPU holds; only read.
     * @param wanted    the most copies worth planning for, at least one.
     * @return the plan, or null when not even one copy can be made from what is there.
     */
    @Nullable
    public static BatchPlan of(final ICraftingPatternDetails details, final KeyCounter available,
            final int wanted, final World world) {
        final GenericStack[] inputs = details.getInputs();
        final AEKey[] placed = new AEKey[inputs.length];
        final Map<AEKey, Long> perCopy = new LinkedHashMap<>();

        for (int x = 0; x < inputs.length; x++) {
            if (inputs[x] == null) {
                continue;
            }

            final IPatternInput slot = details.getPatternInputs().get(x);
            final AEKey drawn;
            final long need;

            if (details.isCraftable() && slot.isFabricated() && inputs[x].what() instanceof AEItemKey) {
                final GenericStack supplied = slot.getSupplied();
                drawn = supplied.what();
                need = supplied.amount() * inputs[x].amount();
                placed[x] = inputs[x].what();
                if (free(available, perCopy, drawn) < need) {
                    return null;
                }
            } else if (details.isCraftable()) {
                need = inputs[x].amount();
                drawn = chooseItem(details, x, inputs[x], slot, available, perCopy, world);
                if (drawn == null) {
                    return null;
                }
                placed[x] = drawn;
            } else {
                drawn = inputs[x].what();
                need = inputs[x].amount();
                placed[x] = drawn;
                if (free(available, perCopy, drawn) < need) {
                    return null;
                }
            }

            perCopy.merge(drawn, need, Long::sum);
        }

        long copies = wanted;
        for (final Map.Entry<AEKey, Long> e : perCopy.entrySet()) {
            copies = Math.min(copies, available.get(e.getKey()) / e.getValue());
        }

        return copies < 1 ? null : new BatchPlan(details, placed, perCopy, (int) copies);
    }

    /**
     * The first item that may go in a crafting slot with one copy's worth still unclaimed, in the order the
     * CPU has always tried them: every option of a substituting pattern, else the encoded item, else - for
     * a damaged tool - any wear of it.
     */
    @Nullable
    private static AEKey chooseItem(final ICraftingPatternDetails details, final int x, final GenericStack input,
            final IPatternInput slot, final KeyCounter available, final Map<AEKey, Long> claimed, final World world) {
        final List<AEKey> candidates = new ArrayList<>();

        if (details.canSubstitute()) {
            for (final GenericStack option : slot.getOptions()) {
                for (final var entry : available.findFuzzy(option.what(), FuzzyMode.IGNORE_ALL)) {
                    candidates.add(entry.getKey());
                }
            }
        } else if (free(available, claimed, input.what()) >= input.amount()) {
            candidates.add(input.what());
        } else if (input.what() instanceof AEItemKey itemKey
                && (itemKey.getItem().isDamageable() || Platform.isGTDamageableItem(itemKey.getItem()))) {
            for (final var entry : available.findFuzzy(input.what(), FuzzyMode.IGNORE_ALL)) {
                candidates.add(entry.getKey());
            }
        }

        for (final AEKey candidate : candidates) {
            if (!(candidate instanceof AEItemKey itemKey) || free(available, claimed, candidate) < input.amount()) {
                continue;
            }
            final int shown = (int) Math.min(input.amount(), itemKey.getMaxStackSize());
            if (details.isValidItemForSlot(x, itemKey.toStack(shown), world)) {
                return candidate;
            }
        }

        return null;
    }

    private static long free(final KeyCounter available, final Map<AEKey, Long> claimed, final AEKey what) {
        return available.get(what) - claimed.getOrDefault(what, 0L);
    }

    public int getCopies() {
        return this.copies;
    }

    /** Every key this plan draws. */
    public Set<AEKey> getDrawnKeys() {
        return this.perCopy.keySet();
    }

    /**
     * Takes {@code copies} copies' worth out of the inventory the plan was made from.
     *
     * @param copies no more than {@link #getCopies()}.
     * @return false, with nothing taken, if the inventory changed since the plan was made.
     */
    public boolean draw(final MEStorage from, final int copies, final IActionSource src) {
        for (final Map.Entry<AEKey, Long> e : this.perCopy.entrySet()) {
            final long amount = e.getValue() * copies;
            if (from.extract(e.getKey(), amount, Actionable.SIMULATE, src) < amount) {
                return false;
            }
        }
        for (final Map.Entry<AEKey, Long> e : this.perCopy.entrySet()) {
            from.extract(e.getKey(), e.getValue() * copies, Actionable.MODULATE, src);
        }
        return true;
    }

    /** Returns what {@link #draw} took, for a batch no medium accepted. */
    public void putBack(final MEStorage into, final int copies, final IActionSource src) {
        for (final Map.Entry<AEKey, Long> e : this.perCopy.entrySet()) {
            into.insert(e.getKey(), e.getValue() * copies, Actionable.MODULATE, src);
        }
    }

    /** One copy's table, as a medium is handed it. */
    public InventoryCrafting getTable() {
        final InventoryCrafting table = this.details.isCraftable()
                ? new InventoryCrafting(new ContainerNull(), 3, 3)
                : new InventoryCrafting(new ContainerNull(), PatternHelper.PROCESSING_INPUT_WIDTH,
                        PatternHelper.PROCESSING_INPUT_HEIGHT);
        final GenericStack[] inputs = this.details.getInputs();

        for (int x = 0; x < this.placed.length && x < table.getSizeInventory(); x++) {
            if (this.placed[x] instanceof AEItemKey itemKey) {
                table.setInventorySlotContents(x, itemKey.toStack((int) inputs[x].amount()));
            }
        }
        return table;
    }

    /** One copy's ingredients that a table cannot hold. */
    public GenericStack[] getExtras() {
        final GenericStack[] inputs = this.details.getInputs();
        final List<GenericStack> extras = new ArrayList<>();

        for (int x = 0; x < this.placed.length; x++) {
            if (this.placed[x] != null && !(this.placed[x] instanceof AEItemKey)) {
                extras.add(new GenericStack(this.placed[x], inputs[x].amount()));
            }
        }
        return extras.toArray(new GenericStack[0]);
    }
}
