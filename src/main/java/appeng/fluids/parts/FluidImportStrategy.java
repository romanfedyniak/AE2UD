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
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;

/**
 * Fluid counterpart of {@code appeng.parts.automation.StorageImportStrategy}. Registered against
 * {@link appeng.api.stacks.AEKeyType#fluids()} through {@code appeng.parts.automation.InitStackWorldBehaviors} (see
 * that file's edit for wave 5), and reached only through that registry - the separate fluid import bus that
 * also used it directly was deleted once the generic bus could serve fluids.
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
        if (!context.hasOperationsLeft()) {
            return false;
        }

        IFluidHandler fh = getFluidHandler();
        if (fh == null) {
            return false;
        }

        // The budget is counted in operations, not millibuckets: every bus in the mod hands out the same
        // 1/8/32/64/96 operations per Speed card, and each key type says what one operation is worth. For
        // fluids that is 125 mB, so this reproduces the pre-port PartFluidImportBus' 125..12000 mB per tick
        // exactly, while an item bus tick over the same budget still moves 1..96 items.
        final int amountPerOperation = Math.max(1, AEKeyType.fluids().getAmountPerOperation());
        int maxDrain = (int) Math.min((long) context.getOperationsRemaining() * amountPerOperation, Integer.MAX_VALUE);
        FluidStack peek = fh.drain(maxDrain, false);
        if (peek == null || peek.amount <= 0) {
            return false;
        }

        AEFluidKey what = AEFluidKey.of(peek);
        if (what == null) {
            return false;
        }

        // The frozen AEKeyFilter, not the concrete context: this strategy runs on whatever context the bus
        // that owns it builds, and the generic PartImportBus builds its own. An empty bus filter matches
        // everything, and a fuzzy card is already baked into the partition list behind getFilter().
        if (!context.getFilter().matches(what)) {
            return false;
        }

        var internal = context.getInternalStorage();
        var source = context.getActionSource();

        long acceptable = internal.insert(what, peek.amount, Actionable.SIMULATE, source);
        if (acceptable <= 0) {
            return false;
        }

        // Drain by stack, not by amount: an untyped drain on a multi-tank block may hand back a different
        // fluid than the one peeked and filter-checked above. The pre-port bus drained by stack for the
        // same reason.
        FluidStack drained = fh.drain(what.toStack((int) Math.min(acceptable, Integer.MAX_VALUE)), true);
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

        // Spend whole operations, matching the unit the budget was handed out in. Anything that moved at
        // all costs at least one, so a dribble of fluid can never loop for free.
        context.reduceOperationsRemaining(Math.max(1, inserted / amountPerOperation));
        return inserted > 0;
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
