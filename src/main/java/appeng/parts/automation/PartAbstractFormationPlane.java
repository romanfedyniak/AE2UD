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
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;

import appeng.api.AEApi;
import appeng.api.behaviors.PlacementStrategy;
import appeng.api.behaviors.StackWorldBehaviors;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Settings;
import appeng.api.upgrades.UpgradeCards;
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
 * Shared base of the formation planes. Replaces the old generic
 * {@code PartAbstractFormationPlane<T extends IAEStack<T>>}, which modelled the plane as an
 * {@code IMEInventory<T>} for exactly one storage channel.
 * <p>
 * A plane no longer serves one key type. {@link MEStorage} is type-erased, the filter holds
 * {@link appeng.api.stacks.GenericStack}s, and {@link PlacementStrategyFacade} routes each key to the
 * {@link PlacementStrategy} registered for <em>its</em> {@link AEKeyType} - so a plane places whatever
 * the world can receive, and a key type an addon registers works here with no change. This base used to
 * carry an abstract {@code getKeyType()} that {@link #insert} rejected everything else against, which
 * put per-type behaviour on the part instead of on the key type.
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
     * The plane's configured filter. Its slots hold {@link appeng.api.stacks.GenericStack}s, so any
     * {@link AEKeyType}'s keys can be stored there and {@link #updateFilter()} reads them back
     * type-erased.
     * <p/>
     * This used to be true only of the NBT surface: {@code setStackInSlot}/{@code insertItem} built
     * their slot content with {@code GenericStack.fromItemStack}, which can only yield an
     * {@link appeng.api.stacks.AEItemKey}, so a filter populated through the
     * {@link net.minecraftforge.items.IItemHandler} surface - i.e. through the GUI - could only ever
     * hold items. The fluids phase' stage 0 fixed that at the source:
     * {@code AppEngInternalAEInventory.toGenericStack} now unwraps a placeholder stack back into the key
     * it stands for, so a plane's filter accepts any key type from the GUI like every other filter in
     * the mod.
     */
    protected abstract AppEngInternalAEInventory getConfigInventory();

    /**
     * Number of configured slots that actually count towards the filter (the rest exist only to be
     * expanded into with a Capacity Card), mirroring the pre-port {@code slotsToUse} calculation.
     */
    protected int getFilterSlotsInUse() {
        return 18 + this.getInstalledCapacityPoints() * 9;
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
        if (this.getInstalledUpgrades(UpgradeCards.fuzzy()) > 0) {
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
        this.filterMode = this.getInstalledUpgrades(UpgradeCards.inverter()) > 0 ? IncludeExclude.BLACKLIST
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
        var player = AEApi.instance().registries().players().findPlayer(playerId);
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
        return new TextComponentString(this.getItemStackRepresentation().getDisplayName());
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
