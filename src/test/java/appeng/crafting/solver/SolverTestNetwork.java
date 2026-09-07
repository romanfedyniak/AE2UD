/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.crafting.solver;


import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * A network made of nothing.
 * <p>
 * The solver sees keys and longs, so a test can give it keys and longs: no world, no registries, no items.
 * That is the point of the line the normalisation phase draws, and this class is what proves the line holds -
 * if anything below it ever needs Minecraft, this stops compiling.
 */
final class SolverTestNetwork implements ICraftingSource {

    private final Map<String, AEKey> keys = new LinkedHashMap<>();
    private final Map<AEKey, List<SolverPattern>> patterns = new LinkedHashMap<>();
    private final Set<AEKey> emitters = new LinkedHashSet<>();
    private final KeyCounter stock = new KeyCounter();

    /** A thing, named. The same name always gives the same key. */
    AEKey key(final String name) {
        return this.keys.computeIfAbsent(name, n -> new TestKey(n, TestKeyType.ITEMS));
    }

    /** A thing measured in thousandths, like a fluid - a bucket is a thousand of it. */
    AEKey fluid(final String name) {
        return this.keys.computeIfAbsent(name, n -> new TestKey(n, TestKeyType.FLUIDS));
    }

    GenericStack stack(final String name, final long amount) {
        return new GenericStack(this.key(name), amount);
    }

    SolverTestNetwork inStorage(final String name, final long amount) {
        this.stock.add(this.key(name), amount);
        return this;
    }

    SolverTestNetwork emits(final String name) {
        this.emitters.add(this.key(name));
        return this;
    }

    /**
     * A pattern taking one of each named input and making one of the named output.
     */
    SolverTestNetwork pattern(final String name, final String output, final String... inputs) {
        return this.pattern(name, new GenericStack[] { this.stack(output, 1) }, ingredients(inputs));
    }

    SolverTestNetwork pattern(final String name, final GenericStack[] outputs,
            final List<SolverIngredient> inputs) {
        final SolverPattern pattern = new SolverPattern(name, inputs, Arrays.asList(outputs), 0);

        for (final GenericStack output : outputs) {
            this.patterns.computeIfAbsent(output.what(), k -> new ArrayList<>()).add(pattern);
        }

        return this;
    }

    private List<SolverIngredient> ingredients(final String... inputs) {
        final List<SolverIngredient> list = new ArrayList<>(inputs.length);

        for (final String input : inputs) {
            list.add(SolverIngredient.of(this.key(input), 1));
        }

        return list;
    }

    KeyCounter getStock() {
        return this.stock;
    }

    SolverPlan solve(final String what, final long amount) {
        return new CraftingSolver(this).solve(this.key(what), amount, this.stock);
    }

    SolverPlan solve(final String what, final long amount, final SolverLimits limits) {
        return new CraftingSolver(this, limits).solve(this.key(what), amount, this.stock);
    }

    /** How many times the pattern of that name runs in the plan. */
    static long crafts(final SolverPlan plan, final String name) {
        for (final Map.Entry<SolverPattern, Long> entry : plan.getCrafts().entrySet()) {
            if (name.equals(entry.getKey().getSource())) {
                return entry.getValue();
            }
        }

        return 0;
    }

    @Override
    public List<SolverPattern> patternsFor(final AEKey what) {
        return this.patterns.getOrDefault(what, Collections.emptyList());
    }

    @Override
    public boolean canEmit(final AEKey what) {
        return this.emitters.contains(what);
    }

    private static final class TestKey extends AEKey {

        private final String name;
        private final AEKeyType type;

        private TestKey(final String name, final AEKeyType type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public AEKeyType getType() {
            return this.type;
        }

        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("test", this.name);
        }

        @Override
        public Object getPrimaryKey() {
            return this.name;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public void toTag(final NBTTagCompound tag) {
        }

        @Override
        public void writeToPacket(final ByteBuf data) {
        }

        @Override
        protected ITextComponent computeDisplayName() {
            return new TextComponentString(this.name);
        }

        @Override
        public void addDrops(final long amount, final List<ItemStack> drops, @Nonnull final World world,
                @Nonnull final BlockPos pos) {
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    private static final class TestKeyType extends AEKeyType {

        private static final AEKeyType ITEMS = new TestKeyType("items", 1);
        private static final AEKeyType FLUIDS = new TestKeyType("fluids", 1000);

        private final int amountPerUnit;

        private TestKeyType(final String name, final int amountPerUnit) {
            super(new ResourceLocation("test", name), TestKey.class, new TextComponentString(name));
            this.amountPerUnit = amountPerUnit;
        }

        @Override
        public int getAmountPerUnit() {
            return this.amountPerUnit;
        }

        @Override
        public AEKey readFromPacket(@Nonnull final ByteBuf input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AEKey loadKeyFromTag(@Nonnull final NBTTagCompound tag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResourceLocation getButtonTexture() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemStack getButtonIcon() {
            throw new UnsupportedOperationException();
        }
    }
}
