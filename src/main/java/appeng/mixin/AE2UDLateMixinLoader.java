package appeng.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import com.google.common.collect.ImmutableMap;

import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.Context;
import zone.rong.mixinbooter.ILateMixinLoader;

@SuppressWarnings("unused")
public class AE2UDLateMixinLoader implements ILateMixinLoader {

    private static final Map<String, BooleanSupplier> mixinConfigs = ImmutableMap.copyOf(new HashMap<>() {
        {
            put("mixins.appliedenergistics2.late.jei.json", () -> Loader.isModLoaded("jei"));
        }
    });

    @Override
    public List<String> getMixinConfigs() {
        return new ArrayList<>(mixinConfigs.keySet());
    }

    @Override
    public boolean shouldMixinConfigQueue(final Context context) {
        return mixinConfigs.get(context.mixinConfig()).getAsBoolean();
    }
}
