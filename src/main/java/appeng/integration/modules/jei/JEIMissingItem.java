package appeng.integration.modules.jei;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.util.Platform;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.gui.TooltipRenderer;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.gui.recipes.RecipeTransferButton;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static mezz.jei.api.recipe.transfer.IRecipeTransferError.Type.USER_FACING;

public class JEIMissingItem implements IRecipeTransferError {

    private boolean errored;
    public long lastUpdate;
    private final List<Integer> craftableSlots = new ArrayList<>();
    private final List<Integer> foundSlots = new ArrayList<>();

    /**
     * What the terminal can supply. Was an {@code IItemList<IAEItemStack>}; see {@link AvailableItems}
     * for why a {@link KeyCounter} cannot stand in for it.
     */
    AvailableItems available = new AvailableItems();

    /**
     * How much of each key this recipe has already claimed. This one <em>is</em> a plain amount count,
     * so it is a {@link KeyCounter}. The old code compared against a possibly-null {@code IAEItemStack}
     * and separately against its size; {@link KeyCounter#get} answers 0 for an absent key, so the two
     * cases collapse into one comparison with no change in behaviour.
     */
    KeyCounter used = new KeyCounter();

    JEIMissingItem(Container container, @Nonnull IRecipeLayout recipeLayout) {
        if (container instanceof ContainerMEMonitorable) {
            AvailableItems available = AvailableItems.merge((ContainerMEMonitorable) container);

            boolean found;
            this.errored = false;
            recipeLayout.getItemStacks().addTooltipCallback(new CraftableCallBack((ContainerMEMonitorable) container));

            KeyCounter used = new KeyCounter();
            for (IGuiIngredient<?> i : recipeLayout.getItemStacks().getGuiIngredients().values()) {
                found = false;
                if (i.isInput() && !i.getAllIngredients().isEmpty()) {
                    List<?> allIngredients = i.getAllIngredients();
                    for (Object allIngredient : allIngredients) {
                        if (allIngredient instanceof ItemStack) {
                            ItemStack stack = (ItemStack) allIngredient;
                            if (!stack.isEmpty()) {
                                AEItemKey search = AEItemKey.of(stack);
                                if (stack.getItem().isDamageable() || Platform.isGTDamageableItem(stack.getItem())) {
                                    Collection<AvailableItems.Entry> fuzzy = available.findFuzzy(search, FuzzyMode.IGNORE_ALL);
                                    if (!fuzzy.isEmpty()) {
                                        for (AvailableItems.Entry entry : fuzzy) {
                                            if (entry.amount() > 0) {
                                                if (Platform.isGTDamageableItem(stack.getItem())) {
                                                    if (!(stack.getMetadata() == ((AEItemKey) entry.what()).getDamage())) {
                                                        continue;
                                                    }
                                                }
                                                found = true;
                                                used.add(entry.what(), 1);
                                            }
                                        }
                                    }
                                } else {
                                    AvailableItems.Entry ext = available.findPrecise(search);
                                    if (ext != null) {
                                        if (ext.amount() > 0 && ext.amount() > used.get(ext.what())) {
                                            used.add(ext.what(), 1);
                                            found = true;
                                        }
                                    }
                                }
                            } else {
                                found = true;
                            }
                        }
                    }
                    if (!found) {
                        this.errored = true;
                        break;
                    }
                }
            }
        }
    }

    @Nonnull
    @Override
    public Type getType() {
        return USER_FACING;
    }

    @Override
    public void showError(Minecraft minecraft, int mouseX, int mouseY, @Nonnull IRecipeLayout recipeLayout, int recipeX, int recipeY) {
        Container c = minecraft.player.openContainer;
        if (c instanceof ContainerMEMonitorable container) {
            boolean found = false;
            boolean foundAny = false;
            boolean craftable = false;
            boolean foundAnyCraftable = false;
            int currentSlot = 0;
            this.errored = false;

            if (System.currentTimeMillis() - lastUpdate > 1000) {
                lastUpdate = System.currentTimeMillis();
                available = AvailableItems.merge(container);
                this.foundSlots.clear();
                this.craftableSlots.clear();
            } else {
                for (IGuiIngredient<?> i : recipeLayout.getItemStacks().getGuiIngredients().values()) {
                    if (i.isInput()) {
                        if (!foundSlots.contains(currentSlot)) {
                            if (craftableSlots.contains(currentSlot)) {
                                i.drawHighlight(minecraft, new Color(0.0f, 0.0f, 1.0f, 0.4f), recipeX, recipeY);
                            } else {
                                i.drawHighlight(minecraft, new Color(1.0f, 0.0f, 0.0f, 0.4f), recipeX, recipeY);
                            }
                        }
                    }
                    currentSlot++;
                }
                return;
            }
            this.used.reset();

            for (IGuiIngredient<?> i : recipeLayout.getItemStacks().getGuiIngredients().values()) {
                found = false;
                craftable = false;
                // Was an IItemList used purely as an ordered set of "stacks worth showing in this slot":
                // everything put in it had size 1, and resetStatus() below zeroed it so the render pass
                // skipped it. A LinkedHashSet says the same thing without the sizes.
                Set<AEKey> valid = new LinkedHashSet<>();
                if (i.isInput()) {
                    List<?> allIngredients = i.getAllIngredients();
                    for (Object allIngredient : allIngredients) {
                        if (allIngredient instanceof ItemStack stack) {
                            if (!stack.isEmpty()) {
                                AEItemKey search = AEItemKey.of(stack);
                                if (stack.getItem().isDamageable() || Platform.isGTDamageableItem(stack.getItem())) {
                                    Collection<AvailableItems.Entry> fuzzy = available.findFuzzy(search, FuzzyMode.IGNORE_ALL);
                                    if (!fuzzy.isEmpty()) {
                                        for (AvailableItems.Entry entry : fuzzy) {
                                            if (entry.amount() > 0) {
                                                if (Platform.isGTDamageableItem(stack.getItem())) {
                                                    if (!(stack.getMetadata() == ((AEItemKey) entry.what()).getDamage())) {
                                                        continue;
                                                    }
                                                }
                                                found = true;
                                                used.add(entry.what(), 1);
                                                valid.add(entry.what());
                                            } else {
                                                if (entry.craftable()) {
                                                    craftable = true;
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    AvailableItems.Entry ext = available.findPrecise(search);
                                    if (ext != null) {
                                        if (ext.amount() > 0 && used.get(ext.what()) < ext.amount()) {
                                            used.add(ext.what(), 1);
                                            if (craftable) {
                                                valid.clear();
                                            }
                                            valid.add(ext.what());
                                            found = true;
                                        } else if (ext.craftable()) {
                                            valid.add(ext.what());
                                            craftable = true;
                                        }
                                    }
                                }
                            } else {
                                found = true;
                            }
                        }
                    }
                    if (i.getAllIngredients().isEmpty()) {
                        currentSlot++;
                        continue;
                    }
                    ArrayList<ItemStack> validStacks = new ArrayList<>();
                    for (AEKey v : valid) {
                        // JEI can only be handed item stacks, so a non-item key shows through the same
                        // wrapper item the terminal uses for it.
                        ItemStack validStack = v.wrapForDisplayOrFilter().copy();
                        validStack.setCount(1);
                        validStacks.add(validStack);
                    }
                    if (!found) {
                        if (craftable) {
                            i.drawHighlight(minecraft, new Color(0.0f, 0.0f, 1.0f, 0.4f), recipeX, recipeY);
                            this.craftableSlots.add(currentSlot);
                            recipeLayout.getItemStacks().set(currentSlot, validStacks);
                            foundAnyCraftable = true;
                        } else {
                            i.drawHighlight(minecraft, new Color(1.0f, 0.0f, 0.0f, 0.4f), recipeX, recipeY);
                        }
                        this.errored = true;
                    } else {
                        foundAny = true;
                        this.foundSlots.add(currentSlot);
                        recipeLayout.getItemStacks().set(currentSlot, validStacks);
                    }
                }
                currentSlot++;
            }
            RecipeTransferButton b = ((RecipeLayout) recipeLayout).getRecipeTransferButton();
            if (b != null) {
                List<String> tooltipLines = new ArrayList<>();
                b.init(c, minecraft.player);
                if (errored && foundAny) {
                    tooltipLines.add(I18n.translateToLocal("gui.tooltips.appliedenergistics2.PartialTransfer"));
                    b.enabled = true;
                    b.visible = true;
                }
                if (errored) {
                    tooltipLines.add(I18n.translateToLocal("gui.tooltips.appliedenergistics2.MissingItem"));
                }
                if (foundAnyCraftable) {
                    tooltipLines.add(I18n.translateToLocal("gui.tooltips.appliedenergistics2.CraftableItem"));
                }
                TooltipRenderer.drawHoveringText(minecraft, tooltipLines, mouseX, mouseY);
            }
        }
    }

    public boolean errored() {
        return errored;
    }
}
