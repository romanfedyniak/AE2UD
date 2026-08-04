package appeng.mixin.hei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import mezz.jei.gui.ingredients.GuiIngredient;

/**
 * The padding between an ingredient's rect and the icon drawn inside it - 1 for item slots, 0 for fluids.
 * HEI keeps it private and only applies it while drawing, so anything drawing over an icon from outside
 * has to read it to land on the icon rather than on its border.
 */
@Mixin(value = GuiIngredient.class, remap = false)
public interface AccessorGuiIngredient {

    @Accessor("xPadding")
    int getXPadding();

    @Accessor("yPadding")
    int getYPadding();
}
