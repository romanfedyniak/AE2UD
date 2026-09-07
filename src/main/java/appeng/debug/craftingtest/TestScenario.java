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
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


/**
 * One thing to ask the planner, and everything the network needs to look like while it is asked.
 * <p>
 * A scenario carries no expected answer. What is right is whatever the planner said last time it was
 * measured - see {@link TestBaseline} - because the point of the rig is to notice that an answer moved, not
 * to assert numbers nobody worked out by hand.
 */
public final class TestScenario {

    private final String name;
    private final String description;
    private final List<TestPattern> patterns;
    private final Set<AEKey> emitable;
    private final KeyCounter stock;
    private final GenericStack request;
    private final CraftingMode mode;
    private final boolean execute;
    private final int timeoutTicks;

    private TestScenario(final Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.patterns = Collections.unmodifiableList(builder.patterns);
        this.emitable = Collections.unmodifiableSet(builder.emitable);
        this.stock = builder.stock;
        this.request = builder.request;
        this.mode = builder.mode;
        this.execute = builder.execute;
        this.timeoutTicks = builder.timeoutTicks;
    }

    public static Builder named(final String name) {
        return new Builder(name);
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public List<TestPattern> getPatterns() {
        return this.patterns;
    }

    public Set<AEKey> getEmitable() {
        return this.emitable;
    }

    public KeyCounter getStock() {
        return this.stock;
    }

    public GenericStack getRequest() {
        return this.request;
    }

    public CraftingMode getMode() {
        return this.mode;
    }

    /**
     * Whether the finished plan is also handed to a crafting cpu and watched until it is done. Off by
     * default: most scenarios are about what the planner decided, and running them would only measure the
     * rig's own medium.
     */
    public boolean isExecuted() {
        return this.execute;
    }

    /**
     * How long the run is given before it is called a timeout. A timeout is a result like any other and is
     * recorded as one - several of these scenarios exist precisely because the old planner never finishes
     * them.
     */
    public int getTimeoutTicks() {
        return this.timeoutTicks;
    }

    public static final class Builder {

        private static final int DEFAULT_TIMEOUT_TICKS = 600;

        private final String name;
        private final List<TestPattern> patterns = new ArrayList<>();
        private final Set<AEKey> emitable = new LinkedHashSet<>();
        private final KeyCounter stock = new KeyCounter();
        private String description = "";
        private GenericStack request;
        private CraftingMode mode = CraftingMode.STANDARD;
        private boolean execute;
        private int timeoutTicks = DEFAULT_TIMEOUT_TICKS;

        private Builder(final String name) {
            this.name = name;
        }

        public Builder describedAs(final String description) {
            this.description = description;
            return this;
        }

        public Builder pattern(final String name, final GenericStack[] inputs, final GenericStack[] outputs) {
            return this.pattern(name, inputs, outputs, 0);
        }

        public Builder pattern(final String name, final GenericStack[] inputs, final GenericStack[] outputs,
                final int priority) {
            this.patterns.add(new TestPattern(name, inputs, outputs, priority));
            return this;
        }

        public Builder emitable(final AEKey what) {
            this.emitable.add(what);
            return this;
        }

        public Builder inStorage(final GenericStack stack) {
            this.stock.add(stack.what(), stack.amount());
            return this;
        }

        public Builder request(final GenericStack request) {
            this.request = request;
            return this;
        }

        public Builder mode(final CraftingMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder executed() {
            this.execute = true;
            return this;
        }

        public Builder timeout(final int ticks) {
            this.timeoutTicks = ticks;
            return this;
        }

        public TestScenario build() {
            if (this.request == null) {
                throw new IllegalStateException("Scenario " + this.name + " asks for nothing");
            }

            return new TestScenario(this);
        }
    }
}
