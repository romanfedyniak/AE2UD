/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import appeng.core.localization.GuiText;

/**
 * How large each multiblock may be built, so a block can say it in its tooltip instead of letting the player
 * find out at the eighteenth block.
 * <p>
 * The numbers come from the config, which means the client's own file - and nothing makes that agree with the
 * server's. So a joining player is sent the server's, exactly as channel tiers are, and they are put back on
 * disconnect. An addon with a multiblock of its own registers here and gets both for free.
 */
public final class MultiblockLimits {

    public static final ResourceLocation CONTROLLER = new ResourceLocation(AppEng.MOD_ID, "controller");
    public static final ResourceLocation CRAFTING_CPU = new ResourceLocation(AppEng.MOD_ID, "crafting_cpu");

    private static final Map<ResourceLocation, Limit> LIMITS = new LinkedHashMap<>();

    private MultiblockLimits() {
    }

    public static Limit register(final ResourceLocation id, final IntSupplier x, final IntSupplier y,
            final IntSupplier z, final BooleanSupplier singleChunk) {
        final Limit limit = new Limit(id, x, y, z, singleChunk);
        LIMITS.put(id, limit);
        return limit;
    }

    public static Collection<Limit> all() {
        return LIMITS.values();
    }

    @Nullable
    public static Limit get(final ResourceLocation id) {
        return LIMITS.get(id);
    }

    /** Back to this side's own config, once the server whose numbers were in force is left. */
    public static void restoreLocal() {
        for (final Limit limit : LIMITS.values()) {
            limit.restore();
        }
    }

    public static final class Limit {

        private final ResourceLocation id;
        private final IntSupplier localX;
        private final IntSupplier localY;
        private final IntSupplier localZ;
        private final BooleanSupplier localSingleChunk;

        /** What the server said, or null while this side's own config is the one in force. */
        @Nullable
        private int[] override;
        private boolean overrideSingleChunk;

        private Limit(final ResourceLocation id, final IntSupplier x, final IntSupplier y, final IntSupplier z,
                final BooleanSupplier singleChunk) {
            this.id = id;
            this.localX = x;
            this.localY = y;
            this.localZ = z;
            this.localSingleChunk = singleChunk;
        }

        public ResourceLocation getId() {
            return this.id;
        }

        public int getX() {
            return this.override != null ? this.override[0] : this.localX.getAsInt();
        }

        public int getY() {
            return this.override != null ? this.override[1] : this.localY.getAsInt();
        }

        public int getZ() {
            return this.override != null ? this.override[2] : this.localZ.getAsInt();
        }

        public boolean requiresSingleChunk() {
            return this.override != null ? this.overrideSingleChunk : this.localSingleChunk.getAsBoolean();
        }

        public void override(final int x, final int y, final int z, final boolean singleChunk) {
            this.override = new int[] { x, y, z };
            this.overrideSingleChunk = singleChunk;
        }

        public void restore() {
            this.override = null;
        }

        /** What a block of this multiblock says it may be built into. */
        public void addTooltip(final List<String> lines) {
            lines.add(GuiText.MaxMultiblockSize.getLocal(this.getX(), this.getY(), this.getZ()));
            if (this.requiresSingleChunk()) {
                lines.add(GuiText.MultiblockSingleChunk.getLocal());
            }
        }
    }
}
