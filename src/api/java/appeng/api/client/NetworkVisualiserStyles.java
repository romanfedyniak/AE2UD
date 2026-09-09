/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.client;


import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


/**
 * What the styles named by {@link appeng.api.networking.visualiser.INetworkVisualiserProvider} look like.
 * Register during client init; an id nobody registered is drawn in the plain colour the config gives for
 * anything unclaimed, which is what a server-only addon leaves behind.
 */
@SideOnly(Side.CLIENT)
public final class NetworkVisualiserStyles {

    private static volatile Map<ResourceLocation, VisualiserStyle> styles = new HashMap<>();

    private NetworkVisualiserStyles() {
    }

    public static synchronized void register(final ResourceLocation id, final VisualiserStyle style) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(style, "style");

        final Map<ResourceLocation, VisualiserStyle> updated = new HashMap<>(styles);
        if (updated.put(id, style) != null) {
            throw new IllegalArgumentException("Duplicate visualiser style " + id);
        }
        styles = updated;
    }

    @Nullable
    public static VisualiserStyle get(@Nullable final ResourceLocation id) {
        return id == null ? null : styles.get(id);
    }
}
