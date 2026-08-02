package appeng.client.gui;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.implementations.GuiCraftAmount;
import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.client.gui.implementations.GuiExpandedProcessingPatternTerm;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.interfaces.ISpecialSlotIngredient;
import appeng.container.slot.IJEITargetSlot;
import mezz.jei.api.gui.IAdvancedGuiHandler;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class AEGuiHandler implements IAdvancedGuiHandler<AEBaseGui>, IGhostIngredientHandler<AEBaseGui> {
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
        List<AEKey> visual;
        int guiSlotIdx;
        Object result = null;
        if (guiContainer instanceof GuiCraftConfirm) {
            guiSlotIdx = getSlotidx(guiContainer, mouseX, mouseY, ((GuiCraftConfirm) guiContainer).getDisplayedRows());
            visual = ((GuiCraftConfirm) guiContainer).getVisual();
            if (guiSlotIdx < visual.size() && guiSlotIdx != -1) {
                result = asIngredient(visual.get(guiSlotIdx), 1);
            } else {
                return null;
            }
        }

        if (guiContainer instanceof GuiCraftingCPU) {
            guiSlotIdx = getSlotidx(guiContainer, mouseX, mouseY, ((GuiCraftingCPU) guiContainer).getDisplayedRows());
            visual = ((GuiCraftingCPU) guiContainer).getVisual();
            if (guiSlotIdx < visual.size() && guiSlotIdx != -1) {
                result = asIngredient(visual.get(guiSlotIdx), 1);
            } else {
                return null;
            }
        }

        if (guiContainer instanceof GuiCraftAmount) {
            if (guiContainer.getSlotUnderMouse() != null) {
                final ItemStack shown = guiContainer.getSlotUnderMouse().getStack();
                final Object unwrapped = asIngredient(shown);
                result = unwrapped != null ? unwrapped : shown;
            } else {
                return null;
            }
        }

        if (result != null) {
            return result;
        }

        Slot slot = guiContainer.getSlotUnderMouse();
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

        return result;
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

    private int getSlotidx(AEBaseGui guiContainer, int mouseX, int mouseY, int rows) {
        int guileft = guiContainer.getGuiLeft();
        int guitop = guiContainer.getGuiTop();
        int currentScroll = guiContainer.getScrollBar().getCurrentScroll();
        final int xo = 9;
        final int yo = 19;

        int guiSlotx = (mouseX - guileft - xo) / 67;
        if (guiSlotx > 2 || mouseX < guileft + xo) return -1;
        int guiSloty = (mouseY - guitop - yo) / 23;
        if (guiSloty > (rows - 1) || mouseY < guitop + yo) return -1;
        return (guiSloty * 3) + guiSlotx + (currentScroll * 3);
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
        if (gui instanceof appeng.client.gui.implementations.GuiMEMonitorable) {
            List<Target<?>> pinTargets = ((appeng.client.gui.implementations.GuiMEMonitorable) gui)
                    .getPinGhostTargets(ingredient);
            targets.addAll((List<Target<I>>) (Object) pinTargets);
        }
        return targets;
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
        if (!(gui instanceof GuiUpgradeable || gui instanceof GuiPatternTerm || gui instanceof GuiExpandedProcessingPatternTerm)) {
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

    @Override
    public void onComplete() {
    }

    @Override
    public boolean shouldHighlightTargets() {
        return true;
    }

}
