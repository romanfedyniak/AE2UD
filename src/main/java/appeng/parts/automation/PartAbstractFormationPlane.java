package appeng.parts.automation;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.IBlockAccess;

import appeng.api.behaviors.PlacementStrategy;
import appeng.api.behaviors.StackWorldBehaviors;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.IConfigManager;
import appeng.helpers.IPriorityHost;
import appeng.me.GridAccessException;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.util.prioritylist.IPartitionList;

/**
 * Shared base of the formation planes (item, and -- once wave 5 adapts it -- fluid). Replaces the old
 * generic {@code PartAbstractFormationPlane<T extends IAEStack<T>>}, which modelled the plane as an
 * {@code IMEInventory<T>} for exactly one storage channel.
 * <p>
 * Under the new model {@link MEStorage} is not generic, so instead each concrete plane declares which
 * single {@link AEKeyType} it serves via {@link #getKeyType()} and this base rejects anything else in
 * {@link #insert}. AE2UD keeps item and fluid formation planes as separate part items/blocks (unlike
 * AE2-original, which merged them into one {@code FormationPlanePart} now that {@code MEStorage} is
 * type-erased); this base class is the shared skeleton both use, mirroring the pre-port split between
 * {@code PartAbstractFormationPlane} (shared) and {@code PartFormationPlane} (item-specific).
 * <p>
 * <b>Wave 5 note:</b> {@code appeng.fluids.parts.PartFluidFormationPlane} currently still extends the
 * old {@code PartAbstractFormationPlane<IAEFluidStack>}. It must be changed to extend this
 * (non-generic) class, implement {@link #getKeyType()} returning {@code AEKeyType.fluids()}, and
 * implement {@link #insert} using its own fluid placement logic (see {@link PartFormationPlane} for
 * the item-side template).
 */
public abstract class PartAbstractFormationPlane extends PartUpgradeable implements IStorageProvider, IPriorityHost, MEStorage {

    private boolean wasActive = false;
    private int priority = 0;
    protected boolean blocked = false;

    @Nullable
    private PlacementStrategy placementStrategy;
    private IncludeExclude filterMode = IncludeExclude.WHITELIST;
    @Nullable
    private IPartitionList filter;

    public PartAbstractFormationPlane(ItemStack is) {
        super(is);
    }

    /**
     * @return the single {@link AEKeyType} this plane instance places. Anything else is rejected by
     *         {@link #insert}.
     */
    protected abstract AEKeyType getKeyType();

    /**
     * The plane's configured filter, as an {@link AppEngInternalAEInventory}. Its slots hold
     * {@link appeng.api.stacks.GenericStack}s, so any {@link AEKeyType}'s keys can be <i>stored</i>
     * there and {@link #updateFilter()} reads them back type-erased.
     * <p/>
     * <b>But they cannot be put there through the {@link net.minecraftforge.items.IItemHandler}
     * surface.</b> {@code AppEngInternalAEInventory.setStackInSlot}/{@code insertItem} build the slot
     * content with {@code GenericStack.fromItemStack}, which always yields an
     * {@link appeng.api.stacks.AEItemKey} - it does <b>not</b> unwrap a wrapped placeholder stack, so a
     * key that arrived via {@code GenericStack.wrapInItemStack} comes back out as the placeholder item
     * itself, not as the key it stood for. A plane whose filter is populated by item-handler writes can
     * therefore only ever filter on items.
     * <p/>
     * Implementations for a non-item key type must populate this inventory some other way. The one in
     * the tree, {@code appeng.fluids.parts.PartFluidFormationPlane}, keeps its real GUI-facing
     * {@code AEFluidInventory} and mirrors it into a private instance of this class through
     * {@code GenericStack.writeTag}/{@code readFromNBT}, both of which are type-erased. Getting this
     * wrong is silent: the filter simply matches nothing and the plane places everything.
     */
    protected abstract AppEngInternalAEInventory getConfigInventory();

    /**
     * Number of configured slots that actually count towards the filter (the rest exist only to be
     * expanded into with a Capacity Card), mirroring the pre-port {@code slotsToUse} calculation.
     */
    protected int getFilterSlotsInUse() {
        return 18 + this.getInstalledUpgrades(Upgrades.CAPACITY) * 9;
    }

    @Override
    protected int getUpgradeSlots() {
        return 5;
    }

    @Override
    public void upgradesChanged() {
        this.updateFilter();
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        this.updateFilter();
        this.getHost().markForSave();
    }

    protected final void updateFilter() {
        var builder = IPartitionList.builder();
        if (this.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
            builder.fuzzyMode((FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE));
        }
        var config = getConfigInventory();
        int slotsToUse = getFilterSlotsInUse();
        for (int x = 0; x < config.getSlots() && x < slotsToUse; x++) {
            var stack = config.getAEStackInSlot(x);
            if (stack != null) {
                builder.add(stack.what());
            }
        }
        this.filter = builder.build();
        this.filterMode = this.getInstalledUpgrades(Upgrades.INVERTER) > 0 ? IncludeExclude.BLACKLIST
                : IncludeExclude.WHITELIST;
    }

    protected final void remountStorage() {
        IStorageProvider.requestUpdate(this.getProxy().getNode());
    }

    public void stateChanged() {
        final boolean currentActive = this.getProxy().isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            this.remountStorage();
            this.getHost().markForUpdate();
        }
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        int minX = 1;
        int minY = 1;
        int maxX = 15;
        int maxY = 15;

        final IPartHost host = this.getHost();
        if (host != null) {
            final TileEntity te = host.getTile();
            final BlockPos pos = te.getPos();
            final EnumFacing e = bch.getWorldX();
            final EnumFacing u = bch.getWorldY();

            if (this.isTransitionPlane(te.getWorld().getTileEntity(pos.offset(e.getOpposite())), this.getSide())) {
                minX = 0;
            }
            if (this.isTransitionPlane(te.getWorld().getTileEntity(pos.offset(e)), this.getSide())) {
                maxX = 16;
            }
            if (this.isTransitionPlane(te.getWorld().getTileEntity(pos.offset(u.getOpposite())), this.getSide())) {
                minY = 0;
            }
            if (this.isTransitionPlane(te.getWorld().getTileEntity(pos.offset(u)), this.getSide())) {
                maxY = 16;
            }
        }

        bch.addBox(5, 5, 14, 11, 11, 15);
        bch.addBox(minX, minY, 15, maxX, maxY, 16);
    }

    public PlaneConnections getConnections() {
        final EnumFacing facingRight, facingUp;
        AEPartLocation location = this.getSide();
        switch (location) {
            case UP:
                facingRight = EnumFacing.EAST;
                facingUp = EnumFacing.NORTH;
                break;
            case DOWN:
                facingRight = EnumFacing.WEST;
                facingUp = EnumFacing.NORTH;
                break;
            case NORTH:
                facingRight = EnumFacing.WEST;
                facingUp = EnumFacing.UP;
                break;
            case SOUTH:
                facingRight = EnumFacing.EAST;
                facingUp = EnumFacing.UP;
                break;
            case WEST:
                facingRight = EnumFacing.SOUTH;
                facingUp = EnumFacing.UP;
                break;
            case EAST:
                facingRight = EnumFacing.NORTH;
                facingUp = EnumFacing.UP;
                break;
            default:
            case INTERNAL:
                return PlaneConnections.of(false, false, false, false);
        }

        boolean left = false, right = false, down = false, up = false;

        final IPartHost host = this.getHost();
        if (host != null) {
            final TileEntity te = host.getTile();
            final BlockPos pos = te.getPos();

            if (this.isTransitionPlane(te.getWorld().getTileEntity(pos.offset(facingRight.getOpposite())), this.getSide())) {
                left = true;
            }
            if (this.isTransitionPlane(te.getWorld().getTileEntity(pos.offset(facingRight)), this.getSide())) {
                right = true;
            }
            if (this.isTransitionPlane(te.getWorld().getTileEntity(pos.offset(facingUp.getOpposite())), this.getSide())) {
                down = true;
            }
            if (this.isTransitionPlane(te.getWorld().getTileEntity(pos.offset(facingUp)), this.getSide())) {
                up = true;
            }
        }

        return PlaneConnections.of(up, right, down, left);
    }

    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        if (pos.offset(this.getSide().getFacing()).equals(neighbor)) {
            final TileEntity te = this.getHost().getTile();
            final AEPartLocation side = this.getSide();
            final BlockPos tePos = te.getPos().offset(side.getFacing());

            this.blocked = !w.getBlockState(tePos).getBlock().isReplaceable(w, tePos);
            if (this.placementStrategy != null) {
                this.placementStrategy.clearBlocked();
            }
        }
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 1;
    }

    protected boolean isTransitionPlane(final TileEntity blockTileEntity, final AEPartLocation side) {
        if (blockTileEntity instanceof IPartHost) {
            final IPart p = ((IPartHost) blockTileEntity).getPart(side);
            return p != null && this.getClass() == p.getClass();
        }
        return false;
    }

    /**
     * Lazily built once the node exists; cleared whenever the plane leaves/rejoins a grid (a new grid
     * means a potentially different owning player).
     */
    protected final PlacementStrategy getPlacementStrategies() {
        if (this.placementStrategy == null) {
            var node = this.getProxy().getNode();
            if (node == null) {
                return PlacementStrategy.noop();
            }

            final TileEntity self = this.getHost().getTile();
            final BlockPos fromPos = self.getPos().offset(this.getSide().getFacing());
            final EnumFacing fromSide = this.getSide().getFacing().getOpposite();
            final UUID owner = resolveOwnerUuid(node.getPlayerID());

            Map<AEKeyType, PlacementStrategy> strategies = StackWorldBehaviors.createPlacementStrategies(
                    self.getWorld(), fromPos, fromSide, self, owner);
            this.placementStrategy = new PlacementStrategyFacade(strategies);
        }
        return this.placementStrategy;
    }

    @Nullable
    private UUID resolveOwnerUuid(int playerId) {
        var player = appeng.api.AEApi.instance().registries().players().findPlayer(playerId);
        return player != null ? player.getGameProfile().getId() : null;
    }

    // --- MEStorage: only insert() is meaningfully implemented, matching the pre-port
    // IMEInventory<T>#extractItems()/getAvailableItems() no-ops. insert() itself stays abstract here
    // (like the pre-port left injectItems() abstract) because it needs the concrete subclass' own
    // settings (e.g. Settings.PLACE_BLOCK only makes sense for item-shaped keys).

    @Override
    public abstract long insert(AEKey what, long amount, Actionable mode, IActionSource source);

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        // The plane doesn't stock anything of its own -- nothing to report.
    }

    @Override
    public ITextComponent getDescription() {
        return this.getItemStackRepresentation().getDisplayName();
    }

    /**
     * @return true if the key is accepted by the plane's own filter/whitelist configuration (not
     *         whether the world can actually receive it -- that's for the placement strategy to say).
     */
    protected boolean matchesConfiguredFilter(AEKey what) {
        return this.filter == null || this.filter.matchesFilter(what, this.filterMode);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.priority = data.getInteger("priority");
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("priority", this.getPriority());
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int newValue) {
        this.priority = newValue;
        this.getHost().markForSave();
        this.remountStorage();
    }

    @Override
    public void mountInventories(final IStorageMounts mounts) {
        if (this.getProxy().isActive()) {
            this.updateFilter();
            mounts.mount(this, this.priority);
        }
    }
}
