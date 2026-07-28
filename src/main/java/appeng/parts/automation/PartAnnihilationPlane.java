/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.automation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.behaviors.PickupStrategy;
import appeng.api.behaviors.StackWorldBehaviors;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.core.AppEng;
import appeng.core.settings.TickRates;
import appeng.core.sync.packets.PacketTransitionEffect;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.parts.PartBasicState;
import appeng.util.EnchantmentUtil;
import appeng.util.Platform;
import appeng.util.SettingsFrom;

public class PartAnnihilationPlane extends PartBasicState implements IGridTickable {

    private static final PlaneModels MODELS = new PlaneModels("part/annihilation_plane_", "part/annihilation_plane_on_");

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    private final IActionSource mySrc = new MachineSource(this);

    @Nullable
    private List<PickupStrategy> pickupStrategies;

    /**
     * Enchantments found on the plane when it was placed will be used to enchant the fake tool used for picking up
     * blocks.
     */
    private Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();

    public PartAnnihilationPlane(final ItemStack is) {
        super(is);
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

            if (this.isAnnihilationPlane(te.getWorld().getTileEntity(pos.offset(e.getOpposite())), this.getSide())) {
                minX = 0;
            }

            if (this.isAnnihilationPlane(te.getWorld().getTileEntity(pos.offset(e)), this.getSide())) {
                maxX = 16;
            }

            if (this.isAnnihilationPlane(te.getWorld().getTileEntity(pos.offset(u.getOpposite())), this.getSide())) {
                minY = 0;
            }

            if (this.isAnnihilationPlane(te.getWorld().getTileEntity(pos.offset(e)), this.getSide())) {
                maxY = 16;
            }
        }

        bch.addBox(5, 5, 14, 11, 11, 15);
        // The smaller collision hitbox here is needed to allow for the entity collision event
        bch.addBox(minX, minY, 15, maxX, maxY, bch.isBBCollision() ? 15 : 16);
    }

    /**
     * @return An object describing which adjacent planes this plane connects to visually.
     */
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

            if (this.isAnnihilationPlane(te.getWorld().getTileEntity(pos.offset(facingRight.getOpposite())), this.getSide())) {
                left = true;
            }

            if (this.isAnnihilationPlane(te.getWorld().getTileEntity(pos.offset(facingRight)), this.getSide())) {
                right = true;
            }

            if (this.isAnnihilationPlane(te.getWorld().getTileEntity(pos.offset(facingUp.getOpposite())), this.getSide())) {
                down = true;
            }

            if (this.isAnnihilationPlane(te.getWorld().getTileEntity(pos.offset(facingUp)), this.getSide())) {
                up = true;
            }
        }

        return PlaneConnections.of(up, right, down, left);
    }

    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        if (pos.offset(this.getSide().getFacing()).equals(neighbor)) {
            this.refresh();
        }
    }

    @Override
    public void onEntityCollision(final Entity entity) {
        if (!(entity instanceof EntityItem) || entity.isDead || !Platform.isServer() || !this.getProxy().isActive()) {
            return;
        }

        boolean capture = false;
        final BlockPos pos = this.getTile().getPos();

        // This is the middle point of the entities BB, which is better suited for comparisons that don't rely on it
        // "touching" the plane
        double posYMiddle = (entity.getEntityBoundingBox().minY + entity.getEntityBoundingBox().maxY) / 2.0D;

        switch (this.getSide()) {
            case DOWN:
            case UP:
                if (entity.posX > pos.getX() && entity.posX < pos.getX() + 1) {
                    if (entity.posZ > pos.getZ() && entity.posZ < pos.getZ() + 1) {
                        if ((entity.posY > pos.getY() + 0.9 && this.getSide() == AEPartLocation.UP) || (entity.posY < pos.getY() + 0.1 && this
                                .getSide() == AEPartLocation.DOWN)) {
                            capture = true;
                        }
                    }
                }
                break;
            case SOUTH:
            case NORTH:
                if (entity.posX > pos.getX() && entity.posX < pos.getX() + 1) {
                    if (posYMiddle > pos.getY() && posYMiddle < pos.getY() + 1) {
                        if ((entity.posZ > pos.getZ() + 0.9 && this.getSide() == AEPartLocation.SOUTH) || (entity.posZ < pos.getZ() + 0.1 && this
                                .getSide() == AEPartLocation.NORTH)) {
                            capture = true;
                        }
                    }
                }
                break;
            case EAST:
            case WEST:
                if (entity.posZ > pos.getZ() && entity.posZ < pos.getZ() + 1) {
                    if (posYMiddle > pos.getY() && posYMiddle < pos.getY() + 1) {
                        if ((entity.posX > pos.getX() + 0.9 && this.getSide() == AEPartLocation.EAST) || (entity.posX < pos.getX() + 0.1 && this
                                .getSide() == AEPartLocation.WEST)) {
                            capture = true;
                        }
                    }
                }
                break;
            default:
                // umm?
                break;
        }

        if (!capture) {
            return;
        }

        PickupStrategy strategy = null;
        for (PickupStrategy candidate : this.getPickupStrategies()) {
            if (candidate.canPickUpEntity(entity)) {
                strategy = candidate;
                break;
            }
        }
        if (strategy == null) {
            return;
        }

        final IEnergySource energy;
        try {
            energy = this.getProxy().getEnergy();
        } catch (final GridAccessException e) {
            return;
        }

        final boolean consumed = strategy.pickUpEntity(energy, this::insertIntoGrid, entity);
        if (consumed) {
            AppEng.proxy.sendToAllNearExcept(null, pos.getX(), pos.getY(), pos.getZ(), 64, this.getTile().getWorld(),
                    new PacketTransitionEffect(entity.posX, entity.posY, entity.posZ, this.getSide(), false));
        }
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 1;
    }

    protected boolean isAnnihilationPlane(final TileEntity blockTileEntity, final AEPartLocation side) {
        if (blockTileEntity instanceof IPartHost) {
            final IPart p = ((IPartHost) blockTileEntity).getPart(side);
            return p != null && p.getClass() == this.getClass();
        }
        return false;
    }

    @Override
    @MENetworkEventSubscribe
    public void chanRender(final MENetworkChannelsChanged c) {
        this.refresh();
        this.getHost().markForUpdate();
    }

    @Override
    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.refresh();
        this.getHost().markForUpdate();
    }

    /**
     * Lazily built once the node exists; cleared whenever the plane's enchantments change.
     */
    protected List<PickupStrategy> getPickupStrategies() {
        if (this.pickupStrategies == null) {
            final IGridNode node = this.getProxy().getNode();
            if (node == null) {
                return ImmutableList.of();
            }

            final TileEntity self = this.getHost().getTile();
            final BlockPos fromPos = self.getPos().offset(this.getSide().getFacing());
            final EnumFacing fromSide = this.getSide().getFacing().getOpposite();
            final UUID owner = resolveOwnerUuid(node.getPlayerID());

            this.pickupStrategies = this.createPickupStrategies(self.getWorld(), fromPos, fromSide, self,
                    this.getEnchantments(), owner);
        }
        return this.pickupStrategies;
    }

    /**
     * Extension point: {@link PartIdentityAnnihilationPlane} overrides this to substitute its own
     * always-silk-touch pickup strategy instead of the registered item one.
     * <p>
     * The plane itself is type-agnostic: it picks up whatever the registered strategies know how to
     * take out of the world, so a key type registered by an addon works here with no change.
     */
    protected List<PickupStrategy> createPickupStrategies(World world, BlockPos fromPos, EnumFacing fromSide,
            TileEntity host, Map<Enchantment, Integer> enchantments, @Nullable UUID owner) {
        return StackWorldBehaviors.createPickupStrategies(world, fromPos, fromSide, host, enchantments, owner);
    }

    /**
     * @return the enchantments captured from the plane's item when it was placed. Exposed so
     *         {@link PartIdentityAnnihilationPlane} can build its own pickup strategy with full
     *         fidelity instead of the fortune/silk-touch-only view the frozen
     *         {@code PickupStrategy.Factory} would give it.
     */
    protected final Map<Enchantment, Integer> getEnchantments() {
        return this.enchantments;
    }

    @Nullable
    private UUID resolveOwnerUuid(int playerId) {
        EntityPlayer player = AEApi.instance().registries().players().findPlayer(playerId);
        return player != null ? player.getGameProfile().getId() : null;
    }

    private long insertIntoGrid(AEKey what, long amount, Actionable mode) {
        try {
            final IEnergySource energy = this.getProxy().getEnergy();
            final MEStorage storage = this.getProxy().getStorage().getInventory();
            return Platform.poweredInsert(energy, storage, what, amount, this.mySrc, mode);
        } catch (final GridAccessException e) {
            return 0;
        }
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(TickRates.AnnihilationPlane.getMin(), TickRates.AnnihilationPlane.getMax(), false, true);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (!this.getProxy().isActive()) {
            return TickRateModulation.SLEEP;
        }

        final IEnergySource energy;
        try {
            energy = this.getProxy().getEnergy();
        } catch (final GridAccessException e) {
            return TickRateModulation.SLEEP;
        }

        for (final PickupStrategy strategy : this.getPickupStrategies()) {
            strategy.reset();
        }

        for (final PickupStrategy strategy : this.getPickupStrategies()) {
            final PickupStrategy.Result result = strategy.tryPickup(energy, this::insertIntoGrid);

            if (result == PickupStrategy.Result.PICKED_UP) {
                return TickRateModulation.URGENT;
            } else if (result == PickupStrategy.Result.CANT_STORE) {
                return TickRateModulation.IDLE;
            }
        }

        return TickRateModulation.SLEEP;
    }

    private void refresh() {
        for (final PickupStrategy strategy : this.getPickupStrategies()) {
            strategy.reset();
        }

        try {
            this.getProxy().getTick().alertDevice(this.getProxy().getNode());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.getConnections(), this.isPowered(), this.isActive());
    }

    @Override
    protected NBTTagCompound downloadSettings(SettingsFrom from, NBTTagCompound output) {
        super.downloadSettings(from, output);
        // Save enchants only when the actual plane is dismantled
        if (from == SettingsFrom.DISMANTLE_ITEM) {
            writeEnchantments(output);
        }

        return output;
    }

    @Override
    public void uploadSettings(SettingsFrom from, NBTTagCompound output, EntityPlayer player) {
        super.uploadSettings(from, output, player);
        // Import enchants only when the plane is placed, not from memory cards
        if (from == SettingsFrom.DISMANTLE_ITEM) {
            readEnchantments(output);
        }
    }

    public void readEnchantments(NBTTagCompound data) {
        this.enchantments = EnchantmentUtil.getEnchantments(data);
        EnchantmentHelper.setEnchantments(this.enchantments, getItemStack());
        this.pickupStrategies = null;
    }

    public void writeEnchantments(NBTTagCompound data) {
        EnchantmentUtil.setEnchantments(data, this.enchantments);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        readEnchantments(data);
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        writeEnchantments(data);
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        this.enchantments = EnchantmentHelper.getEnchantments(getItemStack());
        this.pickupStrategies = null;
    }
}
