/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.migration;


import java.util.Arrays;

import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;


/**
 * What the {@code ItemStack} read hook costs next to the read itself. The mixin does not apply in a plain JUnit
 * run, so the hook is called by hand before the constructor, as the mixin would.
 */
public final class StackReadHookBenchmarkTest {

    private static final int STACKS = 200_000;
    private static final int ROUNDS = 15;
    private static final int WARMUP = 5;

    private static long sink;

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.register();
        LegacyRewrites.addStackRewriter("minecraft:diamond", stack -> {
            if (stack.getShort("Damage") != 0) {
                stack.setShort("Damage", (short) 0);
            }
        });
    }

    @AfterAll
    public static void stop() {
        LegacyRewrites.stopWorld();
    }

    @Test
    public void hookCost() {
        final double read = median(false, null, "minecraft:dye");
        final double off = median(true, null, "minecraft:dye");
        final double legacyOther = median(true, new LegacyRewritesTest.Log(), "minecraft:dye");
        final double legacyRead = median(false, new LegacyRewritesTest.Log(), "minecraft:diamond");
        final double legacyRewritten = median(true, new LegacyRewritesTest.Log(), "minecraft:diamond");

        System.out.printf("ItemStack(NBT) alone:                            %6.1f ns%n", read);
        System.out.printf("  + hook, world not from UEL:                    %6.1f ns%n", off);
        System.out.printf("  + hook, UEL world, id with no rewriter:        %6.1f ns%n", legacyOther);
        System.out.printf("ItemStack(NBT) of an id with a rewriter, alone:  %6.1f ns%n", legacyRead);
        System.out.printf("  + hook, UEL world, rewriter that finds nothing: %5.1f ns%n", legacyRewritten);

        assertThat(off - read, lessThan(50.0));
        assertThat(legacyOther - read, lessThan(100.0));
    }

    private static double median(final boolean hook, final MigrationLog log, final String id) {
        LegacyRewrites.startWorld(log);
        final double[] perStack = new double[ROUNDS];
        for (int round = -WARMUP; round < ROUNDS; round++) {
            final NBTTagCompound[] stacks = stacks(id);
            final long started = System.nanoTime();
            for (final NBTTagCompound stack : stacks) {
                if (hook) {
                    LegacyRewrites.rewriteStack(stack);
                }
                sink += new ItemStack(stack).getCount();
            }
            final long took = System.nanoTime() - started;
            if (round >= 0) {
                perStack[round] = (double) took / STACKS;
            }
        }
        LegacyRewrites.stopWorld();
        Arrays.sort(perStack);
        return perStack[ROUNDS / 2];
    }

    /**
     * A fresh id string in every stack, as a world read from disk has, so that its hash is not already cached.
     */
    private static NBTTagCompound[] stacks(final String id) {
        final NBTTagCompound[] stacks = new NBTTagCompound[STACKS];
        for (int i = 0; i < STACKS; i++) {
            final NBTTagCompound stack = new NBTTagCompound();
            stack.setString("id", new String(id.toCharArray()));
            stack.setByte("Count", (byte) 1);
            stack.setShort("Damage", (short) 4);
            stacks[i] = stack;
        }
        return stacks;
    }
}
