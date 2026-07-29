package appeng.fluids.parts;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.IFluidHandler;

import appeng.api.behaviors.PickupSink;
import appeng.api.behaviors.PickupStrategy;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.util.AEPartLocation;
import appeng.core.AppEng;
import appeng.core.sync.packets.PacketTransitionEffect;

/**
 * Fluid pickup for the annihilation plane: drains an adjacent fluid source block. This is the pre-port
 * {@code PartFluidAnnihilationPlane#pickupFluid}/{@code #storeFluid} logic, moved off the part and behind the
 * frozen {@link PickupStrategy} contract -- see {@code appeng.parts.automation.ItemPickupStrategy} for the
 * item-side counterpart this mirrors. Fluids have no entity form in this fork, so
 * {@link #canPickUpEntity(Entity)}/{@link #pickUpEntity} are always no-ops, exactly like the pre-port class (which
 * never overrode {@code onEntityCollision}).
 */
public class FluidPickupStrategy implements PickupStrategy {

    private final World world;
    private final BlockPos pos;
    private final EnumFacing side;

    private boolean isAccepting = true;

    public FluidPickupStrategy(World world, BlockPos pos, EnumFacing side, TileEntity host,
            Map<Enchantment, Integer> enchantments, @Nullable UUID ownerUuid) {
        this.world = world;
        this.pos = pos;
        this.side = side;
    }

    @Override
    public void reset() {
        this.isAccepting = true;
    }

    @Override
    public boolean canPickUpEntity(Entity entity) {
        return false;
    }

    @Override
    public boolean pickUpEntity(IEnergySource energySource, PickupSink sink, Entity entity) {
        return false;
    }

    @Override
    public Result tryPickup(IEnergySource energySource, PickupSink sink) {
        if (!this.isAccepting) {
            return Result.CANT_PICKUP;
        }

        IBlockState state = this.world.getBlockState(this.pos);
        Block block = state.getBlock();
        if (!(block instanceof IFluidBlock) && !(block instanceof BlockLiquid)) {
            return Result.CANT_PICKUP;
        }

        IFluidHandler fh = FluidUtil.getFluidHandler(this.world, this.pos, null);
        if (fh == null) {
            return Result.CANT_PICKUP;
        }

        FluidStack peek = fh.drain(Integer.MAX_VALUE, false);
        AEFluidKey what = AEFluidKey.of(peek);
        if (what == null) {
            return Result.CANT_PICKUP;
        }

        long amount = peek.amount;
        // Mirrors the pre-port formula exactly: stackSize / min(1, channel.transferFactor()). AEFluidKeyType's
        // getAmountPerOperation() is the renamed transferFactor() (see appeng.tile.misc.CondenserItemInventory's
        // javadoc) and is always >= 1 for fluids, so this reduces to "1 power per millibucket" either way.
        float requiredPower = amount / Math.min(1.0f, what.getAmountPerOperation());

        boolean hasPower = energySource.extractAEPower(requiredPower, Actionable.SIMULATE, PowerMultiplier.CONFIG) >= requiredPower - 0.01;
        long simulated = sink.insert(what, amount, Actionable.SIMULATE);
        boolean canStore = simulated >= amount;

        if (!hasPower || !canStore) {
            this.isAccepting = false;
            return Result.CANT_STORE;
        }

        energySource.extractAEPower(requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
        FluidStack drained = fh.drain(Integer.MAX_VALUE, true);
        AEFluidKey drainedWhat = AEFluidKey.of(drained);
        if (drainedWhat != null) {
            sink.insert(drainedWhat, drained.amount, Actionable.MODULATE);
        }

        AEPartLocation partSide = AEPartLocation.fromFacing(this.side.getOpposite());
        AppEng.proxy.sendToAllNearExcept(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(), 64, this.world,
                new PacketTransitionEffect(this.pos.getX(), this.pos.getY(), this.pos.getZ(), partSide, true));

        return Result.PICKED_UP;
    }
}
