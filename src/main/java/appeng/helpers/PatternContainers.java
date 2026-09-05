/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.helpers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.IPatternContainer;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.DimensionalCoord;

/**
 * Finding the things in a network that hold patterns, and deciding which of them a pattern should go to.
 *
 * <p>Everything here works from {@link IPatternContainer} alone. Nothing names a class, so a machine an
 * addon adds is found by the Pattern Access Terminal and by the pattern terminal's upload button both, on
 * the strength of implementing that one interface.</p>
 */
public final class PatternContainers {

    private PatternContainers() {
    }

    /**
     * Everything in the network that holds patterns and is willing to be listed, in no particular order.
     */
    public static List<IPatternContainer> visible(@Nullable final IGrid grid) {
        final List<IPatternContainer> containers = new ArrayList<>();

        if (grid == null) {
            return containers;
        }

        for (final Class<? extends IGridHost> machineClass : grid.getMachinesClasses()) {
            if (!IPatternContainer.class.isAssignableFrom(machineClass)) {
                continue;
            }

            for (final IGridNode node : grid.getMachines(machineClass)) {
                if (!node.isActive()) {
                    continue;
                }

                final IPatternContainer container = (IPatternContainer) node.getMachine();
                if (container.isVisibleInTerminal()) {
                    containers.add(container);
                }
            }
        }

        return containers;
    }

    /**
     * Slots that are both empty and paid for. A container may keep room it cannot use yet, and a pattern put
     * there is not somewhere it can stay - see {@link IPatternContainer#getUsablePatternSlots}.
     */
    public static int freeSlots(final IPatternContainer container) {
        final IItemHandler patterns = container.getTerminalPatternInventory();
        final int usable = usableSlots(container);

        int free = 0;
        for (int slot = 0; slot < usable; slot++) {
            if (patterns.getStackInSlot(slot).isEmpty()) {
                free++;
            }
        }

        return free;
    }

    /**
     * Puts a pattern in the first free usable slot.
     *
     * @return false if there was none, in which case nothing was moved.
     */
    public static boolean insert(final IPatternContainer container, final ItemStack pattern) {
        final IItemHandler patterns = container.getTerminalPatternInventory();
        final int usable = usableSlots(container);

        for (int slot = 0; slot < usable; slot++) {
            if (patterns.getStackInSlot(slot).isEmpty()
                    && patterns.insertItem(slot, pattern, false).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    /**
     * How many slots a container really offers: what it says it has paid for, and never more than it has.
     */
    public static int usableSlots(final IPatternContainer container) {
        return Math.min(container.getUsablePatternSlots(), container.getTerminalPatternInventory().getSlots());
    }

    /**
     * Whether this container would take this pattern right now: it would run it, it has not got it already,
     * and it has room.
     */
    public static boolean accepts(final IPatternContainer container, final ItemStack pattern,
            @Nullable final ICraftingPatternDetails details) {
        final AEItemKey key = AEItemKey.of(pattern);

        return key != null
                && container.canAccept(pattern, details)
                && !container.containsPattern(key)
                && freeSlots(container) > 0;
    }

    /**
     * Where an upload with nobody watching sends a pattern: of the containers that would take it, the one
     * with the most room, and of those the nearest to the player.
     *
     * <p>Most room rather than first found, because the order machines come out of a grid is the order they
     * were added to it and means nothing to a player - and because spreading patterns over the groups of
     * assemblers is what a player would do by hand anyway. Distance only breaks a tie, so the answer does not
     * change as the player walks about.</p>
     *
     * @return null when nothing in the network would take it.
     */
    @Nullable
    public static IPatternContainer best(final List<IPatternContainer> containers, final EntityPlayer player,
            final ItemStack pattern, @Nullable final ICraftingPatternDetails details) {
        return containers.stream()
                .filter(container -> accepts(container, pattern, details))
                .max(Comparator.<IPatternContainer>comparingInt(PatternContainers::freeSlots)
                        .thenComparing(container -> -distanceTo(container, player)))
                .orElse(null);
    }

    /**
     * How far a container is from the player, squared, or the largest distance there is when it is nowhere or
     * in another world - so that a container which cannot be walked to loses every tie.
     */
    private static double distanceTo(final IPatternContainer container, final EntityPlayer player) {
        final DimensionalCoord where = container.getTerminalLocation();

        if (where == null || where.getWorld() != player.world) {
            return Double.MAX_VALUE;
        }

        return player.getDistanceSq(where.getPos());
    }
}
