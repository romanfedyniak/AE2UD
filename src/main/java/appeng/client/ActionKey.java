package appeng.client;


import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.input.Keyboard;


public enum ActionKey {
    TOGGLE_FOCUS(Keyboard.KEY_TAB, KeyConflictContext.GUI),
    VIEW_PATTERN(Keyboard.KEY_P, KeyConflictContext.GUI);

    private final int defaultKey;
    private final KeyConflictContext conflictContext;

    ActionKey(int defaultKey, KeyConflictContext conflictContext) {
        this.defaultKey = defaultKey;
        this.conflictContext = conflictContext;
    }

    public String getTranslationKey() {
        return "key." + this.name().toLowerCase() + ".desc";
    }

    public int getDefaultKey() {
        return this.defaultKey;
    }

    /**
     * These act only inside an open screen, so binding one to a key that also does something in the world is
     * not a clash. Saying so keeps the controls screen from marking it red.
     */
    public KeyConflictContext getConflictContext() {
        return this.conflictContext;
    }
}
