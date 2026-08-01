/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.util;


import appeng.api.AEApi;
import appeng.api.config.*;
import appeng.api.definitions.IItemDefinition;
import appeng.api.definitions.IMaterials;
import appeng.api.definitions.IParts;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.implementations.items.IAEWrench;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.storage.IStorageService;
import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.features.AEFeature;
import appeng.core.stats.Stats;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.GuiHostType;
import appeng.core.sync.GuiWrapper;
import appeng.hooks.TickHandler;
import appeng.integration.Integrations;
import appeng.integration.modules.bogosorter.InventoryBogoSortModule;
import appeng.integration.modules.gregtech.ToolClass;
import appeng.me.GridAccessException;
import appeng.me.GridNode;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.helpers.ItemComparisonHelper;
import appeng.util.helpers.P2PHelper;
import appeng.util.item.OreHelper;
import appeng.util.prioritylist.IPartitionList;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import gregtech.api.block.machines.BlockMachine;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;
import ic2.api.item.ICustomDamageItem;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.InvalidParameterException;
import java.text.DecimalFormat;
import java.util.*;


/**
 * @author AlgorithmX2
 * @author thatsIch
 * @version rv2
 * @since rv0
 */
@Optional.Interface(iface = "ic2.api.item.ICustomDamageItem", modid = "IC2")
public class Platform {
    private static final Object2BooleanMap<String> CACHED_MODS = new Object2BooleanOpenHashMap<>();

    public static final Block AIR_BLOCK = Blocks.AIR;

    public static final int DEF_OFFSET = 16;

    private static final boolean CLIENT_INSTALL = FMLCommonHandler.instance().getSide().isClient();

    /*
     * random source, use it for item drop locations...
     */
    private static final Random RANDOM_GENERATOR = new Random();
    private static final WeakHashMap<World, EntityPlayer> FAKE_PLAYERS = new WeakHashMap<>();
    // private static Method getEntry;

    private static final ItemComparisonHelper ITEM_COMPARISON_HELPER = new ItemComparisonHelper();
    private static final P2PHelper P2P_HELPER = new P2PHelper();
    private static Method reflectGTgetMTE;
    public static final boolean GTLoaded = isModLoaded("gregtech");

    public static ItemComparisonHelper itemComparisons() {
        return ITEM_COMPARISON_HELPER;
    }

    public static P2PHelper p2p() {
        return P2P_HELPER;
    }

    public static Random getRandom() {
        return RANDOM_GENERATOR;
    }

    public static float getRandomFloat() {
        return RANDOM_GENERATOR.nextFloat();
    }

    /**
     * This displays the value for encoded longs ( double *100 )
     *
     * @param n      to be formatted long value
     * @param isRate if true it adds a /t to the formatted string
     * @return formatted long value
     */
    public static String formatPowerLong(final long n, final boolean isRate) {
        double p = ((double) n) / 100;

        final PowerUnits displayUnits = AEConfig.instance().selectedPowerUnit();
        p = PowerUnits.AE.convertTo(displayUnits, p);

        final String[] preFixes = {
                "k", "M", "G", "T", "P", "T", "P", "E", "Z", "Y"
        };
        String unitName = displayUnits.name();

        String level = "";
        int offset = 0;
        while (p > 1000 && offset < preFixes.length) {
            p /= 1000;
            level = preFixes[offset];
            offset++;
        }

        final DecimalFormat df = new DecimalFormat("#.##");
        return df.format(p) + ' ' + level + unitName + (isRate ? "/t" : "");
    }

    public static AEPartLocation crossProduct(final AEPartLocation forward, final AEPartLocation up) {
        final int west_x = forward.yOffset * up.zOffset - forward.zOffset * up.yOffset;
        final int west_y = forward.zOffset * up.xOffset - forward.xOffset * up.zOffset;
        final int west_z = forward.xOffset * up.yOffset - forward.yOffset * up.xOffset;

        switch (west_x + west_y * 2 + west_z * 3) {
            case 1:
                return AEPartLocation.EAST;
            case -1:
                return AEPartLocation.WEST;

            case 2:
                return AEPartLocation.UP;
            case -2:
                return AEPartLocation.DOWN;

            case 3:
                return AEPartLocation.SOUTH;
            case -3:
                return AEPartLocation.NORTH;
        }

        return AEPartLocation.INTERNAL;
    }

    public static EnumFacing crossProduct(final EnumFacing forward, final EnumFacing up) {
        final int west_x = forward.getYOffset() * up.getZOffset() - forward.getZOffset() * up.getYOffset();
        final int west_y = forward.getZOffset() * up.getXOffset() - forward.getXOffset() * up.getZOffset();
        final int west_z = forward.getXOffset() * up.getYOffset() - forward.getYOffset() * up.getXOffset();

        switch (west_x + west_y * 2 + west_z * 3) {
            case 1:
                return EnumFacing.EAST;
            case -1:
                return EnumFacing.WEST;

            case 2:
                return EnumFacing.UP;
            case -2:
                return EnumFacing.DOWN;

            case 3:
                return EnumFacing.SOUTH;
            case -3:
                return EnumFacing.NORTH;
        }

        // something is better then nothing?
        return EnumFacing.NORTH;
    }

    public static <T extends Enum> T rotateEnum(T ce, final boolean backwards, final EnumSet validOptions) {
        do {
            if (backwards) {
                ce = prevEnum(ce);
            } else {
                ce = nextEnum(ce);
            }
        } while (!validOptions.contains(ce) || isNotValidSetting(ce));

        return ce;
    }

    /*
     * Simple way to cycle an enum...
     */
    private static <T extends Enum> T prevEnum(final T ce) {
        final EnumSet valList = EnumSet.allOf(ce.getClass());

        int pLoc = ce.ordinal() - 1;
        if (pLoc < 0) {
            pLoc = valList.size() - 1;
        }

        if (pLoc < 0 || pLoc >= valList.size()) {
            pLoc = 0;
        }

        int pos = 0;
        for (final Object g : valList) {
            if (pos == pLoc) {
                return (T) g;
            }
            pos++;
        }

        return null;
    }

    /*
     * Simple way to cycle an enum...
     */
    public static <T extends Enum> T nextEnum(final T ce) {
        final EnumSet valList = EnumSet.allOf(ce.getClass());

        int pLoc = ce.ordinal() + 1;
        if (pLoc >= valList.size()) {
            pLoc = 0;
        }

        if (pLoc < 0 || pLoc >= valList.size()) {
            pLoc = 0;
        }

        int pos = 0;
        for (final Object g : valList) {
            if (pos == pLoc) {
                return (T) g;
            }
            pos++;
        }

        return null;
    }

    private static boolean isNotValidSetting(final Enum e) {
        if (e == SortOrder.INVTWEAKS && !Integrations.invTweaks().isEnabled() && !InventoryBogoSortModule.isLoaded()) {
            return true;
        }

        final boolean isJEI = e == SearchBoxMode.JEI_AUTOSEARCH || e == SearchBoxMode.JEI_AUTOSEARCH_KEEP || e == SearchBoxMode.JEI_MANUAL_SEARCH || e == SearchBoxMode.JEI_MANUAL_SEARCH_KEEP;
        return isJEI && !Integrations.jei().isEnabled();
    }

    public static void openGUI(@Nonnull final EntityPlayer p, @Nullable final TileEntity tile, @Nullable final AEPartLocation side, @Nonnull final GuiBridge type) {
        if (isClient()) {
            return;
        }

        if (type.getExternalGui() != null) {
            GuiWrapper.IExternalGui obj = type.getExternalGui();
            GuiWrapper.Opener opener = GuiWrapper.INSTANCE.getOpener(obj.getID());
            if (opener == null) {
                AELog.warn("External Gui with ID: %s is missing a opener.", obj.getID());
            } else {
                World world = tile == null ? p.world : tile.getWorld();
                BlockPos pos = tile == null ? null : tile.getPos();
                EnumFacing face = side == null ? null : side.getFacing();
                opener.open(obj, new GuiWrapper.GuiContext(world, p, pos, face, null));
            }
            return;
        }

        int x = 0;
        int y = 0;
        int z = Integer.MIN_VALUE;

        if (tile != null) {
            x = tile.getPos().getX();
            y = tile.getPos().getY();
            z = tile.getPos().getZ();
        } else {
            if (p.openContainer instanceof IInventorySlotAware) {
                x = ((IInventorySlotAware) p.openContainer).getInventorySlot();
                y = ((IInventorySlotAware) p.openContainer).isBaubleSlot() ? 1 : 0;
            } else {
                x = p.inventory.currentItem;
            }
        }

        if ((type.getType().isItem() && tile == null) || type.hasPermissions(tile, x, y, z, side, p)) {
            if (tile == null && type.getType() == GuiHostType.ITEM) {
                p.openGui(AppEng.instance(), type.ordinal() << 4, p.getEntityWorld(), x, 0, 0);
            } else if (tile == null || type.getType() == GuiHostType.ITEM) {
                if (tile != null) {
                    p.openGui(AppEng.instance(), type.ordinal() << 4 | (1 << 3), p.getEntityWorld(), x, y, z);
                } else {
                    p.openGui(AppEng.instance(), type.ordinal() << 4, p.getEntityWorld(), x, y, z);
                }
            } else {
                p.openGui(AppEng.instance(), type.ordinal() << 4 | (side.ordinal()), tile.getWorld(), x, y, z);
            }
        }
    }

    public static void openGUI(@Nonnull final EntityPlayer p, int slot, @Nonnull final GuiBridge type, boolean isBauble) {
        if (isClient()) {
            return;
        }

        if (type.getExternalGui() != null) {
            GuiWrapper.IExternalGui obj = type.getExternalGui();
            GuiWrapper.Opener opener = GuiWrapper.INSTANCE.getOpener(obj.getID());
            if (opener == null) {
                AELog.warn("External Gui with ID: %s is missing a opener.", obj.getID());
            } else {
                NBTTagCompound extra = new NBTTagCompound();
                extra.setInteger("slot", slot);
                extra.setBoolean("isBauble", isBauble);
                opener.open(obj, new GuiWrapper.GuiContext(p.world, p, null, null, extra));
            }
            return;
        }

        if (type.getType().isItem()) {
            p.openGui(AppEng.instance(), type.ordinal() << 4, p.getEntityWorld(), slot, isBauble ? 1 : 0, Integer.MIN_VALUE);
        }
    }


    /*
     * returns true if the code is on the client.
     */
    public static boolean isClient() {
        return FMLCommonHandler.instance().getEffectiveSide().isClient();
    }

    /*
     * returns true if client classes are available.
     */
    public static boolean isClientInstall() {
        return CLIENT_INSTALL;
    }

    public static boolean hasPermissions(final DimensionalCoord dc, final EntityPlayer player) {
        return dc.getWorld().canMineBlockBody(player, dc.getPos());
    }

    public static boolean hasPermissions(final World world, final BlockPos pos, final EntityPlayer player) {
        return world.canMineBlockBody(player, pos);
    }

    /*
     * Checks to see if a block is air?
     */
    public static boolean isBlockAir(final World w, final BlockPos pos) {
        try {
            return w.getBlockState(pos).getBlock().isAir(w.getBlockState(pos), w, pos);
        } catch (final Throwable e) {
            return false;
        }
    }



    public static ItemStack[] getBlockDrops(final World w, final BlockPos pos) {
        return getBlockDrops(w, pos,0);
    }

    public static ItemStack[] getBlockDrops(final World w, final BlockPos pos, int fortune) {
        List<ItemStack> out = new ArrayList<>();
        final IBlockState state = w.getBlockState(pos);

        if (state != null) {
            out = state.getBlock().getDrops(w, pos, state, fortune);
        }

        if (out == null || out.isEmpty()) {
            return new ItemStack[0];
        }

        // Drop the empty stacks. Vanilla's Block.getDrops only skips a drop when getItemDropped() answers
        // Items.AIR, and a block that answers **null** instead - BlockCableBus does, so a cable or bus never
        // drops itself as a block - still gets an ItemStack added for it, which is empty. Callers reasonably
        // read this array as "the things that dropped", and an empty stack has no AEKey: AEItemKey.of()
        // answers null for one, and Platform.poweredInsert rejects a null key outright.
        final List<ItemStack> nonEmpty = new ArrayList<>(out.size());
        for (final ItemStack stack : out) {
            if (stack != null && !stack.isEmpty()) {
                nonEmpty.add(stack);
            }
        }
        return nonEmpty.toArray(new ItemStack[0]);
    }

    public static AEPartLocation cycleOrientations(final AEPartLocation dir, final boolean upAndDown) {
        if (upAndDown) {
            switch (dir) {
                case NORTH:
                    return AEPartLocation.SOUTH;
                case SOUTH:
                    return AEPartLocation.EAST;
                case EAST:
                    return AEPartLocation.WEST;
                case WEST:
                    return AEPartLocation.NORTH;
                case UP:
                    return AEPartLocation.UP;
                case DOWN:
                    return AEPartLocation.DOWN;
                case INTERNAL:
                    return AEPartLocation.INTERNAL;
            }
        } else {
            switch (dir) {
                case UP:
                    return AEPartLocation.DOWN;
                case DOWN:
                    return AEPartLocation.NORTH;
                case NORTH:
                    return AEPartLocation.SOUTH;
                case SOUTH:
                    return AEPartLocation.EAST;
                case EAST:
                    return AEPartLocation.WEST;
                case WEST:
                    return AEPartLocation.UP;
                case INTERNAL:
                    return AEPartLocation.INTERNAL;
            }
        }

        return AEPartLocation.INTERNAL;
    }

    /*
     * Creates / or loads previous NBT Data on items, used for editing items owned by AE.
     */
    public static NBTTagCompound openNbtData(final ItemStack i) {
        NBTTagCompound compound = i.getTagCompound();

        if (compound == null) {
            i.setTagCompound(compound = new NBTTagCompound());
        }

        return compound;
    }

    /*
     * Generates Item entities in the world similar to how items are generally dropped.
     */
    public static void spawnDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        if (isServer()) {
            for (final ItemStack i : drops) {
                if (!i.isEmpty()) {
                    if (i.getCount() > 0) {
                        final double offset_x = (getRandomInt() % 32 - 16) / 82;
                        final double offset_y = (getRandomInt() % 32 - 16) / 82;
                        final double offset_z = (getRandomInt() % 32 - 16) / 82;
                        final EntityItem ei = new EntityItem(w, 0.5 + offset_x + pos.getX(), 0.5 + offset_y + pos.getY(), 0.2 + offset_z + pos.getZ(), i.copy());
                        w.spawnEntity(ei);
                    }
                }
            }
        }
    }

    /*
     * returns true if the code is on the server.
     */
    public static boolean isServer() {
        return FMLCommonHandler.instance().getEffectiveSide().isServer();
    }

    public static int getRandomInt() {
        return Math.abs(RANDOM_GENERATOR.nextInt());
    }

    public static boolean isModLoaded(final String modid) {
        return CACHED_MODS.computeIfAbsent(modid, (k) -> {
            try {
                // if this fails for some reason, try the other method.
                return Loader.isModLoaded(k);
            } catch (final Throwable ignored) {}

            return Loader.instance().getActiveModList()
                    .stream().anyMatch(mod -> mod.getModId().equals(k));
        });
    }

    public static ItemStack findMatchingRecipeOutput(final InventoryCrafting ic, final World world) {
        return CraftingManager.findMatchingResult(ic, world);
    }

    @SideOnly(Side.CLIENT)
    public static List<String> getTooltip(final Object o) {
        if (o == null) {
            return new ArrayList<>();
        }

        ItemStack itemStack;
        if (o instanceof AEKey key) {
            itemStack = key.wrapForDisplayOrFilter();
        } else if (o instanceof ItemStack) {
            itemStack = (ItemStack) o;
        } else {
            return new ArrayList<>();
        }

        try {
            ITooltipFlag.TooltipFlags tooltipFlag = Minecraft.getMinecraft().gameSettings.advancedItemTooltips ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL;
            return itemStack.getTooltip(Minecraft.getMinecraft().player, tooltipFlag);
        } catch (final Exception errB) {
            return new ArrayList<>();
        }
    }

    /**
     * Replaces the old per-channel {@code getModId(IAEItemStack)}/{@code getModId(IAEFluidStack)}
     * overloads: {@link AEKey} now covers both, so there is only one overload left.
     */
    public static String getModId(final AEKey key) {
        if (key == null) {
            return "** Null";
        }

        final String n = key.getModId();
        return n == null ? "** Null" : n;
    }

    /**
     * Re-expresses a limit written in items as the same limit in {@code what}'s own units, by the ratio of
     * their standard slot sizes. A config slot holding 512 items holds 32 buckets by the same arithmetic.
     */
    public static long scaleAmountFromItems(final long amountInItems, final AEKey what) {
        final long standard = Math.max(1, GenericSlotCapacities.get(AEKeyType.items()));
        return Math.max(1, amountInItems * GenericSlotCapacities.get(what) / standard);
    }

    public static String getItemDisplayName(final Object o) {
        if (o == null) {
            return "** Null";
        }

        ItemStack itemStack;
        if (o instanceof AEKey key) {
            final String n = key.getDisplayName().getFormattedText();
            return n == null ? "** Null" : n;
        } else if (o instanceof ItemStack) {
            itemStack = (ItemStack) o;
        } else {
            return "**Invalid Object";
        }

        try {
            String name = itemStack.getDisplayName();
            if (name == null || name.isEmpty()) {
                name = itemStack.getItem().getTranslationKey(itemStack);
            }
            return name == null ? "** Null" : name;
        } catch (final Exception errA) {
            try {
                final String n = itemStack.getTranslationKey();
                return n == null ? "** Null" : n;
            } catch (final Exception errB) {
                return "** Exception";
            }
        }
    }

    public static String getFluidDisplayName(Object o) {
        if (o == null) {
            return "** Null";
        }
        if (o instanceof AEKey key) {
            final String n = key.getDisplayName().getFormattedText();
            return n == null ? "** Null" : n;
        }
        FluidStack fluidStack;
        if (o instanceof FluidStack) {
            fluidStack = (FluidStack) o;
        } else {
            return "**Invalid Object";
        }
        String n = fluidStack.getLocalizedName();
        if (n == null || "".equalsIgnoreCase(n)) {
            n = fluidStack.getUnlocalizedName();
        }
        return n == null ? "** Null" : n;
    }

    public static boolean isWrench(final EntityPlayer player, final ItemStack eq, final BlockPos pos) {
        if (!eq.isEmpty()) {
            try {
                // TODO: Build Craft Wrench?
                /*
                 * if( eq.getItem() instanceof IToolWrench )
                 * {
                 * IToolWrench wrench = (IToolWrench) eq.getItem();
                 * return wrench.canWrench( player, x, y, z );
                 * }
                 */

                if (eq.getItem() instanceof cofh.api.item.IToolHammer) {
                    return ((cofh.api.item.IToolHammer) eq.getItem()).isUsable(eq, player, pos);
                }
            } catch (final Throwable ignore) { // explodes without BC

            }

            if (eq.getItem() instanceof IAEWrench wrench) {
                return wrench.canWrench(eq, player, pos);
            }
        }
        return false;
    }

    public static boolean isChargeable(final ItemStack i) {
        if (i.isEmpty()) {
            return false;
        }
        final Item it = i.getItem();
        if (it instanceof IAEItemPowerStorage) {
            return ((IAEItemPowerStorage) it).getPowerFlow(i) != AccessRestriction.READ;
        }
        return false;
    }

    public static EntityPlayer getPlayer(final WorldServer w) {
        if (w == null) {
            throw new InvalidParameterException("World is null.");
        }

        final EntityPlayer wrp = FAKE_PLAYERS.get(w);
        if (wrp != null) {
            return wrp;
        }

        final EntityPlayer p = FakePlayerFactory.getMinecraft(w);
        FAKE_PLAYERS.put(w, p);
        return p;
    }

    public static int MC2MEColor(final int color) {
        switch (color) {
            case 4: // "blue"
                return 0;
            case 0: // "black"
                return 1;
            case 15: // "white"
                return 2;
            case 3: // "brown"
                return 3;
            case 1: // "red"
                return 4;
            case 11: // "yellow"
                return 5;
            case 2: // "green"
                return 6;

            case 5: // "purple"
            case 6: // "cyan"
            case 7: // "silver"
            case 8: // "gray"
            case 9: // "pink"
            case 10: // "lime"
            case 12: // "lightBlue"
            case 13: // "magenta"
            case 14: // "orange"
        }
        return -1;
    }

    public static int findEmpty(final RegistryNamespaced registry, final int minId, final int maxId) {
        for (int x = minId; x < maxId; x++) {
            if (registry.getObjectById(x) == null) {
                return x;
            }
        }
        return -1;
    }

    public static int findEmpty(final Object[] l) {
        for (int x = 0; x < l.length; x++) {
            if (l[x] == null) {
                return x;
            }
        }
        return -1;
    }

    /**
     * Returns a random element from the given collection.
     *
     * @return null if the collection is empty
     */
    @Nullable
    public static <T> T pickRandom(final Collection<T> outs) {
        if (outs.isEmpty()) {
            return null;
        }

        int index = RANDOM_GENERATOR.nextInt(outs.size());
        return Iterables.get(outs, index, null);
    }

    public static AEPartLocation rotateAround(final AEPartLocation forward, final AEPartLocation axis) {
        if (axis == AEPartLocation.INTERNAL || forward == AEPartLocation.INTERNAL) {
            return forward;
        }

        switch (forward) {
            case DOWN:
                switch (axis) {
                    case DOWN:
                        return forward;
                    case UP:
                        return forward;
                    case NORTH:
                        return AEPartLocation.EAST;
                    case SOUTH:
                        return AEPartLocation.WEST;
                    case EAST:
                        return AEPartLocation.NORTH;
                    case WEST:
                        return AEPartLocation.SOUTH;
                    default:
                        break;
                }
                break;
            case UP:
                switch (axis) {
                    case NORTH:
                        return AEPartLocation.WEST;
                    case SOUTH:
                        return AEPartLocation.EAST;
                    case EAST:
                        return AEPartLocation.SOUTH;
                    case WEST:
                        return AEPartLocation.NORTH;
                    default:
                        break;
                }
                break;
            case NORTH:
                switch (axis) {
                    case UP:
                        return AEPartLocation.WEST;
                    case DOWN:
                        return AEPartLocation.EAST;
                    case EAST:
                        return AEPartLocation.UP;
                    case WEST:
                        return AEPartLocation.DOWN;
                    default:
                        break;
                }
                break;
            case SOUTH:
                switch (axis) {
                    case UP:
                        return AEPartLocation.EAST;
                    case DOWN:
                        return AEPartLocation.WEST;
                    case EAST:
                        return AEPartLocation.DOWN;
                    case WEST:
                        return AEPartLocation.UP;
                    default:
                        break;
                }
                break;
            case EAST:
                switch (axis) {
                    case UP:
                        return AEPartLocation.NORTH;
                    case DOWN:
                        return AEPartLocation.SOUTH;
                    case NORTH:
                        return AEPartLocation.UP;
                    case SOUTH:
                        return AEPartLocation.DOWN;
                    default:
                        break;
                }
            case WEST:
                switch (axis) {
                    case UP:
                        return AEPartLocation.SOUTH;
                    case DOWN:
                        return AEPartLocation.NORTH;
                    case NORTH:
                        return AEPartLocation.DOWN;
                    case SOUTH:
                        return AEPartLocation.UP;
                    default:
                        break;
                }
            default:
                break;
        }
        return forward;
    }

    public static EnumFacing rotateAround(final EnumFacing forward, final EnumFacing axis) {
        switch (forward) {
            case DOWN:
                switch (axis) {
                    case DOWN:
                        return forward;
                    case UP:
                        return forward;
                    case NORTH:
                        return EnumFacing.EAST;
                    case SOUTH:
                        return EnumFacing.WEST;
                    case EAST:
                        return EnumFacing.NORTH;
                    case WEST:
                        return EnumFacing.SOUTH;
                    default:
                        break;
                }
                break;
            case UP:
                switch (axis) {
                    case NORTH:
                        return EnumFacing.WEST;
                    case SOUTH:
                        return EnumFacing.EAST;
                    case EAST:
                        return EnumFacing.SOUTH;
                    case WEST:
                        return EnumFacing.NORTH;
                    default:
                        break;
                }
                break;
            case NORTH:
                switch (axis) {
                    case UP:
                        return EnumFacing.WEST;
                    case DOWN:
                        return EnumFacing.EAST;
                    case EAST:
                        return EnumFacing.UP;
                    case WEST:
                        return EnumFacing.DOWN;
                    default:
                        break;
                }
                break;
            case SOUTH:
                switch (axis) {
                    case UP:
                        return EnumFacing.EAST;
                    case DOWN:
                        return EnumFacing.WEST;
                    case EAST:
                        return EnumFacing.DOWN;
                    case WEST:
                        return EnumFacing.UP;
                    default:
                        break;
                }
                break;
            case EAST:
                switch (axis) {
                    case UP:
                        return EnumFacing.NORTH;
                    case DOWN:
                        return EnumFacing.SOUTH;
                    case NORTH:
                        return EnumFacing.UP;
                    case SOUTH:
                        return EnumFacing.DOWN;
                    default:
                        break;
                }
            case WEST:
                switch (axis) {
                    case UP:
                        return EnumFacing.SOUTH;
                    case DOWN:
                        return EnumFacing.NORTH;
                    case NORTH:
                        return EnumFacing.DOWN;
                    case SOUTH:
                        return EnumFacing.UP;
                    default:
                        break;
                }
            default:
                break;
        }
        return forward;
    }

    @SideOnly(Side.CLIENT)
    public static String gui_localize(final String string) {
        return I18n.translateToLocal(string);
    }

    public static LookDirection getPlayerRay(final EntityPlayer playerIn, final float eyeOffset) {
        double reachDistance = 5.0d;

        final double x = playerIn.prevPosX + (playerIn.posX - playerIn.prevPosX);
        final double y = playerIn.prevPosY + (playerIn.posY - playerIn.prevPosY) + playerIn.getEyeHeight();
        final double z = playerIn.prevPosZ + (playerIn.posZ - playerIn.prevPosZ);

        final float playerPitch = playerIn.prevRotationPitch + (playerIn.rotationPitch - playerIn.prevRotationPitch);
        final float playerYaw = playerIn.prevRotationYaw + (playerIn.rotationYaw - playerIn.prevRotationYaw);

        final float yawRayX = MathHelper.sin(-playerYaw * 0.017453292f - (float) Math.PI);
        final float yawRayZ = MathHelper.cos(-playerYaw * 0.017453292f - (float) Math.PI);

        final float pitchMultiplier = -MathHelper.cos(-playerPitch * 0.017453292F);
        final float eyeRayY = MathHelper.sin(-playerPitch * 0.017453292F);
        final float eyeRayX = yawRayX * pitchMultiplier;
        final float eyeRayZ = yawRayZ * pitchMultiplier;

        if (playerIn instanceof EntityPlayerMP) {
            reachDistance = ((EntityPlayerMP) playerIn).interactionManager.getBlockReachDistance();
        }

        final Vec3d from = new Vec3d(x, y, z);
        final Vec3d to = from.add(eyeRayX * reachDistance, eyeRayY * reachDistance, eyeRayZ * reachDistance);

        return new LookDirection(from, to);
    }

    public static RayTraceResult rayTrace(final EntityPlayer p, final boolean hitBlocks, final boolean hitEntities) {
        final World w = p.getEntityWorld();

        final float f = 1.0F;
        float f1 = p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * f;
        final float f2 = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * f;
        final double d0 = p.prevPosX + (p.posX - p.prevPosX) * f;
        final double d1 = p.prevPosY + (p.posY - p.prevPosY) * f + 1.62D - p.getYOffset();
        final double d2 = p.prevPosZ + (p.posZ - p.prevPosZ) * f;
        final Vec3d vec3 = new Vec3d(d0, d1, d2);
        final float f3 = MathHelper.cos(-f2 * 0.017453292F - (float) Math.PI);
        final float f4 = MathHelper.sin(-f2 * 0.017453292F - (float) Math.PI);
        final float f5 = -MathHelper.cos(-f1 * 0.017453292F);
        final float f6 = MathHelper.sin(-f1 * 0.017453292F);
        final float f7 = f4 * f5;
        final float f8 = f3 * f5;
        final double d3 = 32.0D;

        final Vec3d vec31 = vec3.add(f7 * d3, f6 * d3, f8 * d3);

        final AxisAlignedBB bb = new AxisAlignedBB(Math.min(vec3.x, vec31.x), Math.min(vec3.y, vec31.y), Math.min(vec3.z, vec31.z), Math.max(vec3.x, vec31.x), Math.max(vec3.y, vec31.y), Math.max(vec3.z, vec31.z)).grow(16, 16, 16);

        Entity entity = null;
        double closest = 9999999.0D;
        if (hitEntities) {
            final List list = w.getEntitiesWithinAABBExcludingEntity(p, bb);

            for (int l = 0; l < list.size(); ++l) {
                final Entity entity1 = (Entity) list.get(l);

                if (!entity1.isDead && entity1 != p && !(entity1 instanceof EntityItem)) {
                    if (entity1.isEntityAlive()) {
                        // prevent killing / flying of mounts.
                        if (entity1.isRidingOrBeingRiddenBy(p)) {
                            continue;
                        }

                        f1 = 0.3F;
                        final AxisAlignedBB boundingBox = entity1.getEntityBoundingBox().grow(f1, f1, f1);
                        final RayTraceResult RayTraceResult = boundingBox.calculateIntercept(vec3, vec31);

                        if (RayTraceResult != null) {
                            final double nd = vec3.squareDistanceTo(RayTraceResult.hitVec);

                            if (nd < closest) {
                                entity = entity1;
                                closest = nd;
                            }
                        }
                    }
                }
            }
        }

        RayTraceResult pos = null;
        Vec3d vec = null;

        if (hitBlocks) {
            vec = new Vec3d(d0, d1, d2);
            pos = w.rayTraceBlocks(vec3, vec31, true);
        }

        if (entity != null && pos != null && pos.hitVec.squareDistanceTo(vec) > closest) {
            pos = new RayTraceResult(entity);
        } else if (entity != null && pos == null) {
            pos = new RayTraceResult(entity);
        }

        return pos;
    }

    /**
     * Extracts, charging the network for the work. Mirrors {@code IStorageHelper.poweredExtraction}
     * (see {@code appeng.api.storage.IStorageHelper}) -- this is the implementation the future
     * {@code IStorageHelper} implementation in {@code appeng.core.api} is expected to delegate to,
     * same as it did for the old generic version of this method.
     */
    public static long poweredExtraction(final IEnergySource energy, final MEStorage cell, final AEKey request, final long amount, final IActionSource src) {
        return poweredExtraction(energy, cell, request, amount, src, Actionable.MODULATE);
    }

    public static long poweredExtraction(final IEnergySource energy, final MEStorage cell, final AEKey request, final long amount, final IActionSource src, final Actionable mode) {
        Preconditions.checkNotNull(energy);
        Preconditions.checkNotNull(cell);
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(src);
        Preconditions.checkNotNull(mode);

        final long retrieved = cell.extract(request, amount, Actionable.SIMULATE, src);

        final double energyFactor = Math.max(1.0, request.getAmountPerOperation());
        final double availablePower = energy.extractAEPower(retrieved / energyFactor, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        final long itemToExtract = Math.min((long) ((availablePower * energyFactor) + 0.9), retrieved);

        if (itemToExtract > 0) {
            if (mode == Actionable.MODULATE) {
                energy.extractAEPower(retrieved / energyFactor, Actionable.MODULATE, PowerMultiplier.CONFIG);
                final long ret = cell.extract(request, itemToExtract, Actionable.MODULATE, src);

                if (ret > 0 && request instanceof AEItemKey) {
                    src.player().ifPresent(player -> Stats.ItemsExtracted.addToPlayer(player, (int) ret));
                }
                return ret;
            } else {
                return itemToExtract;
            }
        }

        return 0;
    }

    /**
     * Inserts, charging the network for the work. Mirrors {@code IStorageHelper.poweredInsert}.
     */
    public static long poweredInsert(final IEnergySource energy, final MEStorage cell, final AEKey input, final long amount, final IActionSource src) {
        return poweredInsert(energy, cell, input, amount, src, Actionable.MODULATE);
    }

    public static long poweredInsert(final IEnergySource energy, final MEStorage cell, final AEKey input, final long amount, final IActionSource src, final Actionable mode) {
        Preconditions.checkNotNull(energy);
        Preconditions.checkNotNull(cell);
        Preconditions.checkNotNull(input);
        Preconditions.checkNotNull(src);
        Preconditions.checkNotNull(mode);

        long toInsert = cell.insert(input, amount, Actionable.SIMULATE, src);
        if (toInsert <= 0) {
            return 0;
        }

        final double energyFactor = Math.max(1.0, input.getAmountPerOperation());
        final double availablePower = energy.extractAEPower(toInsert / energyFactor, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        toInsert = Math.min((long) ((availablePower * energyFactor) + 0.9), toInsert);

        if (toInsert <= 0) {
            return 0;
        }

        if (mode == Actionable.MODULATE) {
            energy.extractAEPower(toInsert / energyFactor, Actionable.MODULATE, PowerMultiplier.CONFIG);
            final long inserted = cell.insert(input, toInsert, Actionable.MODULATE, src);

            if (inserted > 0 && input instanceof AEItemKey) {
                src.player().ifPresent(player -> Stats.ItemsInserted.addToPlayer(player, (int) inserted));
            }

            return inserted;
        } else {
            return toInsert;
        }
    }

    /**
     * Posts the difference between a removed and an added cell to the network's listeners.
     * <p>
     * NOTE: the old implementation diffed the two cells' item lists and pushed the delta through
     * {@code IStorageGrid.postAlterationOfStoredItems(channel, changes, src)}. That method does not
     * exist on the new {@link IStorageService} -- there is no per-channel change list to push
     * anymore, and no direct "notify listeners of this delta" entry point at all, only
     * {@link IStorageService#invalidateCache()} and the cached snapshot
     * {@link IStorageService#getCachedInventory()}. This is flagged in the migration report as a gap
     * for whoever implements {@code IStorageService} (wave 1, {@code appeng.me}) to resolve: either
     * {@code invalidateCache()} is genuinely sufficient (a full recount, diffed against the previous
     * {@code getCachedInventory()} snapshot, to notify watchers), or a finer-grained push path needs
     * to be added to the storage service.
     */
    public static void postChanges(final IStorageService gs, final ItemStack removed, final ItemStack added, final IActionSource src) {
        Preconditions.checkNotNull(gs);
        Preconditions.checkNotNull(removed);
        Preconditions.checkNotNull(added);
        Preconditions.checkNotNull(src);

        gs.invalidateCache();
    }

    public static boolean securityCheck(final GridNode a, final GridNode b) {
        if (a.getLastSecurityKey() == -1 && b.getLastSecurityKey() == -1) {
            return true;
        } else if (a.getLastSecurityKey() == b.getLastSecurityKey()) {
            return true;
        }

        final boolean a_isSecure = isPowered(a.getGrid()) && a.getLastSecurityKey() != -1;
        final boolean b_isSecure = isPowered(b.getGrid()) && b.getLastSecurityKey() != -1;

        if (AEConfig.instance().isFeatureEnabled(AEFeature.LOG_SECURITY_AUDITS)) {
            final String locationA = a.getGridBlock().isWorldAccessible() ? a.getGridBlock().getLocation().toString() : "notInWorld";
            final String locationB = b.getGridBlock().isWorldAccessible() ? b.getGridBlock().getLocation().toString() : "notInWorld";

            AELog.info("Audit: Node A [isSecure=%b, key=%d, playerID=%d, location={%s}] vs Node B[isSecure=%b, key=%d, playerID=%d, location={%s}]", a_isSecure, a.getLastSecurityKey(), a.getPlayerID(), locationA, b_isSecure, b.getLastSecurityKey(), b.getPlayerID(), locationB);
        }

        // can't do that son...
        if (a_isSecure && b_isSecure) {
            return false;
        }

        if (!a_isSecure && b_isSecure) {
            return checkPlayerPermissions(b.getGrid(), a.getPlayerID());
        }

        if (a_isSecure && !b_isSecure) {
            return checkPlayerPermissions(a.getGrid(), b.getPlayerID());
        }

        return true;
    }

    private static boolean isPowered(final IGrid grid) {
        if (grid == null) {
            return false;
        }

        final IEnergyGrid eg = grid.getCache(IEnergyGrid.class);
        return eg.isNetworkPowered();
    }

    private static boolean checkPlayerPermissions(final IGrid grid, final int playerID) {
        if (grid == null) {
            return true;
        }

        final ISecurityGrid gs = grid.getCache(ISecurityGrid.class);

        if (gs == null) {
            return true;
        }

        if (!gs.isAvailable()) {
            return true;
        }

        return gs.hasPermission(playerID, SecurityPermissions.BUILD);
    }

    public static void configurePlayer(final EntityPlayer player, final AEPartLocation side, final TileEntity tile) {
        float pitch = 0.0f;
        float yaw = 0.0f;
        // player.yOffset = 1.8f;

        switch (side) {
            case DOWN:
                pitch = 90.0f;
                // player.getYOffset() = -1.8f;
                break;
            case EAST:
                yaw = -90.0f;
                break;
            case NORTH:
                yaw = 180.0f;
                break;
            case SOUTH:
                yaw = 0.0f;
                break;
            case INTERNAL:
                break;
            case UP:
                pitch = 90.0f;
                break;
            case WEST:
                yaw = 90.0f;
                break;
        }

        player.posX = tile.getPos().getX() + 0.5;
        player.posY = tile.getPos().getY() + 0.5;
        player.posZ = tile.getPos().getZ() + 0.5;

        player.rotationPitch = player.prevCameraPitch = player.cameraPitch = pitch;
        player.rotationYaw = player.prevCameraYaw = player.cameraYaw = yaw;
    }

    public static boolean canAccess(final AENetworkProxy gridProxy, final IActionSource src) {
        try {
            if (src.player().isPresent()) {
                return gridProxy.getSecurity().hasPermission(src.player().get(), SecurityPermissions.BUILD);
            } else if (src.machine().isPresent()) {
                final IActionHost te = src.machine().get();
                final IGridNode n = te.getActionableNode();
                if (n == null) {
                    return false;
                }

                final int playerID = n.getPlayerID();
                return gridProxy.getSecurity().hasPermission(playerID, SecurityPermissions.BUILD);
            } else {
                return false;
            }
        } catch (final GridAccessException gae) {
            return false;
        }
    }

    /**
     * @param filter the view-cell filter, as produced by {@code ItemViewCell.createFilter}, or {@code null}
     *               for "no filtering". Takes an {@link AEKeyFilter} rather than an {@link IPartitionList}
     *               because a view cell filters keys of any registered key type; see the note on
     *               {@code ItemViewCell.createFilter}.
     */
    public static ItemStack extractItemsByRecipe(final IEnergySource energySrc, final IActionSource mySrc, final MEStorage src, final World w, final IRecipe r, final ItemStack output, final InventoryCrafting ci, final ItemStack providedTemplate, final int slot, final KeyCounter items, final Actionable realForFake, final AEKeyFilter filter) {
        if (energySrc.extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.9) {
            if (providedTemplate == null) {
                return ItemStack.EMPTY;
            }

            final AEItemKey ae_req = AEItemKey.of(providedTemplate);
            if (ae_req == null) {
                return ItemStack.EMPTY;
            }

            if (filter == null || filter.matches(ae_req)) {
                final long ae_ext = src.extract(ae_req, 1, realForFake, mySrc);
                if (ae_ext > 0) {
                    energySrc.extractAEPower(1, realForFake, PowerMultiplier.CONFIG);
                    return ae_req.toStack((int) ae_ext);
                }
            }

            final boolean checkFuzzy = OreHelper.INSTANCE.getOre(providedTemplate).isPresent() || providedTemplate.getItemDamage() == OreDictionary.WILDCARD_VALUE || providedTemplate.hasTagCompound() || providedTemplate.isItemStackDamageable();

            if (items != null && checkFuzzy) {
                for (final Object2LongMap.Entry<AEKey> entry : items) {
                    if (!(entry.getKey() instanceof AEItemKey x)) {
                        continue;
                    }

                    final ItemStack sh = x.getReadOnlyStack();
                    if ((Platform.itemComparisons().isEqualItemType(providedTemplate, sh) || OreHelper.INSTANCE.sameOre(ae_req, x)) && !ItemStack.areItemsEqual(sh, output)) { // Platform.isSameItemType( sh, providedTemplate )
                        final ItemStack cp = sh.copy();
                        cp.setCount(1);
                        ci.setInventorySlotContents(slot, cp);
                        if (r.matches(ci, w) && ItemStack.areItemsEqual(r.getCraftingResult(ci), output)) {
                            if (filter == null || filter.matches(x)) {
                                final long ex = src.extract(x, 1, realForFake, mySrc);
                                if (ex > 0) {
                                    energySrc.extractAEPower(1, realForFake, PowerMultiplier.CONFIG);
                                    return x.toStack((int) ex);
                                }
                            }
                        }
                        ci.setInventorySlotContents(slot, providedTemplate);
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    // TODO wtf is this?
    public static ItemStack getContainerItem(final ItemStack stackInSlot) {
        if (stackInSlot == null) {
            return ItemStack.EMPTY;
        }

        final Item i = stackInSlot.getItem();
        if (i == null || !i.hasContainerItem(stackInSlot)) {
            if (stackInSlot.getCount() > 1) {
                stackInSlot.setCount(stackInSlot.getCount() - 1);
                return stackInSlot;
            }
            return ItemStack.EMPTY;
        }

        ItemStack ci = i.getContainerItem(stackInSlot.copy());
        if (!ci.isEmpty() && ci.isItemStackDamageable() && ci.getItemDamage() == ci.getMaxDamage()) {
            ci = ItemStack.EMPTY;
        }

        return ci;
    }

    /**
     * What a crafting slot leaves behind once the craft is done. The same as
     * {@link #getContainerItem(ItemStack)}, except that a container the network assembled out of a fluid
     * never existed as an item and must not be handed back as one.
     *
     * @param cpuSupplied false when the ingredients were fed in by hand rather than pushed by a crafting
     *                    CPU, in which case every container in the grid is real and stays real.
     */
    public static ItemStack getRemainingItem(final ICraftingPatternDetails details, final int slot,
            final ItemStack inSlot, final boolean cpuSupplied) {
        if (cpuSupplied && details != null && details.isContainerFabricated(slot)) {
            return ItemStack.EMPTY;
        }

        return getContainerItem(inSlot);
    }

    public static void notifyBlocksOfNeighbors(final World world, final BlockPos pos) {
        if (!world.isRemote) {
            TickHandler.INSTANCE.addCallable(world, new BlockUpdate(pos));
        }
    }

    public static boolean canRepair(final AEFeature type, final ItemStack a, final ItemStack b) {
        if (b.isEmpty() || a.isEmpty()) {
            return false;
        }

        if (type == AEFeature.CERTUS_QUARTZ_TOOLS) {
            final IItemDefinition certusQuartzCrystal = AEApi.instance().definitions().materials().certusQuartzCrystal();

            return certusQuartzCrystal.isSameAs(b);
        }

        if (type == AEFeature.NETHER_QUARTZ_TOOLS) {
            return Items.QUARTZ == b.getItem();
        }

        return false;
    }

    public static List<ItemStack> findPreferred(final ItemStack[] is) {
        final IParts parts = AEApi.instance().definitions().parts();

        for (final ItemStack stack : is) {
            if (parts.cableGlass().sameAs(AEColor.TRANSPARENT, stack)) {
                return Collections.singletonList(stack);
            }

            if (parts.cableCovered().sameAs(AEColor.TRANSPARENT, stack)) {
                return Collections.singletonList(stack);
            }

            if (parts.cableSmart().sameAs(AEColor.TRANSPARENT, stack)) {
                return Collections.singletonList(stack);
            }

            if (parts.cableDenseSmart().sameAs(AEColor.TRANSPARENT, stack)) {
                return Collections.singletonList(stack);
            }
        }

        return Lists.newArrayList(is);
    }

    public static void sendChunk(final Chunk c, final int verticalBits) {
        try {
            final WorldServer ws = (WorldServer) c.getWorld();
            final PlayerChunkMap pm = ws.getPlayerChunkMap();
            final PlayerChunkMapEntry playerInstance = pm.getEntry(c.x, c.z);

            if (playerInstance != null) {
                playerInstance.sendPacket(new SPacketChunkData(c, verticalBits));
            }
        } catch (final Throwable t) {
            AELog.debug(t);
        }
    }

    public static float getEyeOffset(final EntityPlayer player) {
        assert player.world.isRemote : "Valid only on client";
        return (float) (player.posY + player.getEyeHeight() - player.getDefaultEyeHeight());
    }

    // public static void addStat( final int playerID, final Achievement achievement )
    // {
    // final EntityPlayer p = AEApi.instance().registries().players().findPlayer( playerID );
    // if( p != null )
    // {
    // p.addStat( achievement, 1 );
    // }
    // }

    public static boolean isRecipePrioritized(final ItemStack what) {
        final IMaterials materials = AEApi.instance().definitions().materials();

        boolean isPurified = materials.purifiedCertusQuartzCrystal().isSameAs(what);
        isPurified |= materials.purifiedFluixCrystal().isSameAs(what);
        isPurified |= materials.purifiedNetherQuartzCrystal().isSameAs(what);

        return isPurified;
    }

    //consider methods below moving to a compability class
    public static boolean isGTDamageableItem(Item item) {
        return ((GTLoaded) && ToolClass.getGTToolClass().isAssignableFrom(item.getClass()));
    }

    public static MetaTileEntity getMetaTileEntity(IBlockAccess world, BlockPos pos) {
        if (reflectGTgetMTE == null) {
            try {
                reflectGTgetMTE = ReflectionHelper.findMethod(BlockMachine.class, "getMetaTileEntity", null, IBlockAccess.class, BlockPos.class);
            } catch (ReflectionHelper.UnableToFindMethodException e) {
                reflectGTgetMTE = ReflectionHelper.findMethod(GTUtility.class, "getMetaTileEntity", null, IBlockAccess.class, BlockPos.class);
            }
        } else {
            try {
                return (MetaTileEntity) reflectGTgetMTE.invoke(reflectGTgetMTE, world, pos);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static boolean isIC2DamageableItem(Item item) {
        return (isModLoaded("IC2") && item instanceof ICustomDamageItem);
    }
}
