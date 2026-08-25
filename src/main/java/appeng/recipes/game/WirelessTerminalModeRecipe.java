package appeng.recipes.game;

import java.util.Collections;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;

import appeng.api.AEApi;
import appeng.api.features.IWirelessTerminalMode;
import appeng.helpers.WirelessTerminalModes;
import appeng.util.Platform;

/**
 * Adds one mode to a wireless terminal: the terminal and that mode's terminal part, in any arrangement.
 *
 * <p>The terminal that comes out is the one that went in - its charge, its link to the network, its upgrade
 * cards and everything each of its modes has stored. Only the list of modes it owns is longer.</p>
 */
public final class WirelessTerminalModeRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

    private final IWirelessTerminalMode mode;

    public WirelessTerminalModeRecipe(final IWirelessTerminalMode mode) {
        this.mode = mode;
    }

    @Override
    public boolean matches(final InventoryCrafting inv, final World world) {
        return !this.assemble(inv).isEmpty();
    }

    @Override
    public ItemStack getCraftingResult(final InventoryCrafting inv) {
        return this.assemble(inv);
    }

    /**
     * Empty whenever this is not the recipe, which includes a terminal that already owns the mode. Without
     * that last refusal the ingredient would be swallowed for nothing, and HEI would show a recipe whose
     * result is its own input.
     */
    private ItemStack assemble(final InventoryCrafting inv) {
        final ItemStack ingredient = this.mode.getUnlockIngredient();
        if (ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack terminal = ItemStack.EMPTY;
        boolean foundIngredient = false;

        for (int slot = 0; slot < inv.getSizeInventory(); slot++) {
            final ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (this.isTerminal(stack)) {
                if (!terminal.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                terminal = stack;
            } else if (Platform.itemComparisons().isEqualItemType(ingredient, stack)) {
                if (foundIngredient) {
                    return ItemStack.EMPTY;
                }
                foundIngredient = true;
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (terminal.isEmpty() || !foundIngredient) {
            return ItemStack.EMPTY;
        }

        final ItemStack result = terminal.copy();
        result.setCount(1);

        return WirelessTerminalModes.unlock(result, this.mode.getId()) ? result : ItemStack.EMPTY;
    }

    private boolean isTerminal(final ItemStack stack) {
        return AEApi.instance().definitions().items().wirelessTerminal().isSameAs(stack);
    }

    @Override
    public boolean canFit(final int width, final int height) {
        return width * height >= 2;
    }

    /**
     * A terminal owning nothing but this mode, so the recipe reads in HEI as the mode it grants rather than as
     * a terminal turning into itself.
     */
    @Override
    public ItemStack getRecipeOutput() {
        final ItemStack shown = this.terminalStack();
        if (!shown.isEmpty()) {
            WirelessTerminalModes.setUnlocked(shown, Collections.singletonList(this.mode.getId()));
            WirelessTerminalModes.setModeId(shown, this.mode.getId());
        }

        return shown;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        final NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.fromStacks(this.terminalStack()));
        ingredients.add(Ingredient.fromStacks(this.mode.getUnlockIngredient()));
        return ingredients;
    }

    private ItemStack terminalStack() {
        return AEApi.instance().definitions().items().wirelessTerminal().maybeStack(1)
                .orElse(ItemStack.EMPTY);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(final InventoryCrafting inv) {
        return NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}
