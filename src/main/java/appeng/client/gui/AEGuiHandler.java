package appeng.client.gui;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.implementations.GuiCraftAmount;
import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.implementations.GuiMonitor;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.client.gui.KeySearchTarget;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.interfaces.ISpecialSlotIngredient;
import appeng.container.slot.IJEITargetSlot;
import mezz.jei.api.gui.IAdvancedGuiHandler;
import mezz.jei.api.gui.IGhostIngredientHandler;
import mezz.jei.api.gui.ISlotIngredientProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class AEGuiHandler implements IAdvancedGuiHandler<AEBaseGui>, IGhostIngredientHandler<AEBaseGui>, ISlotIngredientProvider<AEBaseGui> {

    /** Whether the drag now ending was dropped on a target of ours. See {@link #onComplete()}. */
    private boolean landed;
    @Override
    @Nonnull
    public Class<AEBaseGui> getGuiContainerClass() {
        return AEBaseGui.class;
    }

    @Nullable
    @Override
    public List<Rectangle> getGuiExtraAreas(@Nonnull AEBaseGui guiContainer) {
        return guiContainer.getJEIExclusionArea();
    }

    @Nullable
    @Override
    public Object getIngredientUnderMouse(@Nonnull AEBaseGui guiContainer, int mouseX, int mouseY) {
        // A screen that draws things outside slots answers for itself; everything below is about slots.
        if (guiContainer instanceof IKeyUnderMouse source) {
            final AEKey what = source.getKeyUnderMouse(mouseX, mouseY);
            if (what != null) {
                return asIngredient(what, 1);
            }
        }

        final Slot slot = guiContainer.getSlotUnderMouse();
        if (slot instanceof ISpecialSlotIngredient ss) {
            return ss.getIngredient();
        }
        for (GuiCustomSlot customSlot : guiContainer.guiSlots) {
            if (this.checkSlotArea(guiContainer, customSlot, mouseX, mouseY)) {
                return customSlot.getIngredient();
            }
        }

        // Last: an ordinary slot holding a wrapped key. HEI reads a slot's ItemStack itself, and for a
        // non-item key that stack is a WrappedGenericStack placeholder - an item with no recipes, which is
        // why the recipe keybinds did nothing over a fluid and worked over an item. Covers every screen in
        // the mod at once: terminal rows, pattern slots, bus filters, cell partitions.
        if (slot != null) {
            final Object unwrapped = asIngredient(slot.getStack());
            if (unwrapped != null) {
                return unwrapped;
            }
        }

        return null;
    }

    /**
     * Tells HEI what an ordinary slot's stack really represents, so recipe lookups, the recipe keybinds,
     * bookmarks, tooltips, Move Items and cheat-mode clicks all see a fluid instead of its placeholder -
     * every one of them reads a hovered slot through this before falling back to its raw {@code ItemStack}.
     */
    @Nullable
    @Override
    public Object getSlotIngredient(@Nonnull AEBaseGui guiContainer, @Nonnull Slot slot, @Nonnull ItemStack stack) {
        return asIngredient(stack);
    }

    /**
     * Turns a key into the ingredient HEI knows how to look recipes up for: a {@code FluidStack} for a fluid,
     * the key's own stack for anything else.
     *
     * @param amount only matters for a type HEI measures - and only in that it must not be zero, since an
     *               empty {@code FluidStack} is one HEI ignores. Terminal rows wrap with amount 0 because
     *               their amount is drawn separately, and a recipe lookup does not care either way.
     */
    @Nullable
    private static Object asIngredient(@Nullable AEKey what, long amount) {
        if (what == null) {
            return null;
        }
        if (what instanceof AEFluidKey fluidKey) {
            return fluidKey.toStack((int) Math.max(1, Math.min(amount, Integer.MAX_VALUE)));
        }
        return what.wrapForDisplayOrFilter();
    }

    /**
     * @return the ingredient a placeholder stack stands for, or null if this is an ordinary item stack - in
     *         which case HEI already reads the slot correctly and must be left to do so.
     */
    @Nullable
    private static Object asIngredient(ItemStack stack) {
        final GenericStack wrapped = GenericStack.unwrapItemStack(stack);
        return wrapped == null ? null : asIngredient(wrapped.what(), wrapped.amount());
    }

    /**
     * The same reading as {@link #getSlotIngredient}, for a caller that has to hand HEI an ingredient
     * itself rather than answer a question about a slot: an ordinary stack stands for itself, and a
     * placeholder for the fluid or other key inside it.
     */
    public static Object ingredientOf(ItemStack stack) {
        final Object wrapped = asIngredient(stack);
        return wrapped != null ? wrapped : stack;
    }

    /**
     * @return the key a dragged HEI ingredient stands for, or null for anything a pin or a search box has
     *         no use for - an empty fluid, or a key type HEI has no wrapper for.
     */
    @Nullable
    public static AEKey keyOf(final Object ingredient) {
        if (ingredient instanceof ItemStack) {
            final GenericStack stack = GenericStack.resolveItemStack((ItemStack) ingredient);
            return stack == null ? null : stack.what();
        } else if (ingredient instanceof FluidStack && ((FluidStack) ingredient).amount > 0) {
            return AEFluidKey.of((FluidStack) ingredient);
        }
        return null;
    }

    private boolean checkSlotArea(GuiContainer gui, GuiCustomSlot slot, int mouseX, int mouseY) {
        int i = gui.guiLeft;
        int j = gui.guiTop;
        mouseX = mouseX - i;
        mouseY = mouseY - j;
        return mouseX >= slot.xPos() - 1 &&
                mouseX < slot.xPos() + slot.getWidth() + 1 &&
                mouseY >= slot.yPos() - 1 &&
                mouseY < slot.yPos() + slot.getHeight() + 1;
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public <I> List<Target<I>> getTargets(@Nonnull AEBaseGui gui, @Nonnull I ingredient, boolean doStart) {
        ArrayList<Target<I>> targets = new ArrayList<>();
        if (gui instanceof IJEIGhostIngredients g) {
            List<Target<?>> phantomTargets = g.getPhantomTargets(ingredient);
            targets.addAll((List<Target<I>>) (Object) phantomTargets);
        }
        if (gui instanceof GuiMEMonitorable meGui) {
            List<Target<?>> pinTargets = meGui.getPinGhostTargets(ingredient);
            targets.addAll((List<Target<I>>) (Object) pinTargets);
        }

        // Dropping an ingredient on a search box searches for its name - the same convention GTNH's NEI
        // integration used, and now on every box rather than only the terminal's.
        // NOTE: keep the search-box targets last; the wrapping below is applied to the whole list.
        final AEKey dragged = keyOf(ingredient);
        if (dragged != null) {
            for (final KeySearchTarget box : gui.getKeySearchTargets()) {
                targets.add(new Target<I>() {
                    @Override
                    public Rectangle getArea() {
                        return box.getArea();
                    }

                    @Override
                    public void accept(final I ignored) {
                        box.accept(dragged);
                    }
                });
            }
        }

        if (!doStart) {
            // Only a query - a hover highlight, or quickMove looking up the slot behind a target. The map
            // that lookup reads is keyed by the targets the screen itself made, so hand those back bare.
            return targets;
        }

        this.landed = false;

        final List<Target<I>> watched = new ArrayList<>(targets.size());
        for (final Target<I> target : targets) {
            watched.add(new Target<I>() {
                @Override
                public Rectangle getArea() {
                    return target.getArea();
                }

                @Override
                public void accept(final I ingredient) {
                    AEGuiHandler.this.landed = true;
                    target.accept(ingredient);
                }
            });
        }
        return watched;
    }

    /**
     * Shift-click places the ingredient straight into the first slot that will take it, instead of starting
     * a drag.
     * <p>
     * This used to be done inside {@link #getTargets}, which is a query rather than an action - and, worse,
     * one HEI calls <em>while starting the drag it is about to run</em>. The ingredient was therefore both
     * placed in the slot and left hanging off the cursor as a ghost. {@code quickMove} is the hook meant for
     * this: HEI calls it first, and a {@code true} return skips the targeting and drag logic entirely. It
     * did not exist in JEI 4.16, which this code was originally written against; it arrived in HEI 4.30.2,
     * so the workaround only became removable when the dependency was switched in wave 6.
     */
    @Override
    public <I> boolean quickMove(@Nonnull AEBaseGui gui, @Nonnull I ingredient) {
        if (!(gui instanceof GuiUpgradeable || gui instanceof GuiPatternTerm || gui instanceof GuiMonitor)) {
            return false;
        }

        if (!(gui instanceof IJEIGhostIngredients ghostGui)) {
            return false;
        }

        for (Target<I> target : this.getTargets(gui, ingredient, false)) {
            if (ghostGui.getFakeSlotTargetMap().get(target) instanceof IJEITargetSlot jeiSlot && jeiSlot.needAccept()) {
                target.accept(ingredient);
                return true;
            }
        }

        return false;
    }

    /**
     * A drag has ended, landed or not. HEI hands the click back to the screen when nothing took the
     * ingredient, and there an empty hand over a filter slot means "clear it" - so a refused ingredient
     * wiped whatever was in the slot it was dropped on. This runs first, while the drag is still the
     * reason for that click, so it is the last chance to say the click is not the player's.
     * <p>
     * The slot test keeps a drop onto one of HEI's own bookmark targets out of it: those land nowhere near
     * the window's slots, and no click reaches the screen after them.
     */
    @Override
    public void onComplete() {
        final GuiScreen screen = Minecraft.getMinecraft().currentScreen;

        if (!this.landed && screen instanceof AEBaseGui gui && gui.getSlotUnderMouse() != null) {
            gui.ignoreGhostDropClick();
        }

        this.landed = false;
    }

    @Override
    public boolean shouldHighlightTargets() {
        return true;
    }

}
