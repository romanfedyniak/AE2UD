package appeng.fluids.parts;

import javax.annotation.Nullable;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;

/**
 * Fluid counterpart of {@code appeng.parts.automation.StorageExportStrategy}. Registered against
 * {@link appeng.api.stacks.AEKeyType#fluids()} through {@code appeng.parts.automation.InitStackWorldBehaviors} and
 * reached only through that registry, now that the separate fluid export bus is gone. Mirrors the pre-port
 * {@code PartFluidExportBus#doBusWork}'s simulate-then-fill-then-extract sequence.
 */
public class FluidExportStrategy implements StackExportStrategy {

    private final World world;
    private final BlockPos fromPos;
    private final EnumFacing fromSide;

    FluidExportStrategy(World world, BlockPos fromPos, EnumFacing fromSide) {
        this.world = world;
        this.fromPos = fromPos;
        this.fromSide = fromSide;
    }

    @Override
    public long transfer(StackTransferContext context, AEKey what, long maxAmount) {
        if (!(what instanceof AEFluidKey fluidKey) || maxAmount <= 0) {
            return 0;
        }

        IFluidHandler fh = getFluidHandler();
        if (fh == null) {
            return 0;
        }

        int amount = (int) Math.min(maxAmount, Integer.MAX_VALUE);

        var internal = context.getInternalStorage();
        var source = context.getActionSource();

        long extractedSim = internal.extract(fluidKey, amount, Actionable.SIMULATE, source);
        if (extractedSim <= 0) {
            return 0;
        }

        int accepted = fh.fill(fluidKey.toStack((int) extractedSim), false);
        if (accepted <= 0) {
            return 0;
        }

        long extracted = internal.extract(fluidKey, accepted, Actionable.MODULATE, source);
        if (extracted <= 0) {
            return 0;
        }

        int reallyFilled = fh.fill(fluidKey.toStack((int) extracted), true);
        if (reallyFilled < extracted) {
            // Spill whatever couldn't actually be pushed back into the network rather than voiding it.
            internal.insert(fluidKey, extracted - reallyFilled, Actionable.MODULATE, source);
        }

        return reallyFilled;
    }

    @Override
    public long push(AEKey what, long maxAmount, Actionable mode) {
        if (!(what instanceof AEFluidKey fluidKey) || maxAmount <= 0) {
            return 0;
        }

        IFluidHandler fh = getFluidHandler();
        if (fh == null) {
            return 0;
        }

        int amount = (int) Math.min(maxAmount, Integer.MAX_VALUE);
        FluidStack stack = fluidKey.toStack(amount);
        return fh.fill(stack, mode == Actionable.MODULATE);
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

    public static StackExportStrategy createFluid(World world, BlockPos fromPos, EnumFacing fromSide) {
        return new FluidExportStrategy(world, fromPos, fromSide);
    }
}
