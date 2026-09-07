/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.debug;


import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.core.AELog;
import appeng.debug.craftingtest.TestPattern;
import appeng.debug.craftingtest.TestRunner;
import appeng.debug.craftingtest.TestStorage;
import appeng.me.helpers.MachineSource;
import appeng.tile.grid.AENetworkTile;
import appeng.util.Platform;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.util.ITickable;

import javax.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


/**
 * A whole ME network in one block, so a crafting test does not begin with an hour of building one.
 * <p>
 * It is the network's storage, the source of its power, and the only machine on it - a machine that accepts
 * every pattern and really performs it, returning the outputs a tick later. Put a crafting cpu beside it and
 * {@code /ae2 craftingtest} has everything it needs.
 * <p>
 * Nothing here is meant to be balanced or persistent: the patterns and the stock belong to whichever
 * scenario is running, and both are thrown away when the next one starts.
 */
public class TileCraftingTestRig extends AENetworkTile implements ITickable, ICraftingProvider,
        IStorageProvider, IAEPowerStorage {

    /**
     * How many pushed patterns are held at once before {@link #isBusy()} starts refusing. Something has to
     * bound it, and a real machine does; one is what makes an execution run's tick count mean anything.
     */
    private static final int PARALLEL_CRAFTS = 1;

    private final TestStorage storage = new TestStorage();
    private final List<TestPattern> patterns = new ArrayList<>();
    private final Set<AEKey> emitable = new LinkedHashSet<>();
    private final Deque<GenericStack[]> pending = new ArrayDeque<>();
    private final MachineSource source = new MachineSource(this);

    @Nullable
    private TestRunner runner;

    public TileCraftingTestRig() {
        // No channel and no idle draw on purpose: a rig is meant to be dropped next to a crafting cpu and
        // work, without a controller and without a scenario ever failing for want of power.
        this.getProxy().setIdlePowerUsage(0);
    }

    @Override
    public AECableType getCableConnectionType(final AEPartLocation dir) {
        return AECableType.SMART;
    }

    public TestStorage getStorage() {
        return this.storage;
    }

    /**
     * Replaces everything the network can make and everything it holds, and tells the crafting cache to look
     * again. The cache rebuilds on its own tick, so a caller must let one pass before asking for a job.
     */
    public void loadScenario(final List<TestPattern> patterns, final Set<AEKey> emitable,
            final KeyCounter stock) {
        this.patterns.clear();
        this.patterns.addAll(patterns);
        this.emitable.clear();
        this.emitable.addAll(emitable);
        this.pending.clear();
        this.storage.reset(stock);

        this.postPatternChange();
        IStorageProvider.requestUpdate(this.getProxy().getNode());
    }

    public void setRunner(@Nullable final TestRunner runner) {
        this.runner = runner;
    }

    @Nullable
    public TestRunner getRunner() {
        return this.runner;
    }

    private void postPatternChange() {
        try {
            this.getProxy().getGrid().postEvent(
                    new MENetworkCraftingPatternChange(this, this.getProxy().getNode()));
        } catch (final Exception e) {
            AELog.debug(e);
        }
    }

    // --- the network's only machine ---

    @Override
    public void provideCrafting(final ICraftingProviderHelper craftingTracker) {
        for (final TestPattern pattern : this.patterns) {
            craftingTracker.addCraftingOption(this, pattern);
        }

        for (final AEKey what : this.emitable) {
            craftingTracker.setEmitable(what);
        }
    }

    @Override
    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table) {
        return this.pushPattern(patternDetails, table, new GenericStack[0]);
    }

    @Override
    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table,
            final GenericStack[] extraInputs) {
        if (this.isBusy()) {
            return false;
        }

        // The cpu has already taken the ingredients out; all that is owed is the result. What arrived in the
        // table is deliberately not checked - a scenario that wants the ingredients verified verifies them
        // through the plan, where a wrong answer is legible.
        this.pending.add(patternDetails.getCondensedOutputs());
        return true;
    }

    @Override
    public boolean isBusy() {
        return this.pending.size() >= PARALLEL_CRAFTS;
    }

    // --- the network's storage and power ---

    @Override
    public void mountInventories(final IStorageMounts storageMounts) {
        storageMounts.mount(this.storage);
    }

    @Override
    public double injectAEPower(final double amt, final Actionable mode) {
        return 0;
    }

    @Override
    public double getAEMaxPower() {
        return Long.MAX_VALUE / 10000;
    }

    @Override
    public double getAECurrentPower() {
        return Long.MAX_VALUE / 10000;
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        return true;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public double extractAEPower(final double amt, final Actionable mode, final PowerMultiplier pm) {
        return amt;
    }

    // --- ticking ---

    @Override
    public void update() {
        if (this.world == null || !Platform.isServer()) {
            return;
        }

        this.deliverFinishedCrafts();

        if (this.runner != null && !this.runner.tick()) {
            this.runner = null;
        }
    }

    /**
     * Hands last tick's results back to the network. Inserted through the network rather than straight into
     * {@link #storage}, because that is the path a waiting crafting cpu is listening on.
     */
    private void deliverFinishedCrafts() {
        if (this.pending.isEmpty()) {
            return;
        }

        try {
            final var inventory = this.getProxy().getStorage().getInventory();

            while (!this.pending.isEmpty()) {
                for (final GenericStack out : this.pending.poll()) {
                    if (out != null) {
                        inventory.insert(out.what(), out.amount(), Actionable.MODULATE, this.source);
                    }
                }
            }
        } catch (final Exception e) {
            AELog.debug(e);
        }
    }
}
