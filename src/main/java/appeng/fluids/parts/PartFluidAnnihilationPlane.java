package appeng.fluids.parts;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.behaviors.PickupStrategy;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.automation.PartAnnihilationPlane;
import appeng.parts.automation.PlaneModels;

/**
 * Fluid annihilation plane: drains an adjacent fluid source block into the network. This is the fluid counterpart
 * of the split "item vs fluid annihilation plane" fork shape (CONTRACT.md §5/§10) -- upstream AE2-original has no
 * such split, ours keeps it.
 * <p/>
 * Now extends the already-migrated, type-erased {@link PartAnnihilationPlane} instead of duplicating its whole
 * box/connection/tick/entity-collision machinery (as the pre-port class did by extending {@code PartBasicState}
 * directly): that base class is entirely key-type-agnostic, so the only thing this class needs to change is which
 * {@link PickupStrategy} it builds, exactly like {@code appeng.parts.automation.PartIdentityAnnihilationPlane}
 * substitutes its own strategy for the plain item one. Since {@link FluidPickupStrategy#canPickUpEntity} always
 * returns false, the base class's entity-collision handling (item-only) simply never matches here, matching the
 * pre-port class's lack of an {@code onEntityCollision} override.
 */
public class PartFluidAnnihilationPlane extends PartAnnihilationPlane {

    private static final PlaneModels MODELS = new PlaneModels("part/fluid_annihilation_plane_", "part/fluid_annihilation_plane_on_");

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    public PartFluidAnnihilationPlane(final ItemStack is) {
        super(is);
    }

    @Override
    protected List<PickupStrategy> createPickupStrategies(World world, BlockPos fromPos, EnumFacing fromSide,
            TileEntity host, Map<Enchantment, Integer> enchantments, @Nullable UUID owner) {
        return Collections.singletonList(new FluidPickupStrategy(world, fromPos, fromSide, host, enchantments, owner));
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.getConnections(), this.isPowered(), this.isActive());
    }
}
