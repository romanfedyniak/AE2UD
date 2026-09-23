/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.client;


import com.google.common.collect.ImmutableMap;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;


/**
 * How an addon's crafting unit looks in a formed CPU: AE2's frame and rings around a light texture of the
 * addon's. Before the CPU forms the block is drawn from the {@code normal} variant of its own blockstate.
 * <p>
 * Register during client pre-initialisation; the block has to extend AE2's crafting unit block.
 */
@SideOnly(Side.CLIENT)
public final class CraftingUnitModels {

    private static volatile Map<Block, ResourceLocation> lights = ImmutableMap.of();

    private CraftingUnitModels() {
    }

    public static synchronized void register(final Block block, final ResourceLocation lightTexture) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(lightTexture, "lightTexture");

        final Map<Block, ResourceLocation> next = new LinkedHashMap<>(lights);
        next.put(block, lightTexture);
        lights = ImmutableMap.copyOf(next);
    }

    public static Map<Block, ResourceLocation> getAll() {
        return lights;
    }
}
