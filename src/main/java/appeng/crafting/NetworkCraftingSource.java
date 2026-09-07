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


import appeng.api.config.FuzzyMode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.IPatternInput;
import appeng.api.networking.crafting.IPatternInputs;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AEConfig;
import appeng.crafting.solver.ICraftingSource;
import appeng.crafting.solver.SolverIngredient;
import appeng.crafting.solver.SolverPattern;
import appeng.me.cache.CraftingGridCache;
import appeng.util.Platform;
import net.minecraft.world.World;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * The network, said in the only terms the solver understands.
 * <p>
 * This is where a pattern stops being a thing with slots, a crafting grid and a world, and becomes two flat
 * lists of keys and amounts. Everything Minecraft-shaped about crafting is settled here and nowhere below:
 * which substitutes count, which slot the network fills in for itself, what a craft hands back. It is the
 * line the whole design is drawn around, and the reason the solver can be tested without a world.
 * <p>
 * One of these belongs to one job. Normalising is cached inside it because the same pattern is reached from
 * several keys, and the answer cannot change while a job is being worked out.
 */
public final class NetworkCraftingSource implements ICraftingSource {

    private final ICraftingGrid grid;
    private final World world;
    private final KeyCounter stock;
    private final AEKey rootKey;
    @Nullable
    private final ICraftingPatternDetails rootPattern;
    private final boolean requestedByPlayer;
    private final boolean substitutesEnabled;
    private final Map<ICraftingPatternDetails, SolverPattern> normalized = new IdentityHashMap<>();

    /**
     * @param stock       what the network holds, for finding a part-used tool that would do. Read only.
     * @param rootPattern a pattern registered nowhere, satisfying the request directly - see
     *                    {@link VirtualPatternDetails}. Null for an ordinary request.
     */
    public NetworkCraftingSource(final ICraftingGrid grid, final World world, final KeyCounter stock,
            final AEKey rootKey, @Nullable final ICraftingPatternDetails rootPattern,
            final boolean requestedByPlayer) {
        this.grid = grid;
        this.world = world;
        this.stock = stock;
        this.rootKey = rootKey;
        this.rootPattern = rootPattern;
        this.requestedByPlayer = requestedByPlayer;
        this.substitutesEnabled = AEConfig.instance().getEnableCraftingSubstitutes();
    }

    @Override
    public List<SolverPattern> patternsFor(final AEKey what) {
        final boolean root = what.equals(this.rootKey);

        if (this.rootPattern != null && root) {
            return Collections.singletonList(this.normalize(this.rootPattern));
        }

        final List<SolverPattern> found = new ArrayList<>();

        for (final ICraftingPatternDetails details : this.grid.getCraftingFor(what, null, 0, this.world)) {
            // A pattern whose result never comes back is a thing only a player can ask for, and only for the
            // job's own output: anything below would wait for a delivery that is not coming, and a machine
            // that ordered it would never be handed anything and would order it again.
            if (CraftingGridCache.isFakeCrafting(this.grid.getMediums(details))
                    && !(root && this.requestedByPlayer)) {
                continue;
            }

            found.add(this.normalize(details));
        }

        return found;
    }

    @Override
    public boolean canEmit(final AEKey what) {
        // A pattern handed in for the root replaces every other way of getting it, an emitter included.
        if (this.rootPattern != null && what.equals(this.rootKey)) {
            return false;
        }

        return this.grid.canEmitFor(what);
    }

    /**
     * @return the pattern the solver reads, worked out once per job.
     */
    public SolverPattern normalize(final ICraftingPatternDetails details) {
        return this.normalized.computeIfAbsent(details, this::flatten);
    }

    private SolverPattern flatten(final ICraftingPatternDetails details) {
        final GenericStack[] sparse = details.getInputs();
        final IPatternInputs inputs = details.getPatternInputs();
        final List<SolverIngredient> ingredients = new ArrayList<>();
        // Insertion-ordered: the first output is the one a pattern is chosen for, and the solver settles the
        // rest after it. A container handed back joins the end rather than displacing that.
        final Map<AEKey, Long> outputs = new LinkedHashMap<>();

        for (final GenericStack out : details.getCondensedOutputs()) {
            if (out != null) {
                outputs.merge(out.what(), out.amount(), Long::sum);
            }
        }

        for (int x = 0; x < sparse.length; x++) {
            if (sparse[x] == null) {
                continue;
            }

            final IPatternInput slot = inputs.get(x);
            final long perCraft = sparse[x].amount();

            final List<GenericStack> options = this.optionsFor(details, slot, sparse[x], perCraft);
            final long[] uses = new long[options.size()];

            for (int option = 0; option < uses.length; option++) {
                uses[option] = slot.getUses(options.get(option));
            }

            ingredients.add(new SolverIngredient(options, uses));

            final GenericStack back = slot.getReturned();

            if (back != null) {
                outputs.merge(back.what(), back.amount() * perCraft, Long::sum);
            }
        }

        final List<GenericStack> made = new ArrayList<>(outputs.size());

        for (final Map.Entry<AEKey, Long> entry : outputs.entrySet()) {
            made.add(new GenericStack(entry.getKey(), entry.getValue()));
        }

        return new SolverPattern(details, ingredients, made, details.getPriority());
    }

    /**
     * Everything that would satisfy one slot, most preferred first, each carrying what a single craft takes.
     */
    private List<GenericStack> optionsFor(final ICraftingPatternDetails details, final IPatternInput slot,
            final GenericStack encoded, final long perCraft) {
        // The slot already knows whether the pattern substitutes; the config is the separate question of
        // whether this pack wants substitution at all.
        final List<GenericStack> declared = details.canSubstitute() && !this.substitutesEnabled
                ? Collections.singletonList(slot.getOptions().get(0))
                : slot.getOptions();

        final Set<AEKey> seen = new LinkedHashSet<>();
        final List<GenericStack> options = new ArrayList<>(declared.size() + 1);

        for (final GenericStack option : declared) {
            if (seen.add(option.what())) {
                options.add(new GenericStack(option.what(), option.amount() * perCraft));
            }
        }

        // A part-used tool is as good as a whole one, and after a job the network is largely part-used ones.
        if (details.isCraftable() && !slot.isFabricated() && isWearable(encoded.what())) {
            for (final var entry : this.stock.findFuzzy(encoded.what(), FuzzyMode.IGNORE_ALL)) {
                if (entry.getLongValue() > 0 && seen.add(entry.getKey())) {
                    options.add(new GenericStack(entry.getKey(), perCraft));
                }
            }
        }

        return options.isEmpty() ? Collections.singletonList(encoded) : options;
    }

    private static boolean isWearable(final AEKey what) {
        return what instanceof AEItemKey itemKey
                && (itemKey.getItem().isDamageable() || Platform.isGTDamageableItem(itemKey.getItem()));
    }
}
