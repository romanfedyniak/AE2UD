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

package appeng.helpers;


import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.util.InventoryAdaptor;
import com.google.common.collect.ImmutableSet;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;


public class MultiCraftingTracker {

    private final int size;
    private final ICraftingRequester owner;
    /** What the machine orders at. Read at each submit rather than kept, so the setting takes at once. */
    private final ICraftPriorityTarget priority;

    private Future<ICraftingJob>[] jobs = null;
    private ICraftingLink[] links = null;

    public MultiCraftingTracker(final ICraftingRequester o, final int size, final ICraftPriorityTarget priority) {
        this.owner = o;
        this.size = size;
        this.priority = priority;
    }

    public void readFromNBT(final NBTTagCompound extra) {
        for (int x = 0; x < this.size; x++) {
            final NBTTagCompound link = extra.getCompoundTag("links-" + x);

            if (link != null && !link.isEmpty()) {
                this.setLink(x, AEApi.instance().storage().loadCraftingLink(link, this.owner));
            }
        }
    }

    public void writeToNBT(final NBTTagCompound extra) {
        for (int x = 0; x < this.size; x++) {
            final ICraftingLink link = this.getLink(x);

            if (link != null) {
                final NBTTagCompound ln = new NBTTagCompound();
                link.writeToNBT(ln);
                extra.setTag("links-" + x, ln);
            }
        }
    }

    /** For a destination that is an inventory: it takes the result only if the whole stack fits. */
    public boolean handleCrafting(final int x, final long itemToCraft, final AEKey what, final InventoryAdaptor d, final World w, final IGrid g, final ICraftingGrid cg, final IActionSource mySrc) {
        if (!(what instanceof AEItemKey itemKey)) {
            return false;
        }

        return this.handleCrafting(x, itemToCraft, what,
                () -> d.simulateAdd(itemKey.toStack((int) Math.min(itemToCraft, Integer.MAX_VALUE))).isEmpty(),
                w, g, cg, mySrc);
    }

    /**
     * @param destinationAccepts whether whatever ordered this can still receive the result. Asked only when a
     * job is about to be started or submitted, since answering it can be as dear as simulating the delivery.
     */
    public boolean handleCrafting(final int x, final long itemToCraft, final AEKey what, final BooleanSupplier destinationAccepts, final World w, final IGrid g, final ICraftingGrid cg, final IActionSource mySrc) {
        if (this.getLink(x) != null) {
            return false;
        }

        final Future<ICraftingJob> craftingJob = this.getJob(x);

        if (craftingJob == null) {
            if (destinationAccepts.getAsBoolean()) {
                this.setJob(x, cg.beginCraftingJob(w, g, mySrc, new GenericStack(what, itemToCraft), null));
            }
            return false;
        }

        try {
            if (!craftingJob.isDone()) {
                return false;
            }

            final ICraftingJob job = craftingJob.get();

            if (job != null && destinationAccepts.getAsBoolean()) {
                final ICraftingLink link = cg.submitJob(job, this.owner, null, false, mySrc,
                        this.priority.getCraftPriority()).link();

                this.setJob(x, null);

                if (link != null) {
                    this.setLink(x, link);

                    return true;
                }
            }
        } catch (final InterruptedException e) {
            // :P
        } catch (final ExecutionException e) {
            // :P
        }

        return false;
    }

    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        if (this.links == null) {
            return ImmutableSet.of();
        }

        return ImmutableSet.copyOf(new NonNullArrayIterator<>(this.links));
    }

    public void jobStateChange(final ICraftingLink link) {
        if (this.links != null) {
            for (int x = 0; x < this.links.length; x++) {
                if (this.links[x] == link) {
                    this.setLink(x, null);
                    return;
                }
            }
        }
    }

    int getSlot(final ICraftingLink link) {
        if (this.links != null) {
            for (int x = 0; x < this.links.length; x++) {
                if (this.links[x] == link) {
                    return x;
                }
            }
        }

        return -1;
    }

    void cancel() {
        if (this.links != null) {
            for (final ICraftingLink l : this.links) {
                if (l != null) {
                    l.cancel();
                }
            }

            this.links = null;
        }

        if (this.jobs != null) {
            for (final Future<ICraftingJob> l : this.jobs) {
                if (l != null) {
                    l.cancel(true);
                }
            }

            this.jobs = null;
        }
    }

    boolean isBusy(final int slot) {
        return this.getLink(slot) != null || this.getJob(slot) != null;
    }

    private ICraftingLink getLink(final int slot) {
        if (this.links == null) {
            return null;
        }

        return this.links[slot];
    }

    private void setLink(final int slot, final ICraftingLink l) {
        if (this.links == null) {
            this.links = new ICraftingLink[this.size];
        }

        this.links[slot] = l;

        boolean hasStuff = false;
        for (int x = 0; x < this.links.length; x++) {
            final ICraftingLink g = this.links[x];

            if (g == null || g.isCanceled() || g.isDone()) {
                this.links[x] = null;
            } else {
                hasStuff = true;
            }
        }

        if (!hasStuff) {
            this.links = null;
        }
    }

    private Future<ICraftingJob> getJob(final int slot) {
        if (this.jobs == null) {
            return null;
        }

        return this.jobs[slot];
    }

    private void setJob(final int slot, final Future<ICraftingJob> l) {
        if (this.jobs == null) {
            this.jobs = new Future[this.size];
        }

        this.jobs[slot] = l;

        boolean hasStuff = false;

        for (final Future<ICraftingJob> job : this.jobs) {
            if (job != null) {
                hasStuff = true;
            }
        }

        if (!hasStuff) {
            this.jobs = null;
        }
    }
}
