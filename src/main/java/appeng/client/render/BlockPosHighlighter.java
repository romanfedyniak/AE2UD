package appeng.client.render;

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

}
