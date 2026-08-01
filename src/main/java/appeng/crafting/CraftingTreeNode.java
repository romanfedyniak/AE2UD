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
import appeng.api.config.FuzzyMode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInformPlayer;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class CraftingTreeNode {

    // what slot!
    private final int slot;
    private final CraftingJob job;
    private final KeyCounter used = new KeyCounter();
    // parent node.
    private final CraftingTreeProcess parent;
    private final World world;
    // what item is this? (identity only - the amount travels alongside as a plain long)
    final AEKey what;
    // what are the crafting patterns for this?
    private final ArrayList<CraftingTreeProcess> nodes = new ArrayList<>();
    private final ICraftingGrid cc;
    private final int depth;
    private int bytes = 0;
    private boolean canEmit = false;
    private long missing = 0;
    private long howManyEmitted = 0;
    private boolean exhausted = false;

    public CraftingTreeNode(final ICraftingGrid cc, final CraftingJob job, final AEKey wat, final CraftingTreeProcess par, final int slot, final int depth) {
        this.what = wat;
        this.parent = par;
        this.slot = slot;
        this.world = job.getWorld();
        this.job = job;
        this.cc = cc;
        this.depth = depth;

        this.canEmit = cc.canEmitFor(this.what);
    }

    public void addNode() {
        if (!nodes.isEmpty()) {
            return;
        }

        if (this.canEmit) {
            return; // if you can emit for something, you can't make it with patterns.
        }

        for (final ICraftingPatternDetails details : cc.getCraftingFor(this.what, this.parent == null ? null : this.parent.details, slot, this.world))// in
        // order.
        {
            if (this.parent == null || notRecursive(details) && this.parent.details != details) {
                this.nodes.add(new CraftingTreeProcess(cc, job, details, this, depth + 1));
            }
        }
    }

    /**
     * @return how much of {@code l} this invocation managed to satisfy. Nothing outside this class
     * tree actually reads the value (mirrors the old {@code IAEItemStack} return, which every caller
     * discarded too) - it only exists to short-circuit the method's own control flow.
     */
    long request(final MECraftingInventory inv, long l, final IActionSource src) throws CraftBranchFailure, InterruptedException {
        addNode();
        this.job.handlePausing();

        final KeyCounter inventoryList = inv.getItemList();
        final List<GenericStack> thingsUsed = new ArrayList<>();

        // A fabricated slot is not matched against the crafting grid: what the network hands over is a fluid,
        // which no InventoryCrafting can hold and no isValidItemForSlot would accept. It is drawn plainly,
        // like a processing pattern's ingredient, and leaves no container behind.
        if (this.getSlot() >= 0 && this.parent != null && this.parent.details.isCraftable()
                && !this.parent.details.isContainerFabricated(this.slot)) {
            final LinkedList<GenericStack> itemList = new LinkedList<>();

            final boolean damageableItem = this.what instanceof AEItemKey whatItemKey
                    && (whatItemKey.getItem().isDamageable() || Platform.isGTDamageableItem(whatItemKey.getItem()));

            if (this.parent.details.canSubstitute()) {
                for (final GenericStack subs : this.parent.details.getSubstituteInputs(this.slot)) {
                    if (damageableItem) {
                        for (final var entry : inventoryList.findFuzzy(this.what, FuzzyMode.IGNORE_ALL)) {
                            if (entry.getLongValue() > 0) {
                                itemList.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                            }
                        }
                    }

                    final long subsAmount = inventoryList.get(subs.what());
                    if (subsAmount > 0) {
                        itemList.add(new GenericStack(subs.what(), subsAmount));
                    }
                }
            } else {
                if (damageableItem) {
                    for (final var entry : inventoryList.findFuzzy(this.what, FuzzyMode.IGNORE_ALL)) {
                        if (entry.getLongValue() > 0) {
                            itemList.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                        }
                    }
                } else {
                    final long amount = inventoryList.get(this.what);
                    if (amount > 0) {
                        itemList.add(new GenericStack(this.what, amount));
                    }
                }
            }

            for (final GenericStack fuzz : itemList) {
                if (fuzz.what() instanceof AEItemKey fuzzItemKey
                        && this.parent.details.isValidItemForSlot(this.getSlot(), fuzzItemKey.getReadOnlyStack(), this.world)) {
                    final long available = inv.extract(fuzz.what(), l, Actionable.MODULATE, src);

                    if (available > 0) {
                        if (fuzzItemKey.getItem().hasContainerItem(fuzzItemKey.getReadOnlyStack())) {
                            final ItemStack is2 = Platform.getContainerItem(fuzzItemKey.toStack());
                            final GenericStack o = GenericStack.fromItemStack(is2);

                            if (o != null) {
                                this.parent.addContainers(o);
                            }
                        }

                        if (!this.exhausted) {
                            final long usedAmount = this.job.checkUse(fuzz.what(), available);

                            if (usedAmount > 0) {
                                thingsUsed.add(new GenericStack(fuzz.what(), usedAmount));
                                this.used.add(fuzz.what(), usedAmount);
                            }
                        }

                        this.bytes += available;
                        l -= available;

                        if (l == 0) {
                            return available;
                        }
                    }
                }
            }
        } else {
            final long available = inv.extract(this.what, l, Actionable.MODULATE, src);

            if (available > 0) {
                if (!this.exhausted) {
                    final long usedAmount = this.job.checkUse(this.what, available);

                    if (usedAmount > 0) {
                        thingsUsed.add(new GenericStack(this.what, usedAmount));
                        this.used.add(this.what, usedAmount);
                    }
                }

                this.bytes += available;
                l -= available;

                if (l == 0) {
                    return available;
                }
            }
        }

        if (this.canEmit) {
            this.howManyEmitted = l;
            this.bytes += l;

            return l;
        }

        this.exhausted = true;

        if (this.nodes.size() == 1) {
            final CraftingTreeProcess pro = this.nodes.get(0);

            while (pro.possible && l > 0) {
                final GenericStack madeWhat = pro.getAmountCrafted(this.what);
                pro.request(inv, pro.getTimes(l, madeWhat.amount()), src);

                final long available = inv.extract(madeWhat.what(), l, Actionable.MODULATE, src);

                if (available > 0) {
                    if (parent != null && madeWhat.what() instanceof AEItemKey madeItemKey
                            && madeItemKey.getItem().hasContainerItem(madeItemKey.getReadOnlyStack())) {
                        final ItemStack is2 = Platform.getContainerItem(madeItemKey.toStack());
                        final GenericStack o = GenericStack.fromItemStack(is2);

                        if (o != null) {
                            this.parent.addContainers(o);
                        }
                    }

                    this.bytes += available;
                    l -= available;

                    if (l <= 0) {
                        return available;
                    }
                } else {
                    pro.possible = false; // ;P
                }
            }
        } else if (this.nodes.size() > 1) {
            for (final CraftingTreeProcess pro : this.nodes) {
                try {
                    while (pro.possible && l > 0) {
                        final MECraftingInventory subInv = new MECraftingInventory(inv, true, true, true);
                        pro.request(subInv, 1, src);

                        final long available = subInv.extract(this.what, l, Actionable.MODULATE, src);

                        if (available > 0) {
                            if (!subInv.commit(src)) {
                                throw new CraftBranchFailure(this.what, l);
                            }

                            this.bytes += available;
                            l -= available;

                            if (l <= 0) {
                                return available;
                            }
                        } else {
                            pro.possible = false; // ;P
                        }
                    }
                } catch (final CraftBranchFailure fail) {
                    pro.possible = true;
                }
            }
        }

        if (job.isSimulation()) {
            this.bytes += l;
            if (parent != null && this.what instanceof AEItemKey whatItemKey
                    && whatItemKey.getItem().hasContainerItem(whatItemKey.getReadOnlyStack())) {
                final ItemStack is2 = Platform.getContainerItem(whatItemKey.toStack());
                final GenericStack o = GenericStack.fromItemStack(is2);

                if (o != null) {
                    this.parent.addContainers(o);
                }
            }
            this.missing += l;
            return l;
        }

        for (final GenericStack o : thingsUsed) {
            this.job.refund(o.what(), o.amount());
            this.used.add(o.what(), -o.amount());
        }

        throw new CraftBranchFailure(this.what, l);
    }

    boolean notRecursive(ICraftingPatternDetails details) {
        if (this.parent == null) {
            return true;
        }
        if (this.parent.details == details) {
            return false;
        }
        return this.parent.notRecursive(details);
    }

    void dive(final CraftingJob job) {
        if (this.missing > 0) {
            job.addMissing(this.what, this.missing);
        }
        // missing = 0;

        // A job is charged per thing, and for a fluid the thing is a bucket, not a millibucket. This is
        // getAmountPerUnit and deliberately not getAmountPerByte - the latter is how densely a storage cell
        // packs the type (8 items, 8000 mB) and dividing by it would make every existing job eight times
        // cheaper. Applied once here rather than at the six places above, which all add a raw amount of this
        // node's single key: no rounding drift, and no chance of five of them learning it and the sixth not.
        job.addBytes(this.bytes / Math.max(1, this.what.getType().getAmountPerUnit()));

        for (final CraftingTreeProcess pro : this.nodes) {
            pro.dive(job);
        }
    }

    void setSimulate() {
        this.missing = 0;
        this.bytes = 0;
        this.used.reset();
        this.exhausted = false;

        for (final CraftingTreeProcess pro : this.nodes) {
            pro.setSimulate();
        }
    }

    public void setJob(final MECraftingInventory storage, final CraftingCPUCluster craftingCPUCluster, final IActionSource src) throws CraftBranchFailure {
        for (final var entry : this.used) {
            final AEKey key = entry.getKey();
            final long amount = entry.getLongValue();

            if (amount <= 0) {
                continue;
            }

            final long actuallyExtracted = storage.extract(key, amount, Actionable.MODULATE, src);

            if (actuallyExtracted != amount) {
                if (src.player().isPresent()) {
                    try {
                        if (actuallyExtracted <= 0) {
                            NetworkHandler.instance().sendTo(new PacketInformPlayer(new GenericStack(key, amount), null, PacketInformPlayer.InfoType.NO_ITEMS_EXTRACTED), (EntityPlayerMP) src.player().get());
                        } else {
                            NetworkHandler.instance().sendTo(new PacketInformPlayer(new GenericStack(key, amount), new GenericStack(key, actuallyExtracted), PacketInformPlayer.InfoType.PARTIAL_ITEM_EXTRACTION), (EntityPlayerMP) src.player().get());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                throw new CraftBranchFailure(key, amount);
            }

            craftingCPUCluster.addStorage(key, actuallyExtracted);
        }

        if (this.howManyEmitted > 0) {
            craftingCPUCluster.addEmitable(this.what, this.howManyEmitted);
        }

        for (final CraftingTreeProcess pro : this.nodes) {
            pro.setJob(storage, craftingCPUCluster, src);
        }
    }

    /**
     * Was {@code getPlan(IItemList<IAEItemStack>)}. The old {@code IAEItemStack} plan entries carried
     * two independent numbers per key - {@code getStackSize()} (drawn from storage / missing) and
     * {@code getCountRequestable()} (to be produced by crafting, flagged {@code isCraftable(true)}) -
     * which a single-{@code long}-per-key {@link KeyCounter} cannot represent at once. Split into two
     * counters instead of merging them into one and losing the distinction; see
     * {@link CraftingJob#populatePlan(KeyCounter, KeyCounter, KeyCounter)}.
     */
    void getPlan(final KeyCounter used, final KeyCounter requestable, final KeyCounter craftingSteps) {
        if (this.missing > 0) {
            used.add(this.what, this.missing);
        }

        if (this.howManyEmitted > 0) {
            requestable.add(this.what, this.howManyEmitted);
        }

        for (final var entry : this.used) {
            used.add(entry.getKey(), entry.getLongValue());
        }

        for (final CraftingTreeProcess pro : this.nodes) {
            pro.getPlan(used, requestable, craftingSteps);
        }
    }

    int getSlot() {
        return this.slot;
    }
}
