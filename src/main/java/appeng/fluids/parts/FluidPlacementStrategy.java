package appeng.fluids.parts;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;

import appeng.api.behaviors.PlacementStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;

/**
 * Fluid placement for the formation plane: fills the adjacent space with a fluid source block, one bucket at a
 * time. This is the pre-port {@code PartFluidFormationPlane#injectItems} logic, moved off the part and behind the
 * frozen {@link PlacementStrategy} contract -- see {@code appeng.parts.automation.ItemPlacementStrategy} for the
 * item-side counterpart this mirrors. Unlike the item strategy there is no "place as entity" branch: fluids have
 * no free-floating entity form, exactly as the pre-port code assumed.
 */
public class FluidPlacementStrategy implements PlacementStrategy {

    private final World world;
    private final BlockPos pos;

    private boolean blocked = false;

    public FluidPlacementStrategy(World world, BlockPos pos, EnumFacing fromSide, TileEntity host, @Nullable UUID ownerUuid) {
        this.world = world;
        this.pos = pos;
    }

    @Override
    public void clearBlocked() {
        this.blocked = !canReplace();
    }

    @Override
    public long placeInWorld(AEKey what, long amount, Actionable type, boolean placeAsEntity) {
        if (this.blocked || !(what instanceof AEFluidKey fluidKey) || amount < AEFluidKey.AMOUNT_BUCKET) {
            // Need a full bucket, exactly like the pre-port check against Fluid.BUCKET_VOLUME.
            return 0;
        }

        if (!canReplace()) {
            this.blocked = true;
            return 0;
        }

        if (type == Actionable.MODULATE) {
            FluidStack fs = fluidKey.toStack(AEFluidKey.AMOUNT_BUCKET);
            FluidTank tank = new FluidTank(fs, AEFluidKey.AMOUNT_BUCKET);
            if (!FluidUtil.tryPlaceFluid(null, this.world, this.pos, tank, fs)) {
                return 0;
            }
        }

        return AEFluidKey.AMOUNT_BUCKET;
    }

    private boolean canReplace() {
        IBlockState state = this.world.getBlockState(this.pos);
        Block block = state.getBlock();
        return block.isReplaceable(this.world, this.pos) && !(block instanceof IFluidBlock) && !(block instanceof BlockLiquid)
                && !state.getMaterial().isLiquid();
    }
}
