/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.networking.crafting;

import appeng.api.stacks.GenericStack;

/**
 * Why submitting a crafting job failed.
 */
public enum CraftingSubmitErrorCode {
    /**
     * The plan is a simulation - it could not be completed with what the network has - and cannot be submitted.
     */
    INCOMPLETE_PLAN,
    /**
     * The network has no crafting CPUs at all.
     */
    NO_CPU_FOUND,
    /**
     * Every CPU on the network was unsuitable. {@link ICraftingSubmitResult#errorDetail()} holds an
     * {@link UnsuitableCpus} saying how many fell out for which reason.
     */
    NO_SUITABLE_CPU_FOUND,
    /**
     * The chosen CPU is working on another job.
     */
    CPU_BUSY,
    /**
     * The chosen CPU has no power, or not enough channels.
     */
    CPU_OFFLINE,
    /**
     * The chosen CPU has less storage than the job needs.
     */
    CPU_TOO_SMALL,
    /**
     * An ingredient the job counted on could not be taken from the network after all, which is what happens
     * when something else empties storage between planning the job and starting it.
     * {@link ICraftingSubmitResult#errorDetail()} holds the {@link GenericStack} that was missing, if it is known.
     */
    MISSING_INGREDIENT
}
