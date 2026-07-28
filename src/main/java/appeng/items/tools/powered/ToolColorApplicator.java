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

package appeng.items.tools.powered;


import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.implementations.items.IItemGroup;
import appeng.api.implementations.tiles.IColorableTile;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.block.networking.BlockCableBus;
import appeng.block.paint.BlockPaint;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.helpers.IMouseWheelItem;
import appeng.hooks.IBlockTool;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellUpgrades;
import appeng.items.misc.ItemPaintBall;
import appeng.items.tools.powered.powersink.AEBasePoweredItem;
import appeng.me.helpers.BaseActionSource;
import appeng.tile.misc.TilePaint;
import appeng.util.Platform;
import net.minecraft.block.Block;
import net.minecraft.block.BlockColored;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.BlockStainedGlassPane;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.commons.lang3.text.WordUtils;

import javax.annotation.Nullable;
import java.util.*;


public class ToolColorApplicator extends AEBasePoweredItem implements IBasicCellItem, IItemGroup, IBlockTool, IMouseWheelItem {

    private static final double POWER_PER_USE = 100;
    private static final Map<Integer, AEColor> ORE_TO_COLOR = new HashMap<>();

    static {
        for (final AEColor color : AEColor.VALID_COLORS) {
            final String dyeName = color.dye.getTranslationKey();
            final String oreDictName = "dye" + WordUtils.capitalize(dyeName);
            final int oreDictId = OreDictionary.getOreID(oreDictName);

            ORE_TO_COLOR.put(oreDictId, color);
        }
    }

    public ToolColorApplicator() {
        super(AEConfig.instance().getColorApplicatorBattery());
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer p, World w, BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        return this.onItemUse(p.getHeldItem(hand), p, w, pos, hand, side, hitX, hitY, hitZ);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
        ItemStack stack = p.getHeldItem(hand);
        if (p.isSneaking()) {
            if (!w.isRemote) {
                cycleColors(stack, getColor(stack), 1);
            }
            return ActionResult.newResult(EnumActionResult.SUCCESS, stack);
        }
        return ActionResult.newResult(EnumActionResult.PASS, stack);
    }

    @Override
    public EnumActionResult onItemUse(ItemStack is, EntityPlayer p, World w, BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (p.isSneaking()) return EnumActionResult.PASS;

        final Block blk = w.getBlockState(pos).getBlock();

        ItemStack paintBall = this.getColor(is);

        final MEStorage inv = getInventory(is);
        if (inv != null) {
            final AEItemKey key = AEItemKey.of(paintBall);
            final long extracted = key == null ? 0
                    : inv.extract(key, paintBall.getCount(), Actionable.SIMULATE, new BaseActionSource());

            if (extracted > 0) {
                paintBall = key.toStack(1);
            } else {
                paintBall = ItemStack.EMPTY;
            }

            if (!Platform.hasPermissions(new DimensionalCoord(w, pos), p)) {
                return EnumActionResult.FAIL;
            }

            if (!paintBall.isEmpty() && paintBall.getItem() instanceof ItemSnowball) {
                final TileEntity te = w.getTileEntity(pos);
                // clean cables.
                if (te instanceof IColorableTile) {
                    if (this.getAECurrentPower(is) > POWER_PER_USE && ((IColorableTile) te).getColor() != AEColor.TRANSPARENT) {
                        if (((IColorableTile) te).recolourBlock(side, AEColor.TRANSPARENT, p)) {
                            consumeItem(is, paintBall, false);
                            return EnumActionResult.SUCCESS;
                        }
                    }
                }

                // clean paint balls..
                final Block testBlk = w.getBlockState(pos.offset(side)).getBlock();
                final TileEntity painted = w.getTileEntity(pos.offset(side));
                if (this.getAECurrentPower(is) > POWER_PER_USE && testBlk instanceof BlockPaint && painted instanceof TilePaint) {
                    consumeItem(is, paintBall, false);
                    ((TilePaint) painted).cleanSide(side.getOpposite());
                    return EnumActionResult.SUCCESS;
                }
            } else if (!paintBall.isEmpty()) {
                final AEColor color = this.getColorFromItem(paintBall);

                if (color != null && this.getAECurrentPower(is) > POWER_PER_USE) {
                    if (color != AEColor.TRANSPARENT && this.recolourBlock(blk, side, w, pos, side, color, p)) {
                        consumeItem(is, paintBall, false);
                        return EnumActionResult.SUCCESS;
                    }
                }
            }
        }

        return EnumActionResult.FAIL;
    }

    public boolean consumeColor(ItemStack applicator, AEColor color, boolean simulate) {
        final MEStorage inv = getInventory(applicator);
        if (inv == null) return false;

        ItemStack paintItem = null;
        for (final var entry : inv.getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey itemKey) {
                final ItemStack def = itemKey.toStack(1);
                if (getColorFromItem(def) == color) {
                    paintItem = def;
                }
            }
        }

        if (paintItem != null) {
            return consumeItem(applicator, paintItem, simulate);
        }
        return false;
    }

    public boolean consumeItem(ItemStack applicator, ItemStack paintItem, boolean simulate) {
        final MEStorage inv = getInventory(applicator);
        if (inv == null) return false;

        final AEItemKey key = AEItemKey.of(paintItem);
        if (key == null) return false;

        final Actionable mode = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
        final long amount = paintItem.getCount();
        boolean success = inv.extract(key, amount, mode, new BaseActionSource()) > 0
                && this.extractAEPower(applicator, POWER_PER_USE, mode) >= POWER_PER_USE;

        // Clear the color when we run out
        if (success && !simulate && ItemStack.areItemStacksEqual(paintItem, getColor(applicator))) {
            if (inv.extract(key, amount, Actionable.SIMULATE, new BaseActionSource()) <= 0) {
                setColor(applicator, ItemStack.EMPTY);
            }
        }
        return success;
    }

    public boolean setActiveColor(ItemStack applicator, @Nullable AEColor color) {
        if (color == null) {
            setColor(applicator, ItemStack.EMPTY);
            return true;
        }

        final MEStorage inv = getInventory(applicator);
        if (inv == null) return false;

        for (final var entry : inv.getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey itemKey) {
                final ItemStack def = itemKey.toStack(1);
                if (getColorFromItem(def) == color) {
                    setColor(applicator, def);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getItemStackDisplayName(final ItemStack par1ItemStack) {
        String extra = GuiText.Empty.getLocal();

        final AEColor selected = this.getActiveColor(par1ItemStack);

        if (selected != null && Platform.isClient()) {
            extra = Platform.gui_localize(selected.unlocalizedName);
        }

        return super.getItemStackDisplayName(par1ItemStack) + " - " + extra;
    }

    public AEColor getActiveColor(final ItemStack tol) {
        return this.getColorFromItem(this.getColor(tol));
    }

    private AEColor getColorFromItem(final ItemStack paintBall) {
        if (paintBall.isEmpty()) {
            return null;
        }

        if (paintBall.getItem() instanceof ItemSnowball) {
            return AEColor.TRANSPARENT;
        }

        if (paintBall.getItem() instanceof ItemPaintBall ipb) {
            return ipb.getColor(paintBall);
        } else {
            final int[] id = OreDictionary.getOreIDs(paintBall);

            for (final int oreID : id) {
                if (ORE_TO_COLOR.containsKey(oreID)) {
                    return ORE_TO_COLOR.get(oreID);
                }
            }
        }

        return null;
    }

    private MEStorage getInventory(ItemStack stack) {
        return StorageCells.getCellInventory(stack, null);
    }

    public ItemStack getColor(final ItemStack is) {
        final NBTTagCompound c = is.getTagCompound();
        if (c != null && c.hasKey("color")) {
            final NBTTagCompound color = c.getCompoundTag("color");
            final ItemStack oldColor = new ItemStack(color);
            if (!oldColor.isEmpty()) {
                return oldColor;
            }
        }

        return this.findNextColor(is, ItemStack.EMPTY, 0);
    }

    private ItemStack findNextColor(final ItemStack is, final ItemStack anchor, final int scrollOffset) {
        ItemStack newColor = ItemStack.EMPTY;

        final MEStorage inv = getInventory(is);
        if (inv != null) {
            final KeyCounter itemList = inv.getAvailableStacks();
            if (anchor.isEmpty()) {
                final AEItemKey firstItem = itemList.getFirstKey(AEItemKey.class);
                if (firstItem != null) {
                    newColor = firstItem.toStack(1);
                }
            } else {
                final LinkedList<AEItemKey> list = new LinkedList<>();

                for (final var entry : itemList) {
                    if (entry.getKey() instanceof AEItemKey itemKey) {
                        list.add(itemKey);
                    }
                }

                list.sort(Comparator.comparingInt(AEItemKey::getDamage));
                if (list.isEmpty()) return ItemStack.EMPTY;

                AEItemKey where = list.getFirst();
                int cycles = 1 + list.size();

                while (cycles > 0 && !where.matches(anchor)) {
                    list.addLast(list.removeFirst());
                    cycles--;
                    where = list.getFirst();
                }

                if (scrollOffset > 0) {
                    list.addLast(list.removeFirst());
                }

                if (scrollOffset < 0) {
                    list.addFirst(list.removeLast());
                }

                return list.get(0).toStack(1);
            }
        }

        if (!newColor.isEmpty()) {
            this.setColor(is, newColor);
        }

        return newColor;
    }

    private void setColor(final ItemStack is, final ItemStack newColor) {
        final NBTTagCompound data = Platform.openNbtData(is);
        if (newColor.isEmpty()) {
            data.removeTag("color");
        } else {
            final NBTTagCompound color = new NBTTagCompound();
            newColor.writeToNBT(color);
            data.setTag("color", color);
        }
    }

    private boolean recolourBlock(final Block blk, final EnumFacing side, final World w, final BlockPos pos, final EnumFacing orientation, final AEColor newColor, final EntityPlayer p) {
        final IBlockState state = w.getBlockState(pos);

        if (blk instanceof BlockColored) {
            final EnumDyeColor color = state.getValue(BlockColored.COLOR);

            if (newColor.dye == color) {
                return false;
            }

            return w.setBlockState(pos, state.withProperty(BlockColored.COLOR, newColor.dye));
        }

        if (blk == Blocks.GLASS) {
            return w.setBlockState(pos, Blocks.STAINED_GLASS.getDefaultState().withProperty(BlockStainedGlass.COLOR, newColor.dye));
        }

        if (blk == Blocks.STAINED_GLASS) {
            final EnumDyeColor color = state.getValue(BlockStainedGlass.COLOR);

            if (newColor.dye == color) {
                return false;
            }

            return w.setBlockState(pos, state.withProperty(BlockStainedGlass.COLOR, newColor.dye));
        }

        if (blk == Blocks.GLASS_PANE) {
            return w.setBlockState(pos, Blocks.STAINED_GLASS_PANE.getDefaultState().withProperty(BlockStainedGlassPane.COLOR, newColor.dye));
        }

        if (blk == Blocks.STAINED_GLASS_PANE) {
            final EnumDyeColor color = state.getValue(BlockStainedGlassPane.COLOR);

            if (newColor.dye == color) {
                return false;
            }

            return w.setBlockState(pos, state.withProperty(BlockStainedGlassPane.COLOR, newColor.dye));
        }

        if (blk == Blocks.HARDENED_CLAY) {
            return w.setBlockState(pos, Blocks.STAINED_HARDENED_CLAY.getDefaultState().withProperty(BlockColored.COLOR, newColor.dye));
        }

        if (blk instanceof BlockCableBus) {
            return ((BlockCableBus) blk).recolorBlock(w, pos, side, newColor.dye, p);
        }

        return blk.recolorBlock(w, pos, side, newColor.dye);
    }

    public void cycleColors(final ItemStack is, final ItemStack paintBall, final int i) {
        if (paintBall.isEmpty()) {
            this.setColor(is, this.getColor(is));
        } else {
            this.setColor(is, this.findNextColor(is, paintBall, i));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines, final ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);

        final var cdi = StorageCells.getCellInventory(stack, null);

        AEApi.instance().client().addCellInformation(cdi, lines);
    }

    @Override
    public int getBytes(final ItemStack cellItem) {
        return 512;
    }

    @Override
    public int getBytesPerType(final ItemStack cellItem) {
        return 8;
    }

    @Override
    public int getTotalTypes(final ItemStack cellItem) {
        return 27;
    }

    @Override
    public boolean isBlackListed(final ItemStack cellItem, final AEKey requestedAddition) {
        if (requestedAddition instanceof AEItemKey itemKey) {
            final int[] id = OreDictionary.getOreIDs(itemKey.getReadOnlyStack());

            for (final int x : id) {
                if (ORE_TO_COLOR.containsKey(x)) {
                    return false;
                }
            }

            if (itemKey.getItem() instanceof ItemSnowball) {
                return false;
            }

            return !(itemKey.getItem() instanceof ItemPaintBall && itemKey.getDamage() < 20);
        }
        return true;
    }

    @Override
    public boolean storableInStorageCell() {
        return true;
    }

    @Override
    public boolean isStorageCell(final ItemStack i) {
        return true;
    }

    @Override
    public double getIdleDrain() {
        return 0.5;
    }

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.items();
    }

    @Override
    public String getUnlocalizedGroupName(final Set<ItemStack> others, final ItemStack is) {
        return GuiText.StorageCells.getUnlocalized();
    }

    @Override
    public boolean isEditable(final ItemStack is) {
        return true;
    }

    @Override
    public IItemHandler getUpgradesInventory(final ItemStack is) {
        return new CellUpgrades(is, 2);
    }

    @Override
    public IItemHandler getConfigInventory(final ItemStack is) {
        return new CellConfig(is);
    }

    @Override
    public FuzzyMode getFuzzyMode(final ItemStack is) {
        final String fz = Platform.openNbtData(is).getString("FuzzyMode");
        try {
            return FuzzyMode.valueOf(fz);
        } catch (final Throwable t) {
            return FuzzyMode.IGNORE_ALL;
        }
    }

    @Override
    public void setFuzzyMode(final ItemStack is, final FuzzyMode fzMode) {
        Platform.openNbtData(is).setString("FuzzyMode", fzMode.name());
    }

    @Override
    public void onWheel(final ItemStack is, final boolean up) {
        this.cycleColors(is, this.getColor(is), up ? 1 : -1);
    }
}
