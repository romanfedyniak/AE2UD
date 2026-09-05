/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.networking.crafting;


import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.stacks.AEItemKey;
import appeng.api.util.DimensionalCoord;


/**
 * Something in a network that holds encoded patterns: an ME Interface, and whatever an addon builds that does
 * the same job.
 * <p>
 * The Pattern Access Terminal lists these rather than naming the classes it knows about, and so does anything
 * that puts a pattern into a network. A machine that keeps its own patterns - a multiblock assembler, say -
 * only has to say so here to be reachable by both.
 * <p>
 * Upstream calls this {@code PatternContainer} and carries rather more on it: grouping, custom names, opening
 * the machine's own screen. This is deliberately the smaller half, and the names of the methods that do the
 * same job as upstream's are upstream's, so that half can arrive later as default methods rather than as a
 * rename. Everything with an answer that can be worked out from the rest is a default, so an addon
 * implementing this writes two methods and stops.
 */
public interface IPatternContainer {

    /**
     * Whether a terminal lists this at all. False is how a machine says the player has asked not to be
     * bothered with it, not that it is broken or full.
     */
    default boolean isVisibleInTerminal() {
        return true;
    }

    /**
     * The patterns this holds. Empty slots are free space; what may go in them is {@link #canAccept}'s
     * business, not this handler's.
     */
    @Nonnull
    IItemHandler getTerminalPatternInventory();

    /**
     * How many of that inventory's slots are actually in use. A container may keep room it has not been paid
     * for - an interface's pattern rows are bought one expansion card at a time - and a pattern put past this
     * line is not somewhere it can stay.
     */
    default int getUsablePatternSlots() {
        return this.getTerminalPatternInventory().getSlots();
    }

    /**
     * Whether this container would actually work the given pattern, ignoring whether it has room for it.
     * <p>
     * This is where a container's own rules live. An interface takes a processing pattern anywhere, but a
     * crafting one only when something beside it will take plans; a machine that only assembles answers the
     * other way round. Answering honestly here is what keeps a pattern from being filed somewhere it would
     * sit and never run.
     *
     * @param pattern the encoded pattern itself, for a container that cares which item it is
     * @param details what that pattern says it does, or null when it decodes to nothing
     */
    boolean canAccept(@Nonnull ItemStack pattern, @Nullable ICraftingPatternDetails details);

    /**
     * Whether this already holds that exact pattern. Two of one pattern in one container is always a
     * mistake: the second takes a slot and adds nothing.
     */
    default boolean containsPattern(@Nonnull final AEItemKey pattern) {
        final IItemHandler patterns = this.getTerminalPatternInventory();

        for (int slot = 0; slot < patterns.getSlots(); slot++) {
            if (pattern.matches(patterns.getStackInSlot(slot))) {
                return true;
            }
        }

        return false;
    }

    /**
     * What to call this in a list, and what to draw beside the name.
     * <p>
     * Named for what it returns rather than after upstream's {@code getTerminalGroup}, which answers with a
     * grouping this version has no notion of yet. That one can arrive beside this without disturbing it.
     */
    @Nonnull
    MachineIdentity getTerminalIdentity();

    /**
     * What to call this while one particular pattern is being filed into it.
     * <p>
     * A container that stands beside several machines has a choice of names, and the useful one is the
     * machine that would run this pattern rather than whichever neighbour happens to come first. Defaults to
     * the plain identity, which is right for anything with only one thing to be named after.
     */
    @Nonnull
    default MachineIdentity getTerminalIdentity(@Nonnull final ItemStack pattern,
            @Nullable final ICraftingPatternDetails details) {
        return this.getTerminalIdentity();
    }

    /**
     * Where this stands, so a terminal can point the player at it. Null when it is nowhere in particular.
     */
    @Nullable
    DimensionalCoord getTerminalLocation();

    /**
     * The order a terminal lists containers in, smallest first. Ties are broken by whatever the terminal
     * likes, so this need only separate the ones a player wants separated.
     */
    default long getTerminalSortOrder() {
        return 0;
    }

    /**
     * Whether everything here is a fake craft - a pattern that hands its output over without making it. A
     * terminal marks the whole container rather than each pattern, because the setting belongs to the
     * container.
     */
    default boolean isFakeCrafting() {
        return false;
    }
}
