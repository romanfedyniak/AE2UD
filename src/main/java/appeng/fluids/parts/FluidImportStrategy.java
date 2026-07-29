package appeng.fluids.parts;

import javax.annotation.Nullable;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import appeng.api.behaviors.StackImportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.util.prioritylist.IPartitionList;

/**
 * Fluid counterpart of {@code appeng.parts.automation.StorageImportStrategy}. Registered against
 * {@link appeng.api.stacks.AEKeyType#fluids()} through {@code appeng.parts.automation.InitStackWorldBehaviors} (see
 * that file's edit for wave 5), and also used directly by {@link PartFluidImportBus} for its own,
 * fluids-only import bus.
 * <p/>
 * Unlike the item version this does one bounded transfer per call instead of looping in
 * 64-unit chunks: the pre-port {@code PartFluidImportBus#doBusWork} always drained a single, bounded amount (in
 * millibuckets) from the target once per tick -- fluids were never chunked into discrete "operations" the way item
 * stacks are, so this mirrors that shape rather than the item bus' per-operation loop.
 */
public class FluidImportStrategy implements StackImportStrategy {

    private final World world;
    private final BlockPos fromPos;
    private final EnumFacing fromSide;

    FluidImportStrategy(World world, BlockPos fromPos, EnumFacing fromSide) {
        this.world = world;
        this.fromPos = fromPos;
        this.fromSide = fromSide;
    }

    @Override
    public boolean transfer(StackTransferContext context) {
        if (!(context instanceof FluidTransferContext ctx) || !context.hasOperationsLeft()) {
            return false;
        }

        IFluidHandler fh = getFluidHandler();
        if (fh == null) {
            return false;
        }

        int maxDrain = (int) Math.min(context.getOperationsRemaining(), Integer.MAX_VALUE);
        FluidStack peek = fh.drain(maxDrain, false);
        if (peek == null || peek.amount <= 0) {
            return false;
        }

        AEFluidKey what = AEFluidKey.of(peek);
        if (what == null) {
            return false;
        }

        IPartitionList partitionList = ctx.getPartitionList();
        if (!partitionList.isEmpty() && !matchesFilter(what, partitionList, ctx.getFuzzyMode())) {
            return false;
        }

        var internal = context.getInternalStorage();
        var source = context.getActionSource();

        long acceptable = internal.insert(what, peek.amount, Actionable.SIMULATE, source);
        if (acceptable <= 0) {
            return false;
        }

        FluidStack drained = fh.drain((int) Math.min(acceptable, Integer.MAX_VALUE), true);
        if (drained == null || drained.amount <= 0) {
            return false;
        }

        long inserted = internal.insert(what, drained.amount, Actionable.MODULATE, source);
        if (inserted < drained.amount) {
            // Be lenient rather than void fluid: try to hand back whatever didn't fit, mirroring the
            // "unpowered fallback insert" leniency the item-side StorageImportStrategy uses.
            long leftover = drained.amount - inserted;
            fh.fill(what.toStack((int) leftover), true);
        }

        context.reduceOperationsRemaining(inserted);
        return inserted > 0;
    }

    private boolean matchesFilter(AEKey what, IPartitionList partitionList, @Nullable FuzzyMode fuzzyMode) {
        if (fuzzyMode != null) {
            for (AEKey listed : partitionList.getItems()) {
                if (what.fuzzyEquals(listed, fuzzyMode)) {
                    return true;
                }
            }
            return false;
        }
        return partitionList.isListed(what);
    }

    @Nullable
    private IFluidHandler getFluidHandler() {
        TileEntity target = getTileEntity();
        return target == null ? null : target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, this.fromSide);
    }

    @Nullable
    private TileEntity getTileEntity() {
        if (this.world.getChunkProvider().getLoadedChunk(this.fromPos.getX() >> 4, this.fromPos.getZ() >> 4) == null) {
            return null;
        }
        return this.world.getTileEntity(this.fromPos);
    }

    public static StackImportStrategy createFluid(World world, BlockPos fromPos, EnumFacing fromSide) {
        return new FluidImportStrategy(world, fromPos, fromSide);
    }
}
