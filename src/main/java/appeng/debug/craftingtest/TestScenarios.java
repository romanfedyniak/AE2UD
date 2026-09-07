/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.debug.craftingtest;


import appeng.api.config.CraftingMode;
import appeng.api.stacks.GenericStack;

import java.util.ArrayList;
import java.util.List;


/**
 * The questions worth asking the planner.
 * <p>
 * Every scenario is built every time, in this order, whichever ones are then run - so the keys they share
 * are the same keys no matter what was asked for, and a single scenario can be re-run on its own and still
 * be compared against a baseline recorded with all of them.
 */
public final class TestScenarios {

    private TestScenarios() {
    }

    public static List<TestScenario> build(final TestKeys k) {
        final List<TestScenario> scenarios = new ArrayList<>();

        scenarios.add(TestScenario.named("chain")
                .describedAs("one pattern per step, nothing shared")
                .pattern("b", in(k.stack("chain_a", 1)), out(k.stack("chain_b", 1)))
                .pattern("c", in(k.stack("chain_b", 1)), out(k.stack("chain_c", 1)))
                .pattern("t", in(k.stack("chain_c", 1)), out(k.stack("chain_t", 1)))
                .inStorage(k.stack("chain_a", 4096))
                .request(k.stack("chain_t", 64))
                .build());

        scenarios.add(TestScenario.named("diamond")
                .describedAs("two branches meeting on one leaf, which is counted twice by a tree")
                .pattern("x", in(k.stack("dia_base", 2)), out(k.stack("dia_x", 1)))
                .pattern("y", in(k.stack("dia_base", 3)), out(k.stack("dia_y", 1)))
                .pattern("t", in(k.stack("dia_x", 1), k.stack("dia_y", 1)), out(k.stack("dia_t", 1)))
                .inStorage(k.stack("dia_base", 100000))
                .request(k.stack("dia_t", 100))
                .build());

        scenarios.add(TestScenario.named("spill")
                .describedAs("the preferred pattern runs out of its ingredient and the rest goes to the other")
                .pattern("fast", in(k.stack("spill_dust", 1)), out(k.stack("spill_t", 1)), 10)
                .pattern("slow", in(k.stack("spill_ore", 1)), out(k.stack("spill_t", 1)), 0)
                .inStorage(k.stack("spill_dust", 250))
                .inStorage(k.stack("spill_ore", 1000))
                .request(k.stack("spill_t", 400))
                .build());

        scenarios.add(TestScenario.named("spill_short")
                .describedAs("both patterns run out - the plan the confirmation screen shows when it cannot be made")
                .pattern("fast", in(k.stack("short_dust", 1)), out(k.stack("short_t", 1)), 10)
                .pattern("slow", in(k.stack("short_ore", 1)), out(k.stack("short_t", 1)), 0)
                .inStorage(k.stack("short_dust", 250))
                .inStorage(k.stack("short_ore", 100))
                .request(k.stack("short_t", 400))
                .build());

        scenarios.add(TestScenario.named("spill_same_priority")
                .describedAs("the same again with both patterns left at the default priority")
                .pattern("first", in(k.stack("same_dust", 1)), out(k.stack("same_t", 1)))
                .pattern("second", in(k.stack("same_ore", 1)), out(k.stack("same_t", 1)))
                .inStorage(k.stack("same_dust", 250))
                .inStorage(k.stack("same_ore", 1000))
                .request(k.stack("same_t", 400))
                .build());

        scenarios.add(TestScenario.named("missing")
                .describedAs("nothing in storage and no pattern for the leaf")
                .pattern("t", in(k.stack("miss_base", 1)), out(k.stack("miss_t", 1)))
                .request(k.stack("miss_t", 10))
                .build());

        scenarios.add(TestScenario.named("force_missing")
                .describedAs("the same request as a forced start, which promises the cpu what is lacking")
                .pattern("t", in(k.stack("force_base", 1)), out(k.stack("force_t", 1)))
                .request(k.stack("force_t", 10))
                .mode(CraftingMode.IGNORE_MISSING)
                .build());

        scenarios.add(TestScenario.named("emitter")
                .describedAs("a key nothing crafts, promised by a level emitter")
                .emitable(k.key("emit_t"))
                .request(k.stack("emit_t", 10))
                .build());

        scenarios.add(TestScenario.named("byproduct")
                .describedAs("one pattern makes both ingredients of the next")
                .pattern("ab", in(k.stack("by_base", 1)), out(k.stack("by_a", 1), k.stack("by_b", 1)))
                .pattern("t", in(k.stack("by_a", 1), k.stack("by_b", 1)), out(k.stack("by_t", 1)))
                .inStorage(k.stack("by_base", 1000))
                .request(k.stack("by_t", 10))
                .build());

        scenarios.add(TestScenario.named("recursive")
                .describedAs("a pattern that eats its own output and makes more of it than it ate")
                .pattern("t", in(k.stack("rec_t", 1), k.stack("rec_base", 1)), out(k.stack("rec_t", 2)))
                .inStorage(k.stack("rec_t", 1))
                .inStorage(k.stack("rec_base", 1000))
                .request(k.stack("rec_t", 100))
                .build());

        scenarios.add(TestScenario.named("ring")
                .describedAs("two patterns that make each other, and grow round the loop")
                .pattern("a", in(k.stack("ring_b", 1)), out(k.stack("ring_a", 2)))
                .pattern("b", in(k.stack("ring_a", 1), k.stack("ring_base", 1)), out(k.stack("ring_b", 1)))
                .inStorage(k.stack("ring_b", 1))
                .inStorage(k.stack("ring_base", 1000))
                .request(k.stack("ring_a", 100))
                .build());

        scenarios.add(deep(k, 40));
        scenarios.add(wide(k, 200));
        scenarios.add(batch(k, "batch_small", 1));
        scenarios.add(batch(k, "batch_large", 20000));

        scenarios.add(TestScenario.named("executed")
                .describedAs("a plan handed to a crafting cpu and watched until it is finished")
                .pattern("b", in(k.stack("exec_a", 1)), out(k.stack("exec_b", 1)))
                .pattern("t", in(k.stack("exec_b", 1)), out(k.stack("exec_t", 1)))
                .inStorage(k.stack("exec_a", 8))
                .request(k.stack("exec_t", 4))
                .executed()
                .build());

        return scenarios;
    }

    /**
     * A single chain, deep enough that the depth is the point. Kept short of what blows the stack: the old
     * planner recurses, and a scenario nobody can record a baseline for is no use as a baseline.
     */
    private static TestScenario deep(final TestKeys k, final int depth) {
        final TestScenario.Builder builder = TestScenario.named("deep")
                .describedAs("a chain " + depth + " patterns long");

        for (int i = 1; i <= depth; i++) {
            builder.pattern("step" + i, in(k.stack("deep_" + (i - 1), 1)), out(k.stack("deep_" + i, 1)));
        }

        return builder.inStorage(k.stack("deep_0", 4096))
                .request(k.stack("deep_" + depth, 16))
                .build();
    }

    /**
     * One output over many independent branches - the shape a modpack's late-game recipe really has, and the
     * one where the number of patterns rather than the amount decides the cost.
     */
    private static TestScenario wide(final TestKeys k, final int branches) {
        final TestScenario.Builder builder = TestScenario.named("wide")
                .describedAs(branches + " independent two-step branches feeding one output");

        final GenericStack[] inputs = new GenericStack[branches];

        for (int i = 0; i < branches; i++) {
            builder.pattern("mid" + i, in(k.stack("wide_raw" + i, 1)), out(k.stack("wide_mid" + i, 1)));
            inputs[i] = k.stack("wide_mid" + i, 1);
            builder.inStorage(k.stack("wide_raw" + i, 64));
        }

        return builder.pattern("t", inputs, out(k.stack("wide_t", 1)))
                .request(k.stack("wide_t", 8))
                .build();
    }

    /**
     * The same question at two sizes. A planner that works on patterns rather than on items answers both in
     * the same time; one that works item by item does not, and the pair is what says so.
     */
    private static TestScenario batch(final TestKeys k, final String name, final long amount) {
        return TestScenario.named(name)
                .describedAs("the same two patterns asked for " + amount)
                .pattern("fast", in(k.stack("batch_dust", 1)), out(k.stack("batch_t", 1)), 10)
                .pattern("slow", in(k.stack("batch_ore", 1)), out(k.stack("batch_t", 1)), 0)
                .inStorage(k.stack("batch_dust", 10))
                .inStorage(k.stack("batch_ore", 1000000))
                .request(k.stack("batch_t", amount))
                .build();
    }

    private static GenericStack[] in(final GenericStack... stacks) {
        return stacks;
    }

    private static GenericStack[] out(final GenericStack... stacks) {
        return stacks;
    }
}
