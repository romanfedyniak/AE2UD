package appeng.parts.misc;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.stacks.AEKeyType;

/**
 * Registers the built-in {@link ExternalStorageStrategy} implementations that {@link PartStorageBus} (and any
 * future storage bus) can pick up through
 * {@link appeng.api.behaviors.StackWorldBehaviors#createExternalStorageStrategies}.
 * <p/>
 * Only the item strategy is registered here (wave 3b). The fluid strategy is wave 5's job - it registers through
 * this exact same public API, with no changes required in {@link PartStorageBus} or here.
 * <p/>
 * Called once from {@code appeng.core.Registration} during mod init.
 */
public final class InitExternalStorageStrategies {

    private InitExternalStorageStrategies() {
    }

    public static void register() {
        ExternalStorageStrategy.register(AEKeyType.items(), ItemHandlerAdapter.Strategy::new);
    }
}
