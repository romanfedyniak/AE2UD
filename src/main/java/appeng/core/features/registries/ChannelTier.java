package appeng.core.features.registries;

import net.minecraft.util.ResourceLocation;

import appeng.api.networking.pathing.IChannelTier;

public final class ChannelTier implements IChannelTier {

    private final ResourceLocation id;
    private final int capacity;

    ChannelTier(final ResourceLocation id, final int capacity) {
        this.id = id;
        this.capacity = capacity;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public int getCapacity() {
        return this.capacity;
    }
}
