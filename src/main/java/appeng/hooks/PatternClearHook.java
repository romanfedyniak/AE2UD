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
 * A shift right click, into the air or on a block, turns a pattern back into blank ones. AE2's own pattern does
 * it itself; this does it for the patterns addons encode in the pattern terminal, which are items of their own.
 */
public class PatternClearHook {

    @SubscribeEvent
    public void onRightClickItem(final PlayerInteractEvent.RightClickItem event) {
        if (this.clear(event)) {
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);
        }
    }

    /**
     * As AE2's own pattern does on a block: the client lets the click through so it reaches the server, and the
     * server clears the pattern instead of using the block.
     */
    @SubscribeEvent
    public void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        if (this.clear(event) && !event.getWorld().isRemote) {
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);
        }
    }

    private boolean clear(final PlayerInteractEvent event) {
        final EntityPlayer player = event.getEntityPlayer();
        final ItemStack stack = event.getItemStack();

        if (!player.isSneaking() || player instanceof FakePlayer || stack.getItem() instanceof ItemEncodedPattern
                || PatternEncodingModes.forPattern(stack) == null) {
            return false;
        }

        PatternOutputs.forget(stack);

        if (!player.world.isRemote) {
            final ItemStack blank = AEApi.instance().definitions().materials().blankPattern()
                    .maybeStack(stack.getCount()).orElse(ItemStack.EMPTY);
            if (!blank.isEmpty()) {
                player.setHeldItem(event.getHand(), blank);
            }
        }
        return true;
    }
}
