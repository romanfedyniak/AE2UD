package appeng.util;

import net.minecraft.util.math.BlockPos;


public class BlockPosUtils {
    public static long getDistance(BlockPos blockPos, BlockPos blockPos2) {
        return Math.abs(blockPos.getX() - blockPos2.getX()) + Math.abs(blockPos.getY() - blockPos2.getY())
                + Math.abs(blockPos.getZ() - blockPos2.getZ());
    }
}
