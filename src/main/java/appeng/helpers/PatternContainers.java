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
 * Finding the things in a network that hold patterns, and choosing between them. Works from
 * {@link IPatternContainer} alone, so a machine an addon adds is found without naming its class.
 */
public final class PatternContainers {

    private PatternContainers() {
    }

    /** In no particular order. */
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

    /** Empty and paid for. A pattern put past that line is not somewhere it can stay. */
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

    /** @return false if there was no free usable slot, in which case nothing was moved. */
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

    /** What it says it has paid for, and never more than it has. */
    public static int usableSlots(final IPatternContainer container) {
        return Math.min(container.getUsablePatternSlots(), container.getTerminalPatternInventory().getSlots());
    }

    /** It would run the pattern, it has not got it already, and it has room. */
    public static boolean accepts(final IPatternContainer container, final ItemStack pattern,
            @Nullable final ICraftingPatternDetails details) {
        final AEItemKey key = AEItemKey.of(pattern);

        return key != null
                && container.canAccept(pattern, details)
                && !container.containsPattern(key)
                && freeSlots(container) > 0;
    }

    /**
     * Most room, then nearest. Never first-found: a grid hands machines back in the order they joined it,
     * which changes across a reload.
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

    /** Squared, and infinite for another world, so an unreachable container loses every tie. */
    private static double distanceTo(final IPatternContainer container, final EntityPlayer player) {
        final DimensionalCoord where = container.getTerminalLocation();

        if (where == null || where.getWorld() != player.world) {
            return Double.MAX_VALUE;
        }

        return player.getDistanceSq(where.getPos());
    }
}
