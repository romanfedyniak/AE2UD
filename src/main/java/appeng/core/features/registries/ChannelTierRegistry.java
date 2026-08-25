package appeng.core.features.registries;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import appeng.api.networking.pathing.IChannelTier;
import appeng.api.networking.pathing.IChannelTierRegistry;
import appeng.core.AEConfig;
import appeng.core.AELog;

public final class ChannelTierRegistry implements IChannelTierRegistry {

    private final Map<ResourceLocation, IChannelTier> tiers = new LinkedHashMap<>();

    /**
     * The number comes from the config file the moment the tier is named, which is why registering late leaves
     * a tier nobody can configure.
     */
    @Override
    public IChannelTier register(final ResourceLocation id, final int defaultCapacity) {
        if (id == null) {
            return null;
        }

        final IChannelTier existing = this.tiers.get(id);
        if (existing != null) {
            AELog.warn("Channel tier %s is already registered, ignoring the second one.", id);
            return existing;
        }

        final IChannelTier tier = new ChannelTier(id, AEConfig.instance().getChannelTierCapacity(id, defaultCapacity));
        this.tiers.put(id, tier);
        return tier;
    }

    @Nullable
    @Override
    public IChannelTier getTier(final ResourceLocation id) {
        return id == null ? null : this.tiers.get(id);
    }

    @Override
    public Collection<IChannelTier> getTiers() {
        return Collections.unmodifiableCollection(this.tiers.values());
    }
}
