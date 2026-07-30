package appeng.integration.modules.jei;


import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;

import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mezz.jei.api.recipe.IFocus;
import mezz.jei.config.KeyBindings;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseGui;


/**
 * Makes HEI's recipe keybinds work over a slot that shows a non-item key.
 * <p/>
 * Every such key travels through a GUI as a {@link appeng.items.misc.WrappedGenericStack} placeholder, which
 * is a perfectly ordinary item with no recipes. HEI asks the hovered <em>slot</em> for its ingredient before
 * it asks any {@code IAdvancedGuiHandler}, so it finds that placeholder, is satisfied, and looks up recipes
 * for a display shim - which is why pressing the key over a fluid row did nothing while an item row worked.
 * Telling HEI what the slot really holds is therefore not enough; the answer has to arrive before it asks.
 * <p/>
 * So this listens to the keyboard event at {@link EventPriority#HIGHEST}, ahead of HEI's own handler, and
 * only takes over when the slot under the mouse holds a wrapped key. Anything else - every item slot in the
 * mod, and every other screen - is left entirely alone.
 */
@SideOnly(Side.CLIENT)
public class WrappedKeyRecipeShortcut {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyboardInput(final GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (JEIPlugin.runtime == null || !Keyboard.getEventKeyState()) {
            return;
        }

        if (!(event.getGui() instanceof AEBaseGui gui) || gui.isTextFieldFocused()) {
            // A focused search box owns the keyboard: the letter is text, not a shortcut. HEI makes the
            // same check for its own field, which is why an item row typed an R and a fluid row did not.
            return;
        }

        final IFocus.Mode mode = modeFor(Keyboard.getEventKey());
        if (mode == null) {
            return;
        }

        final Object ingredient = ingredientUnder(gui.getSlotUnderMouse());
        if (ingredient == null) {
            return;
        }

        JEIPlugin.runtime.getRecipesGui()
                .show(JEIPlugin.runtime.getRecipeRegistry().createFocus(mode, ingredient));
        event.setCanceled(true);
    }

    /**
     * @return which way round to look the ingredient up, or null if this key is neither of HEI's two recipe
     *         bindings. Read from HEI's own bindings so a rebind is followed automatically.
     */
    @Nullable
    private static IFocus.Mode modeFor(final int key) {
        if (key == Keyboard.KEY_NONE) {
            return null;
        }
        if (KeyBindings.showRecipe.isActiveAndMatches(key)) {
            return IFocus.Mode.OUTPUT;
        }
        if (KeyBindings.showUses.isActiveAndMatches(key)) {
            return IFocus.Mode.INPUT;
        }
        return null;
    }

    @Nullable
    private static Object ingredientUnder(@Nullable final Slot slot) {
        if (slot == null) {
            return null;
        }

        final GenericStack wrapped = GenericStack.unwrapItemStack(slot.getStack());
        if (wrapped == null) {
            // An ordinary item: HEI reads the slot correctly by itself, so stay out of the way.
            return null;
        }

        if (wrapped.what() instanceof AEFluidKey fluidKey) {
            // A recipe lookup ignores the amount, and a zero-amount FluidStack is the empty one, which HEI
            // would discard - terminal rows wrap with zero because their amount is drawn separately.
            return fluidKey.toStack((int) Math.max(1, Math.min(wrapped.amount(), Integer.MAX_VALUE)));
        }

        return null;
    }

    static void register() {
        MinecraftForge.EVENT_BUS.register(new WrappedKeyRecipeShortcut());
    }
}
