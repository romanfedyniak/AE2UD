/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.networking.visualiser;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


/**
 * The providers the network visualiser asks while it builds a picture. Register during init.
 * <p>
 * The list is replaced rather than written into, because registration happens once and the picture is built
 * on the server thread over and over.
 */
public final class NetworkVisualisers {

    private static volatile List<INetworkVisualiserProvider> providers = Collections.emptyList();

    private NetworkVisualisers() {
    }

    public static synchronized void register(final INetworkVisualiserProvider provider) {
        Objects.requireNonNull(provider, "provider");

        final List<INetworkVisualiserProvider> updated = new ArrayList<>(providers);
        updated.add(provider);
        providers = Collections.unmodifiableList(updated);
    }

    /**
     * @return every registered provider, usually none at all.
     */
    public static List<INetworkVisualiserProvider> providers() {
        return providers;
    }
}
