package appeng.mixin.hei;

import java.awt.Rectangle;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.gui.ingredients.GuiIngredient;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.gui.recipes.RecipesGui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.render.StackSizeRenderer;

/**
 * Marks recipe ingredients the terminal that opened this recipe screen can already autocraft, the same
 * "+" the terminal itself uses on its own slots.
 * <p>
 * HEI has no public way to enumerate the recipes currently shown or to draw over one from outside its own
 * code - {@link RecipesGui} keeps its layout list private, and an ingredient's screen position exists only
 * as a rect relative to its layout, offset by the layout's own origin and the icon's padding.
 */
@Mixin(value = RecipesGui.class, remap = false)
public abstract class MixinRecipesGui extends GuiScreen {

    @Final
    @Shadow
    private List<RecipeLayout> recipeLayouts;

    @Shadow
    public abstract GuiScreen getParentScreen();

    // drawScreen is inherited from GuiScreen, so it needs remapping even though everything this mixin
    // touches inside it does not.
    @Inject(method = "drawScreen", at = @At(value = "INVOKE",
            target = "Lmezz/jei/gui/recipes/RecipeGuiTabs;draw(Lnet/minecraft/client/Minecraft;II)V",
            shift = At.Shift.AFTER, remap = false), remap = true)
    private void ae2ud$markCraftableIngredients(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!(this.getParentScreen() instanceof GuiMEMonitorable terminal)) {
            return;
        }

        final FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        for (final RecipeLayout layout : this.recipeLayouts) {
            markIngredients$AE2UD(terminal, fontRenderer, layout, layout.getItemStacks().getGuiIngredients());
            markIngredients$AE2UD(terminal, fontRenderer, layout, layout.getFluidStacks().getGuiIngredients());
        }
    }

    @Unique
    private static <T> void markIngredients$AE2UD(final GuiMEMonitorable terminal, final FontRenderer fontRenderer,
                                                  final RecipeLayout layout,
                                                  final Map<Integer, ? extends IGuiIngredient<T>> ingredients) {
        for (final IGuiIngredient<T> ingredient : ingredients.values()) {
            final AEKey key = toKey$AE2UD(ingredient.getDisplayedIngredient());
            if (key == null || !terminal.isDisplayedKeyCraftable(key) || !(ingredient instanceof GuiIngredient<?> concrete)) {
                continue;
            }

            final Rectangle rect = concrete.getRect();
            final AccessorGuiIngredient padding = (AccessorGuiIngredient) concrete;
            StackSizeRenderer.drawCraftableMark(fontRenderer,
                    layout.getPosX() + rect.x + padding.getXPadding(),
                    layout.getPosY() + rect.y + padding.getYPadding());
        }
    }

    @Unique
    @Nullable
    private static AEKey toKey$AE2UD(@Nullable final Object ingredient) {
        if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
            return AEItemKey.of(stack);
        }
        if (ingredient instanceof FluidStack stack) {
            return AEFluidKey.of(stack);
        }
        return null;
    }
}
