package appeng.fluids.parts;


import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.IFluidHandler;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.IConfigManager;
import appeng.core.AppEng;
import appeng.core.sync.GuiBridge;
import appeng.fluids.helper.IConfigurableFluidInventory;
import appeng.fluids.util.AEFluidInventory;
import appeng.fluids.util.IAEFluidInventory;
import appeng.fluids.util.IAEFluidTank;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.parts.PartModel;
import appeng.parts.automation.PartUpgradeable;
import appeng.util.Platform;


/**
 * Fluid level emitter. Reports the network's stock of one configured fluid (or the network total, unconfigured)
 * as a redstone signal.
 * <p/>
 * Narrower feature set than {@code appeng.parts.automation.PartLevelEmitter} on purpose: the pre-port fluid level
 * emitter never supported {@code Settings.LEVEL_TYPE}/energy-level watching, {@code Settings.CRAFT_VIA_REDSTONE}
 * or {@code Upgrades.FUZZY} filtering -- only {@code Settings.REDSTONE_EMITTER} plus a single exact configured
 * fluid (or none, meaning "whole network"). Preserved exactly rather than expanded to match the item emitter,
 * since adding those would be new functionality, not a migration of an existing mechanic.
 * <p/>
 * The old "register as a whole-monitor listener when unconfigured" mechanism ({@code IMEMonitorHandlerReceiver},
 * deleted) is replaced by {@link IStackWatcher#setWatchAll(boolean)} -- exactly the case CONTRACT.md §10 designed
 * that method for.
 */
public class PartFluidLevelEmitter extends PartUpgradeable implements IStorageWatcherNode, IAEFluidInventory, IConfigurableFluidInventory {
    @PartModels
    public static final ResourceLocation MODEL_BASE_OFF = new ResourceLocation(AppEng.MOD_ID, "part/level_emitter_base_off");
    @PartModels
    public static final ResourceLocation MODEL_BASE_ON = new ResourceLocation(AppEng.MOD_ID, "part/level_emitter_base_on");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_OFF = new ResourceLocation(AppEng.MOD_ID, "part/level_emitter_status_off");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_ON = new ResourceLocation(AppEng.MOD_ID, "part/level_emitter_status_on");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_HAS_CHANNEL = new ResourceLocation(AppEng.MOD_ID, "part/level_emitter_status_has_channel");

    public static final PartModel MODEL_OFF_OFF = new PartModel(MODEL_BASE_OFF, MODEL_STATUS_OFF);
    public static final PartModel MODEL_OFF_ON = new PartModel(MODEL_BASE_OFF, MODEL_STATUS_ON);
    public static final PartModel MODEL_OFF_HAS_CHANNEL = new PartModel(MODEL_BASE_OFF, MODEL_STATUS_HAS_CHANNEL);
    public static final PartModel MODEL_ON_OFF = new PartModel(MODEL_BASE_ON, MODEL_STATUS_OFF);
    public static final PartModel MODEL_ON_ON = new PartModel(MODEL_BASE_ON, MODEL_STATUS_ON);
    public static final PartModel MODEL_ON_HAS_CHANNEL = new PartModel(MODEL_BASE_ON, MODEL_STATUS_HAS_CHANNEL);

    private static final int FLAG_ON = 4;

    private boolean prevState = false;
    private long lastReportedValue = 0;
    private long reportingValue = 0;
    private long lastWatcherRescanTick = -1;
    private IStackWatcher stackWatcher = null;
    private final AEFluidInventory config = new AEFluidInventory(this, 1);

    public PartFluidLevelEmitter(ItemStack is) {
        super(is);

        this.getConfigManager().registerSetting(Settings.REDSTONE_EMITTER, RedstoneMode.HIGH_SIGNAL);
    }

    public long getReportingValue() {
        return this.reportingValue;
    }

    public void setReportingValue(final long v) {
        this.reportingValue = v;
        this.updateState();
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {
        this.configureWatchers();
    }

    @Override
    public void updateWatcher(IStackWatcher newWatcher) {
        this.stackWatcher = newWatcher;
        this.configureWatchers();
    }

    @Override
    public void onStackChange(final AEKey what, final long amount) {
        final AEKey myStack = this.getConfiguredKey();

        if (myStack != null && what.equals(myStack)) {
            this.lastReportedValue = amount;
            this.updateState();
            return;
        }

        // Either the whole network is being watched (no filter configured) -- a single change has to trigger a
        // full rescan. Guard against rescanning more than once per tick when many keys change at once, same idea
        // as the pre-port "once per tick" concern the old listener model had implicitly.
        final TileEntity te = this.getHost() != null ? this.getHost().getTile() : null;
        final long tick = te != null && te.getWorld() != null ? te.getWorld().getTotalWorldTime() : -1;
        if (tick == this.lastWatcherRescanTick) {
            return;
        }
        this.lastWatcherRescanTick = tick;

        try {
            this.updateReportingValue(this.getProxy().getStorage());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    @Override
    public void onFluidInventoryChanged(IAEFluidTank inv, int slot) {
        this.configureWatchers();
    }

    @Override
    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange powerEvent) {
        if (this.getProxy().isActive()) {
            onListUpdate();
        }
        this.updateState();
    }

    @Override
    @MENetworkEventSubscribe
    public void chanRender(final MENetworkChannelsChanged c) {
        if (this.getProxy().isActive()) {
            onListUpdate();
        }
        this.updateState();
    }

    @Override
    public int isProvidingStrongPower() {
        return this.prevState ? 15 : 0;
    }

    @Override
    public int isProvidingWeakPower() {
        return this.prevState ? 15 : 0;
    }

    @Override
    protected int populateFlags(final int cf) {
        return cf | (this.prevState ? FLAG_ON : 0);
    }

    private void onListUpdate() {
        try {
            this.updateReportingValue(this.getProxy().getStorage());
        } catch (final GridAccessException e) {
            // ;P
        }
    }

    private void updateState() {
        final boolean isOn = this.isLevelEmitterOn();
        if (this.prevState != isOn) {
            this.getHost().markForUpdate();
            final TileEntity te = this.getHost().getTile();
            this.prevState = isOn;
            Platform.notifyBlocksOfNeighbors(te.getWorld(), te.getPos());
            Platform.notifyBlocksOfNeighbors(te.getWorld(), te.getPos().offset(this.getSide().getFacing()));
        }
    }

    @Nullable
    private AEKey getConfiguredKey() {
        final GenericStack stack = this.config.getFluidInSlot(0);
        return stack != null ? stack.what() : null;
    }

    private void configureWatchers() {
        final AEKey myStack = this.getConfiguredKey();

        if (this.stackWatcher != null) {
            this.stackWatcher.reset();
            if (myStack != null) {
                this.stackWatcher.setWatchAll(false);
                this.stackWatcher.add(myStack);
            } else {
                // Replaces the pre-port "register as a listener on the whole monitor" path -- see class javadoc.
                this.stackWatcher.setWatchAll(true);
            }
        }

        try {
            this.updateReportingValue(this.getProxy().getStorage());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    private void updateReportingValue(final IStorageService storage) {
        final AEKey myStack = this.getConfiguredKey();
        final var stacks = storage.getCachedInventory();

        if (myStack == null) {
            this.lastReportedValue = 0;
            for (var entry : stacks) {
                this.lastReportedValue += entry.getLongValue();
                if (this.lastReportedValue > this.reportingValue) {
                    // Stop here, we have enough info -- avoids blank-emitter spam causing lag, same idea as the
                    // deleted NetworkMonitor.getGridCurrentCount() this replaces.
                    break;
                }
            }
        } else {
            this.lastReportedValue = stacks.get(myStack);
        }
        this.updateState();
    }

    private boolean isLevelEmitterOn() {
        if (Platform.isClient()) {
            return (this.getClientFlags() & FLAG_ON) == FLAG_ON;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        final boolean flipState = this.getConfigManager().getSetting(Settings.REDSTONE_EMITTER) == RedstoneMode.LOW_SIGNAL;
        return flipState == (this.reportingValue > this.lastReportedValue);
    }

    @Override
    public AECableType getCableConnectionType(final AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 16;
    }

    @Override
    public boolean canConnectRedstone() {
        return true;
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(7, 7, 11, 9, 9, 16);
    }

    @Override
    public void randomDisplayTick(final World world, final BlockPos pos, final Random r) {
        if (this.isLevelEmitterOn()) {
            final AEPartLocation d = this.getSide();

            final double d0 = d.xOffset * 0.45F + (r.nextFloat() - 0.5F) * 0.2D;
            final double d1 = d.yOffset * 0.45F + (r.nextFloat() - 0.5F) * 0.2D;
            final double d2 = d.zOffset * 0.45F + (r.nextFloat() - 0.5F) * 0.2D;

            world.spawnParticle(EnumParticleTypes.REDSTONE, 0.5 + pos.getX() + d0, 0.5 + pos.getY() + d1, 0.5 + pos.getZ() + d2, 0.0D, 0.0D, 0.0D
            );
        }
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        if (Platform.isServer()) {
            Platform.openGUI(player, this.getHost().getTile(), this.getSide(), GuiBridge.GUI_FLUID_LEVEL_EMITTER);
        }
        return true;
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return this.isLevelEmitterOn() ? MODEL_ON_HAS_CHANNEL : MODEL_OFF_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return this.isLevelEmitterOn() ? MODEL_ON_ON : MODEL_OFF_ON;
        } else {
            return this.isLevelEmitterOn() ? MODEL_ON_OFF : MODEL_OFF_OFF;
        }
    }

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
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.lastReportedValue = data.getLong("lastReportedValue");
        this.reportingValue = data.getLong("reportingValue");
        this.prevState = data.getBoolean("prevState");
        this.config.readFromNBT(data, "config");
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        data.setLong("lastReportedValue", this.lastReportedValue);
        data.setLong("reportingValue", this.reportingValue);
        data.setBoolean("prevState", this.prevState);
        this.config.writeToNBT(data, "config");
    }

}
