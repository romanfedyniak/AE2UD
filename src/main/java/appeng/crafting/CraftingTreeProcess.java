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

package appeng.crafting;


import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AEConfig;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.google.common.collect.ImmutableCollection;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Map.Entry;


public class CraftingTreeProcess {
    private final CraftingTreeNode parent;
    final ICraftingPatternDetails details;
    private final CraftingJob job;
    private final Object2LongArrayMap<CraftingTreeNode> nodes = new Object2LongArrayMap<>();
    private final int depth;
    private final ICraftingGrid cc;
    private final World world;
    boolean possible = true;
    private long crafts = 0;
    private long bytes = 0;
    private ArrayList<GenericStack> containers;

    public CraftingTreeProcess(final ICraftingGrid cc, final CraftingJob job, final ICraftingPatternDetails details, final CraftingTreeNode craftingTreeNode, final int depth) {
        this.parent = craftingTreeNode;
        this.details = details;
        this.job = job;
        this.depth = depth;
        this.cc = cc;
        this.world = job.getWorld();
    }

    public void addProcess() {
        if (!nodes.isEmpty()) {
            return;
        }

        final GenericStack[] list = details.getInputs();

        // this is minor different then below, this slot uses the pattern, but kinda fudges it.
        for (GenericStack part : details.getCondensedInputs()) {
            if (part == null) {
                continue;
            }
            for (int x = 0; x < list.length; x++) {
                final GenericStack comparePart = list[x];
                // NOTE: compare by identity only (this.what()), not GenericStack.equals() - the
                // condensed part carries the *total* amount across every slot using this item, while
                // list[x] carries just that one slot's amount, so the two amounts routinely differ
                // even when it's "the same input".
                if (comparePart != null && part.what().equals(comparePart.what())) {
                    boolean isPartContainer = false;
                    AEKey partKey = part.what();

                    if (partKey instanceof AEItemKey partItemKey && partItemKey.getItem().hasContainerItem(partItemKey.getReadOnlyStack())) {
                        part = list[x];
                        partKey = part.what();
                        isPartContainer = true;
                    }

                    long wantedSize = part.amount();

                    // A slot the network fills in for has exactly one source and is not subject to the
                    // substitution config, which is off by default. Planning it as the encoded container
                    // instead would reserve buckets and leave the CPU waiting on water it never asked for -
                    // a job that hangs forever with a full CPU and nothing in the log.
                    if (details.isContainerFabricated(x)) {
                        final GenericStack supplied = details.getSubstituteInputs(x).get(0);
                        this.nodes.put(new CraftingTreeNode(cc, job, supplied.what(), this, x, depth + 1),
                                wantedSize * supplied.amount());
                        if (isPartContainer) {
                            continue;
                        }
                        break;
                    }

                    if (AEConfig.instance().getEnableCraftingSubstitutes()) {
                        long remaining;
                        long requestAmount;

                        if (details.canSubstitute()) {
                            for (final GenericStack subs : details.getSubstituteInputs(x)) {
                                remaining = job.checkAvailable(subs.what(), subs.amount());

                                if (remaining > 0) {
                                    if (remaining >= wantedSize) {
                                        requestAmount = wantedSize;
                                        wantedSize = 0;
                                        //we have the items
                                    } else {
                                        requestAmount = remaining;
                                        wantedSize -= remaining;
                                    }
                                    final CraftingTreeNode node = new CraftingTreeNode(cc, job, subs.what(), this, x, depth + 1);
                                    this.nodes.put(node, requestAmount);
                                    if (wantedSize == 0) {
                                        break;
                                    }
                                }
                            }
                        } else {
                            remaining = job.checkAvailable(partKey, wantedSize);

                            if (remaining > 0) {
                                if (remaining >= wantedSize) {
                                    requestAmount = wantedSize;
                                    wantedSize = 0;
                                    //we have the items
                                } else {
                                    requestAmount = remaining;
                                    wantedSize -= remaining;
                                }
                                this.nodes.put(new CraftingTreeNode(cc, job, partKey, this, x, depth + 1), requestAmount);
                            }
                        }
                        if (wantedSize > 0) {
                            if (details.canSubstitute() && cc.getCraftingFor(partKey, details, x, world).isEmpty()) {
                                //try to order the crafting of a substitute
                                ICraftingPatternDetails prioritizedPattern = null;
                                AEKey prioritizedKey = null;
                                for (final GenericStack subs : details.getSubstituteInputs(x)) {
                                    final ImmutableCollection<ICraftingPatternDetails> detailCollection = cc.getCraftingFor(subs.what(), details, x, world);

                                    for (final ICraftingPatternDetails sp : detailCollection) {
                                        if (prioritizedPattern == null) {
                                            prioritizedPattern = sp;
                                            prioritizedKey = subs.what();
                                        } else {
                                            if (sp.getPriority() > prioritizedPattern.getPriority()) {
                                                prioritizedPattern = sp;
                                            }
                                        }
                                    }
                                    if (prioritizedKey != null) {
                                        final CraftingTreeNode node = new CraftingTreeNode(cc, job, subs.what(), this, x, depth + 1);
                                        this.nodes.put(node, wantedSize);
                                        wantedSize = 0;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (wantedSize > 0) {
                        // use the first slot...
                        this.nodes.put(new CraftingTreeNode(cc, job, partKey, this, x, depth + 1), wantedSize);
                        wantedSize = 0;
                    }
                    if (!isPartContainer && wantedSize == 0) {
                        break;
                    }
                }
            }
        }
    }

    boolean notRecursive(ICraftingPatternDetails details) {
        return this.parent == null || this.parent.notRecursive(details);
    }

    long getTimes(final long remaining, final long stackSize) {
        for (final GenericStack part : details.getCondensedOutputs()) {
            for (final GenericStack o : details.getCondensedInputs()) {
                if (part.what().equals(o.what()) || this.returnsContainer(o.what())) {
                    return 1;
                }
            }
        }
        return (remaining / stackSize) + (remaining % stackSize != 0 ? 1 : 0);
    }

    /**
     * Planning one craft at a time is what keeps a returned container available for the next one, since
     * {@link #request} puts the whole batch's containers back only at the end. A slot the network fills in
     * for returns nothing, so it is not a reason to give up batching - but the same item may sit in another
     * slot that does return, and one such slot is enough.
     */
    private boolean returnsContainer(final AEKey what) {
        if (!(what instanceof AEItemKey itemKey) || !itemKey.getItem().hasContainerItem(itemKey.getReadOnlyStack())) {
            return false;
        }

        final GenericStack[] sparse = this.details.getInputs();

        for (int x = 0; x < sparse.length; x++) {
            if (sparse[x] != null && sparse[x].what().equals(what) && !this.details.isContainerFabricated(x)) {
                return true;
            }
        }

        return false;
    }

    void request(final MECraftingInventory inv, final long amountOfTimes, final IActionSource src) throws CraftBranchFailure, InterruptedException {
        addProcess();
        this.job.handlePausing();

        // request and remove inputs...
        for (final Entry<CraftingTreeNode, Long> entry : this.nodes.object2LongEntrySet()) {
            entry.getKey().request(inv, entry.getValue() * amountOfTimes, src);
        }

        if (this.containers != null) {
            for (final GenericStack container : this.containers) {
                inv.insert(container.what(), container.amount(), Actionable.MODULATE, src);
            }
            containers = null;
        }
        // assume its possible.

        // add crafting results..
        for (final GenericStack out : this.details.getCondensedOutputs()) {
            if (out == null) {
                continue;
            }
            inv.insert(out.what(), out.amount() * amountOfTimes, Actionable.MODULATE, src);
        }
        this.crafts += amountOfTimes;
    }

    public void addContainers(GenericStack container) {
        if (this.containers == null) {
            this.containers = new ArrayList<>();
        }
        this.containers.add(container);
    }

    void dive(final CraftingJob job) {
        final GenericStack amountCrafted = this.getAmountCrafted(this.parent.what);
        job.addTask(amountCrafted.what(), amountCrafted.amount() * this.crafts, this.details, this.depth);

        for (final Entry<CraftingTreeNode, Long> entry : this.nodes.object2LongEntrySet()) {
            entry.getKey().dive(job);
        }

        job.addBytes(this.crafts * 8 + this.bytes);
    }

    /**
     * Was {@code IAEItemStack getAmountCrafted(IAEItemStack)}. Finds which of this pattern's
     * condensed outputs matches {@code what2} (exact identity first, then item+damageable fuzzy), and
     * returns that output's key paired with its per-craft amount. Note the exact-match branch keeps
     * {@code what2}'s own identity (matching old behaviour: {@code what2.copy()}), while the fuzzy
     * branch takes the matched output's identity instead (old: {@code is.copy()}) - the two branches
     * are intentionally asymmetric.
     */
    GenericStack getAmountCrafted(AEKey what2) {
        for (final GenericStack is : this.details.getCondensedOutputs()) {
            if (is != null && is.what().equals(what2)) {
                return new GenericStack(what2, is.amount());
            }
        }

        // more fuzzy!
        for (final GenericStack is : this.details.getCondensedOutputs()) {
            if (is != null && is.what() instanceof AEItemKey isItemKey && what2 instanceof AEItemKey what2Key
                    && isItemKey.getItem() == what2Key.getItem()
                    && (isItemKey.getItem().isDamageable() || isItemKey.getDamage() == what2Key.getDamage())) {
                return new GenericStack(is.what(), is.amount());
            }
        }

        throw new IllegalStateException("Crafting Tree construction failed.");
    }

    void setSimulate() {
        this.crafts = 0;
        this.bytes = 0;

        for (final Entry<CraftingTreeNode, Long> entry : this.nodes.object2LongEntrySet()) {
            entry.getKey().setSimulate();
        }
    }

    void setJob(final MECraftingInventory storage, final CraftingCPUCluster craftingCPUCluster, final IActionSource src) throws CraftBranchFailure {
        craftingCPUCluster.addCrafting(this.details, this.crafts);

        for (final Entry<CraftingTreeNode, Long> entry : this.nodes.object2LongEntrySet()) {
            entry.getKey().setJob(storage, craftingCPUCluster, src);
        }
    }

    void getPlan(final KeyCounter used, final KeyCounter requestable) {
        for (final GenericStack i : this.details.getOutputs()) {
            if (i == null) {
                continue;
            }
            requestable.add(i.what(), i.amount() * this.crafts);
        }

        for (final Entry<CraftingTreeNode, Long> entry : this.nodes.object2LongEntrySet()) {
            entry.getKey().getPlan(used, requestable);
        }
    }
}
