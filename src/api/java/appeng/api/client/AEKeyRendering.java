/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.client;


import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;


/**
 * Registry of {@link AEKeyRenderHandler}, one per key type. Register during client init; a type registered
 * without a handler still draws, as the model of its {@link AEKeyType#getButtonIcon()}.
 */
@SideOnly(Side.CLIENT)
public final class AEKeyRendering {

    public static final int NO_TINT = 0xFFFFFFFF;

    // Replaced wholesale rather than written into: registration happens once, reads happen every frame.
    private static volatile Map<AEKeyType, AEKeyRenderHandler> handlers = new IdentityHashMap<>();

    private AEKeyRendering() {
    }

    public static synchronized void register(final AEKeyType type, final AEKeyRenderHandler handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handler, "handler");

        final Map<AEKeyType, AEKeyRenderHandler> updated = new IdentityHashMap<>(handlers);
        if (updated.put(type, handler) != null) {
            throw new IllegalArgumentException("Duplicate render handler for key type " + type.getRegistryName());
        }
        handlers = updated;
    }

    @Nullable
    public static AEKeyRenderHandler get(@Nullable final AEKeyType type) {
        return type == null ? null : handlers.get(type);
    }

    @Nullable
    public static AEKeyRenderHandler get(@Nullable final AEKey what) {
        return what == null ? null : get(what.getType());
    }

    public static int getTint(@Nullable final AEKey what) {
        final AEKeyRenderHandler handler = get(what);
        return handler == null ? NO_TINT : handler.getTint(what);
    }
}
