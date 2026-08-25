package appeng.core.features.registries;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import appeng.api.features.IWirelessTerminalMode;
import appeng.api.features.IWirelessTerminalModeRegistry;
import appeng.core.AELog;

public final class WirelessTerminalModeRegistry implements IWirelessTerminalModeRegistry {

    private final Map<ResourceLocation, IWirelessTerminalMode> modes = new LinkedHashMap<>();

    @Override
    public void register(final IWirelessTerminalMode mode) {
        if (mode == null || mode.getId() == null) {
            return;
        }

        final IWirelessTerminalMode existing = this.modes.putIfAbsent(mode.getId(), mode);
        if (existing != null) {
            AELog.warn("Wireless terminal mode %s is already registered, ignoring the second one.", mode.getId());
        }
    }

    @Nullable
    @Override
    public IWirelessTerminalMode getMode(final ResourceLocation id) {
        return id == null ? null : this.modes.get(id);
    }

    @Override
    public Collection<IWirelessTerminalMode> getModes() {
        return Collections.unmodifiableCollection(this.modes.values());
    }

    /**
     * The first mode registered, which is AE2's own plain terminal - an addon cannot get in front of it,
     * because it has to depend on AE2 to name a mode at all.
     */
    @Nullable
    @Override
    public IWirelessTerminalMode getDefaultMode() {
        return this.modes.isEmpty() ? null : this.modes.values().iterator().next();
    }
}
