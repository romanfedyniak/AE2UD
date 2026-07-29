package appeng.fluids.parts;


import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fluids.capability.IFluidHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.core.sync.GuiBridge;
import appeng.fluids.helper.IConfigurableFluidInventory;
import appeng.fluids.util.AEFluidInventory;
import appeng.fluids.util.IAEFluidInventory;
import appeng.fluids.util.IAEFluidTank;
import appeng.items.parts.PartModels;
import appeng.parts.automation.PartAbstractFormationPlane;
import appeng.parts.automation.PlaneModels;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.util.Platform;


/**
 * Fluid formation plane: places a fluid source block in the adjacent space. Fluid counterpart of the split
 * "item vs fluid formation plane" fork shape (CONTRACT.md §5/§10) -- upstream AE2-original merged these into one
 * type-erased part now that {@code MEStorage} isn't per-channel; ours stays split.
 * <p/>
 * <b>Two config inventories, for a reason.</b> {@link PartAbstractFormationPlane#getConfigInventory()} is frozen
 * (wave 3a, already committed) to return the concrete class {@link AppEngInternalAEInventory} -- not an
 * interface, and not swappable for a fluid-tank equivalent. Worse: that class's own {@code IItemHandler}
 * mutation surface ({@code insertItem}/{@code setStackInSlot}, already committed in wave 2) always builds its
 * slot content via {@code GenericStack.fromItemStack}, which can only ever produce an item-typed key -- so a
 * plain {@code AppEngInternalAEInventory} can never actually hold a fluid filter entry through normal
 * interaction, regardless of what gets wrapped into a slot's {@code ItemStack}. Meanwhile
 * {@code appeng.fluids.container.ContainerFluidFormationPlane} (5-C, already committed) still calls
 * {@code plane.getConfig(): IAEFluidTank} for its actual GUI, matching every other fluid part's shape.
 * <p/>
 * Resolution: {@link #config} (an {@link AEFluidInventory}) stays the real, GUI-facing 63-slot fluid filter,
 * unchanged from the pre-port shape and matching 5-C's container. {@link #filterView} is a second, purely
 * internal {@link AppEngInternalAEInventory} that exists only so the frozen, {@code final}
 * {@code updateFilter()} (which reads {@link #getConfigInventory()}) can see this plane's actual configured
 * fluids instead of always finding an empty filter -- which would have silently turned "restrict what this
 * plane places" into a permanently-disabled no-op, exactly the kind of regression CONTRACT.md rule 6 forbids.
 * {@link #filterView} is kept in sync with {@link #config} via {@link #syncFilterView()}, which round-trips
 * through {@code GenericStack.writeTag}/{@code AppEngInternalAEInventory#readFromNBT} -- both already generic
 * over any {@link AEKey} type -- rather than through the item-only {@code IItemHandler} surface. It is never
 * exposed to any GUI or capability.
 */
public class PartFluidFormationPlane extends PartAbstractFormationPlane implements IAEFluidInventory, IConfigurableFluidInventory {

    private static final PlaneModels MODELS = new PlaneModels("part/fluid_formation_plane_", "part/fluid_formation_plane_on_");

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    private final AEFluidInventory config = new AEFluidInventory(this, 63);
    private final AppEngInternalAEInventory filterView = new AppEngInternalAEInventory(null, 63);

    public PartFluidFormationPlane(final ItemStack is) {
        super(is);

        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.syncFilterView();
        this.updateFilter();
    }

    @Override
    protected AEKeyType getKeyType() {
        return AEKeyType.fluids();
    }

    /**
     * Refreshes the mirror before handing it out. {@code updateFilter()} is the only caller, and a stale
     * mirror there is invisible: an empty partition list means "accept everything", so the plane would
     * quietly place every fluid the network offered it. Syncing on read makes staleness impossible
     * instead of relying on every mutation path remembering to push.
     */
    @Override
    protected AppEngInternalAEInventory getConfigInventory() {
        this.syncFilterView();
        return this.filterView;
    }

    /**
     * Rebuilds {@link #filterView} from {@link #config}'s actual contents. See the class javadoc for why this
     * goes through an NBT round-trip instead of a direct slot-by-slot copy.
     */
    private void syncFilterView() {
        final NBTTagCompound wrapper = new NBTTagCompound();
        final NBTTagCompound slots = new NBTTagCompound();
        for (int i = 0; i < this.config.getSlots(); i++) {
            final NBTTagCompound slotTag = new NBTTagCompound();
            GenericStack.writeTag(slotTag, this.config.getFluidInSlot(i));
            slots.setTag("#" + i, slotTag);
        }
        wrapper.setTag("filter", slots);
        this.filterView.readFromNBT(wrapper, "filter");
    }

    @Override
    public void onFluidInventoryChanged(final IAEFluidTank inv, final int slot) {
        if (inv == this.config) {
            this.syncFilterView();
            this.updateFilter();
        }
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.config.readFromNBT(data, "config");
        this.syncFilterView();
        this.updateFilter();
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.config.writeToNBT(data, "config");
    }

    /**
     * The real, GUI-facing fluid filter. Called by the already-committed
     * {@code appeng.fluids.container.ContainerFluidFormationPlane#getFluidConfigInventory()}.
     */
    public IAEFluidTank getConfig() {
        return this.config;
    }

    @Override
    public IFluidHandler getFluidInventoryByName(final String name) {
        if (name.equals("config")) {
            return this.config;
        }
        return null;
    }

    @Override
    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.stateChanged();
    }

    @Override
    @MENetworkEventSubscribe
    public void chanRender(final MENetworkChannelsChanged changedChannels) {
        this.stateChanged();
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        if (Platform.isServer()) {
            Platform.openGUI(player, this.getHost().getTile(), this.getSide(), GuiBridge.GUI_FLUID_FORMATION_PLANE);
        }

        return true;
    }

    @Override
    public long insert(final AEKey what, final long amount, final Actionable mode, final IActionSource source) {
        if (this.blocked || what == null || what.getType() != AEKeyType.fluids() || amount <= 0) {
            return 0;
        }

        if (!this.matchesConfiguredFilter(what)) {
            return 0;
        }

        return this.getPlacementStrategies().placeInWorld(what, amount, mode, false);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.getConnections(), this.isPowered(), this.isActive());
    }

    @Override
    public ItemStack getItemStackRepresentation() {
        return AEApi.instance().definitions().parts().fluidFormationnPlane().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public GuiBridge getGuiBridge() {
        return GuiBridge.GUI_FLUID_FORMATION_PLANE;
    }
}
