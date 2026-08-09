package appeng.mixin.minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import appeng.client.render.crafting.EncodedPatternPreview;

/**
 * The same preview {@link MixinGuiContainer} draws in a slot, for a pattern held in hand, dropped on the
 * ground or hung in an item frame.
 * <p>
 * Both overloads are swapped because both resolve the model themselves, and the model has to be resolved
 * from the output rather than from the pattern: the two-argument one draws the ground and item frames, the
 * four-argument one draws whatever an entity is holding. The screen path reaches neither of them.
 */
@Mixin(RenderItem.class)
public class MixinRenderItem {

    @ModifyVariable(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V",
            at = @At("HEAD"), argsOnly = true)
    private ItemStack ae2ud$previewInWorld(final ItemStack stack) {
        return ae2ud$preview(stack);
    }

    @ModifyVariable(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
            at = @At("HEAD"), argsOnly = true)
    private ItemStack ae2ud$previewInHand(final ItemStack stack) {
        return ae2ud$preview(stack);
    }

    private static ItemStack ae2ud$preview(final ItemStack stack) {
        final ItemStack output = EncodedPatternPreview.previewFor(stack);
        return output.isEmpty() ? stack : output;
    }
}
