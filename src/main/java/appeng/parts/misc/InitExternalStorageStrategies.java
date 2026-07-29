package appeng.parts.misc;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.stacks.AEKeyType;
import appeng.fluids.parts.FluidHandlerAdapter;

/**
 * Registers the built-in {@link ExternalStorageStrategy} implementations that {@link PartStorageBus} (and any
 * future storage bus) can pick up through
 * {@link appeng.api.behaviors.StackWorldBehaviors#createExternalStorageStrategies}.
 * <p/>
 * The item strategy was registered in wave 3b. The fluid strategy (wave 5) registers through this exact same
 * public API, with no changes required in {@link PartStorageBus} or here -- from this point on the same storage
 * bus serves items and fluids simultaneously (see CONTRACT.md §9, wave 3b entry).
 * {@code appeng.fluids.parts.PartFluidStorageBus} looks the fluid strategy up the same way, restricted to
 * {@code AEKeyType.fluids()} alone.
 * <p/>
 * Called once from {@code appeng.core.Registration} during mod init.
 */
public final class InitExternalStorageStrategies {

    private InitExternalStorageStrategies() {
    }

    public static void register() {
        ExternalStorageStrategy.register(AEKeyType.items(), ItemHandlerAdapter.Strategy::new);
        ExternalStorageStrategy.register(AEKeyType.fluids(), FluidHandlerAdapter.Strategy::new);
    }
}
