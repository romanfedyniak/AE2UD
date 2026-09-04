package appeng.client;


import java.util.function.BooleanSupplier;

import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.input.Keyboard;

import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.util.Platform;


public enum ActionKey {
    TOGGLE_FOCUS(Keyboard.KEY_TAB, KeyConflictContext.GUI),
    VIEW_PATTERN(Keyboard.KEY_P, KeyConflictContext.GUI),

    JEI_RETRIEVE(Mouse.MIDDLE, KeyConflictContext.GUI, KeyModifier.CONTROL,
            () -> isEnabledWithJei(AEFeature.JEI_RETRIEVE)),
    JEI_CRAFT(Mouse.MIDDLE, KeyConflictContext.GUI, KeyModifier.ALT,
            () -> isEnabledWithJei(AEFeature.JEI_CRAFT_REQUEST));

    /**
     * Vanilla numbers mouse buttons a hundred below zero so they share the space keyboard codes live in,
     * which is how the pick block key is bound too. In a holder because an enum constant may not read a
     * static field of its own enum.
     */
    private static final class Mouse {
        static final int MIDDLE = 2 - 100;
    }

    private final int defaultKey;
    private final KeyConflictContext conflictContext;
    private final KeyModifier modifier;
    private final BooleanSupplier available;

    ActionKey(int defaultKey, KeyConflictContext conflictContext) {
        this(defaultKey, conflictContext, KeyModifier.NONE, () -> true);
    }

    ActionKey(int defaultKey, KeyConflictContext conflictContext, KeyModifier modifier,
            BooleanSupplier available) {
        this.defaultKey = defaultKey;
        this.conflictContext = conflictContext;
        this.modifier = modifier;
        this.available = available;
    }

    public String getTranslationKey() {
        return "key." + this.name().toLowerCase() + ".desc";
    }

    public int getDefaultKey() {
        return this.defaultKey;
    }

    public KeyModifier getDefaultModifier() {
        return this.modifier;
    }

    /**
     * Whether the key is worth offering at all. One that acts on an ingredient in JEI's list is not, in a
     * pack without JEI or with the feature turned off - an entry in the controls screen that can never do
     * anything is worse than no entry.
     */
    public boolean isAvailable() {
        return this.available.getAsBoolean();
    }

    /**
     * These act only inside an open screen, so binding one to a key that also does something in the world is
     * not a clash. Saying so keeps the controls screen from marking it red.
     */
    public KeyConflictContext getConflictContext() {
        return this.conflictContext;
    }

    private static boolean isEnabledWithJei(final AEFeature feature) {
        return Platform.isModLoaded("jei") && AEConfig.instance().isFeatureEnabled(feature);
    }
}
