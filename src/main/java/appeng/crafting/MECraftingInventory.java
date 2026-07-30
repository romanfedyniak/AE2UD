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
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInformPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import java.io.IOException;


/**
 * A crafting-local, layered view over a {@link MEStorage}: reads fall through to the wrapped
 * {@code target} (a snapshot taken at construction time), while writes stay local until {@link
 * #commit(IActionSource)} pushes them back out. This replaces the old {@code IMEInventory<IAEItemStack>}
 * implementation of the same class; the two source-typed constructors it used to have
 * ({@code IMEInventory<IAEItemStack>} and {@code IMEMonitor<IAEItemStack>} + {@code IActionSource})
 * collapse into one {@link MEStorage}-based constructor now that both source interfaces are gone
 * (see CONTRACT.md §9).
 */
public class MECraftingInventory implements MEStorage {

    private final MEStorage target;
    private final KeyCounter localCache;

    private final boolean logExtracted;
    private final KeyCounter extractedCache;

    private final boolean logInjections;
    private final KeyCounter injectedCache;

    private final boolean logMissing;
    private final KeyCounter missingCache;

    /**
     * An empty, unbacked inventory. {@link #commit(IActionSource)} is a no-op success on one of these,
     * mirroring the old no-arg constructor (used as the initial/idle state of a crafting CPU's job
     * inventory before a job is running).
     */
    public MECraftingInventory() {
        this.target = null;
        this.localCache = new KeyCounter();
        this.logExtracted = false;
        this.logInjections = false;
        this.logMissing = false;
        this.extractedCache = null;
        this.injectedCache = null;
        this.missingCache = null;
    }

    /**
     * The general constructor. Replaces both old source-typed constructors
     * ({@code IMEInventory<IAEItemStack>, boolean, boolean, boolean} and
     * {@code IMEMonitor<IAEItemStack>, IActionSource, boolean, boolean, boolean}) — both source types
     * collapsed into {@link MEStorage}, and the extra {@code IActionSource} disappeared since it was
     * only ever used to simulate-extract a snapshot, which {@link MEStorage#getAvailableStacks()}
     * already gives us directly.
     */
    public MECraftingInventory(final MEStorage target, final boolean logExtracted, final boolean logInjections, final boolean logMissing) {
        this.target = target;
        this.logExtracted = logExtracted;
        this.logInjections = logInjections;
        this.logMissing = logMissing;

        this.missingCache = logMissing ? new KeyCounter() : null;
        this.extractedCache = logExtracted ? new KeyCounter() : null;
        this.injectedCache = logInjections ? new KeyCounter() : null;

        this.localCache = target.getAvailableStacks();
    }

    /**
     * Was {@code MECraftingInventory(IItemList<IAEItemStack>)}. Builds a detached, uncommittable
     * inventory (like the no-arg constructor) seeded from an existing snapshot.
     */
    public MECraftingInventory(final KeyCounter counter) {
        this.target = null;
        this.localCache = new KeyCounter();
        this.localCache.addAll(counter);
        this.logExtracted = false;
        this.logInjections = false;
        this.logMissing = false;
        this.missingCache = null;
        this.extractedCache = null;
        this.injectedCache = null;
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (what == null || amount <= 0) {
            return 0;
        }

        if (mode == Actionable.MODULATE) {
            if (this.logInjections) {
                this.injectedCache.add(what, amount);
            }
            this.localCache.add(what, amount);
        }

        return amount;
    }

    @Override
    public long extract(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (what == null || amount <= 0) {
            return 0;
        }

        final long available = this.localCache.get(what);
        if (available <= 0) {
            return 0;
        }

        final long extracted = Math.min(available, amount);

        if (mode == Actionable.MODULATE) {
            this.localCache.remove(what, extracted);
            if (this.logExtracted) {
                this.extractedCache.add(what, extracted);
            }
        }

        return extracted;
    }

    @Override
    public void getAvailableStacks(final KeyCounter out) {
        for (final var entry : this.localCache) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    public KeyCounter getItemList() {
        return this.localCache;
    }

    /**
     * Pushes everything logged in {@link #injectedCache}/{@link #extractedCache} back onto
     * {@link #target}, rolling back on any partial failure. Mirrors the old {@code commit} exactly,
     * translated from stack-mutation to plain {@code long} bookkeeping.
     */
    public boolean commit(final IActionSource src) {
        if (this.target == null) {
            return true;
        }

        final KeyCounter added = new KeyCounter();
        final KeyCounter pulled = new KeyCounter();
        boolean failed = false;

        if (this.logInjections) {
            for (final var entry : this.injectedCache) {
                final AEKey what = entry.getKey();
                final long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }

                final long inserted = this.target.insert(what, amount, Actionable.MODULATE, src);
                added.add(what, inserted);

                if (inserted != amount) {
                    failed = true;
                    break;
                }
            }
        }

        if (failed) {
            for (final var entry : added) {
                this.target.extract(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, src);
            }

            return false;
        }

        if (this.logExtracted) {
            for (final var entry : this.extractedCache) {
                final AEKey what = entry.getKey();
                final long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }

                final long extracted = this.target.extract(what, amount, Actionable.MODULATE, src);
                pulled.add(what, extracted);

                if (extracted != amount) {
                    if (src.player().isPresent()) {
                        try {
                            if (extracted <= 0) {
                                NetworkHandler.instance().sendTo(new PacketInformPlayer(new GenericStack(what, amount), null, PacketInformPlayer.InfoType.NO_ITEMS_EXTRACTED), (EntityPlayerMP) src.player().get());
                            } else {
                                NetworkHandler.instance().sendTo(new PacketInformPlayer(new GenericStack(what, amount), new GenericStack(what, extracted), PacketInformPlayer.InfoType.PARTIAL_ITEM_EXTRACTION), (EntityPlayerMP) src.player().get());
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    failed = true;
                }
            }
        }

        if (failed) {
            for (final var entry : added) {
                this.target.extract(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, src);
            }

            for (final var entry : pulled) {
                this.target.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, src);
            }

            return false;
        }

        return true;
    }

    /**
     * Zeroes out {@code what} in the local cache without removing the key, exactly like the old
     * {@code list.setStackSize(0)} did. Used by {@link CraftingJob} to make sure the job's own output
     * doesn't get "found" as already-available in its own simulation inventory.
     */
    void ignore(final AEKey what) {
        this.localCache.set(what, 0);
    }
}
