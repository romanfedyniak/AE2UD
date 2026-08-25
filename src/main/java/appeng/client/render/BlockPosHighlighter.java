package appeng.client.render;

import appeng.core.AEConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

// taken from McJty's McJtyLib
public class BlockPosHighlighter {
    private static List<BlockPos> hilightedBlocks = Collections.emptyList();
    private static long expireHilight;


    private static int dimension;

    public static void hilightBlock(BlockPos c, long expireHilight, int dimension) {
        hilightBlocks(c == null ? Collections.emptyList() : Collections.singletonList(c), expireHilight, dimension);
    }

    /**
     * Several at once, for when the thing being pointed at lives in more than one place - every interface
     * feeding the same kind of machine, say.
     */
    public static void hilightBlocks(Collection<BlockPos> blocks, long expireHilight, int dimension) {
        hilightedBlocks = new ArrayList<>(blocks);
        BlockPosHighlighter.expireHilight = expireHilight;
        BlockPosHighlighter.dimension = dimension;
    }

    public static List<BlockPos> getHilightedBlocks() {
        return hilightedBlocks;
    }

    public static BlockPos getHilightedBlock() {
        return hilightedBlocks.isEmpty() ? null : hilightedBlocks.get(0);
    }

    public static long getExpireHilight() {
        return expireHilight;
    }

    public static int getDimension() {
        return dimension;
    }

    /**
     * Points the player at a block just highlighted, so that what was asked for is in front of them rather
     * than somewhere behind. The nearest one, when several were highlighted at once.
     */
    public static void turnPlayerTowards(Collection<BlockPos> blocks) {
        if (blocks.isEmpty() || !AEConfig.instance().turnToHighlightedBlock()) {
            return;
        }

        final EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) {
            return;
        }

        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (final BlockPos block : blocks) {
            final double distance = player.getDistanceSq(block);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = block;
            }
        }

        final double dx = nearest.getX() + 0.5 - player.posX;
        final double dy = nearest.getY() + 0.5 - (player.posY + player.getEyeHeight());
        final double dz = nearest.getZ() + 0.5 - player.posZ;
        final double horizontal = Math.sqrt(dx * dx + dz * dz);

        // standing inside the block leaves no direction to face
        if (horizontal == 0 && dy == 0) {
            return;
        }

        final float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        final float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        // the previous angles as well, or the camera sweeps across for a tick on its way here
        player.rotationYaw = yaw;
        player.prevRotationYaw = yaw;
        player.rotationPitch = pitch;
        player.prevRotationPitch = pitch;

        player.setRotationYawHead(yaw);
        player.prevRotationYawHead = yaw;
        player.renderYawOffset = yaw;
        player.prevRenderYawOffset = yaw;
    }

    public static void turnPlayerTowards(BlockPos block) {
        turnPlayerTowards(block == null ? Collections.emptyList() : Collections.singletonList(block));
    }

}
