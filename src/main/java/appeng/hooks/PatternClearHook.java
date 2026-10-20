package appeng.hooks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import appeng.api.AEApi;
import appeng.api.patterns.PatternEncodingModes;
import appeng.helpers.PatternOutputs;
import appeng.items.misc.ItemEncodedPattern;

/**
 * A shift right click into the air turns a pattern back into blank ones. AE2's own pattern does it itself; this
 * does it for the patterns addons encode in the pattern terminal, which are items of their own.
 */
public class PatternClearHook {

    @SubscribeEvent
    public void onRightClickItem(final PlayerInteractEvent.RightClickItem event) {
        final EntityPlayer player = event.getEntityPlayer();
        final ItemStack stack = event.getItemStack();

        if (!player.isSneaking() || player instanceof FakePlayer || stack.getItem() instanceof ItemEncodedPattern
                || PatternEncodingModes.forPattern(stack) == null) {
            return;
        }

        PatternOutputs.forget(stack);
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);

        if (!player.world.isRemote) {
            final ItemStack blank = AEApi.instance().definitions().materials().blankPattern()
                    .maybeStack(stack.getCount()).orElse(ItemStack.EMPTY);
            if (!blank.isEmpty()) {
                player.setHeldItem(event.getHand(), blank);
            }
        }
    }
}
