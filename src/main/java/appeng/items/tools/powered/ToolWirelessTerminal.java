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

import appeng.api.upgrades.CardTraits;
import appeng.api.upgrades.IUpgradeInventory;


import appeng.api.AEApi;
import appeng.api.config.*;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.features.IWirelessTerminalMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.IConfigManager;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.helpers.WirelessTerminalMigration;
import appeng.helpers.WirelessTerminalModes;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellUpgrades;
import appeng.items.materials.ItemMaterial;
import appeng.items.tools.powered.powersink.AEBasePoweredItem;
import appeng.me.helpers.PlayerSource;
import appeng.util.ConfigManager;
import appeng.util.ItemToggle;
import appeng.util.Platform;
import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import baubles.api.cap.IBaublesItemHandler;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.NonNullList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Optional.Interface(iface = "baubles.api.IBauble", modid = "baubles")

public class ToolWirelessTerminal extends AEBasePoweredItem implements IWirelessTermHandler, IBauble {

    private static final int MAGNET_INTERVAL = 5;

    public ToolWirelessTerminal() {
        super(AEConfig.instance().getWirelessTerminalBattery());
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(final World w, final EntityPlayer player, final EnumHand hand) {
        AEApi.instance().registries().wireless().openWirelessTerminalGui(player.getHeldItem(hand), w, player);
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean isFull3D() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines, final ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);

        if (stack.hasTagCompound()) {
            final NBTTagCompound tag = Platform.openNbtData(stack);
            if (tag != null) {
                final String encKey = tag.getString("encryptionKey");

                if (encKey == null || encKey.isEmpty()) {
                    lines.add(TextFormatting.RED + GuiText.Unlinked.getLocal());
                } else {
                    lines.add(TextFormatting.GREEN + GuiText.Linked.getLocal());
                }
            }
        } else {
            lines.add(I18n.translateToLocal("AppEng.GuiITooltip.Unlinked"));
        }

        this.addModeInformation(stack, lines);
    }

    /**
     * What this terminal can be switched to. A mode whose addon has been taken out is still listed, by its
     * bare name, because it is still written on the terminal and comes back with the addon.
     */
    @SideOnly(Side.CLIENT)
    private void addModeInformation(final ItemStack stack, final List<String> lines) {
        if (this.getLegacyMode() != null) {
            return;
        }

        for (final IWirelessTerminalMode mode : WirelessTerminalModes.getUnlockedModes(stack)) {
            lines.add(TextFormatting.GRAY + "- " + I18n.translateToLocal(mode.getUnlocalizedName()));
        }

        for (final ResourceLocation unknown : WirelessTerminalModes.getUnknown(stack)) {
            lines.add(TextFormatting.DARK_GRAY + "- "
                    + I18n.translateToLocalFormatted(GuiText.UnknownWirelessMode.getUnlocalized(), unknown));
        }
    }

    @Override
    public boolean canHandle(final ItemStack is) {
        return AEApi.instance().definitions().items().wirelessTerminal().isSameAs(is);
    }

    /**
     * The mode this item used to be, back when there was a wireless terminal item per screen. Null for the
     * one terminal that carries modes itself; the three left over from before answer their own, and are
     * converted into it the moment a player holds one.
     */
    @Nullable
    public ResourceLocation getLegacyMode() {
        return null;
    }

    /**
     * The item-level NBT keys that belonged to that old terminal's screen and now belong to its mode alone.
     * The crafting terminal and the pattern terminal both wrote a three by three grid to {@code craftingGrid},
     * one of real items and one of ghosts, so on a single item they have to be told apart.
     */
    public String[] getLegacyModeKeys() {
        return new String[0];
    }

    @Override
    public boolean usePower(final EntityPlayer player, final double amount, final ItemStack is) {
        return this.extractAEPower(is, amount, Actionable.MODULATE) >= amount - 0.5;
    }

    @Override
    public boolean hasPower(final EntityPlayer player, final double amt, final ItemStack is) {
        return this.getAECurrentPower(is) >= amt;
    }

    @Override
    public IConfigManager getConfigManager(final ItemStack target) {
        final ConfigManager out = new ConfigManager((manager, settingName, newValue) ->
        {
            final NBTTagCompound data = Platform.openNbtData(target);
            manager.writeToNBT(data);
        });

        out.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        out.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        out.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
        out.registerSetting(Settings.PICK_BLOCK, YesNo.YES);

        out.readFromNBT(Platform.openNbtData(target).copy());
        return out;
    }

    @Override
    public String getEncryptionKey(final ItemStack item) {
        final NBTTagCompound tag = Platform.openNbtData(item);
        return tag.getString("encryptionKey");
    }

    @Override
    public void setEncryptionKey(final ItemStack item, final String encKey, final String name) {
        final NBTTagCompound tag = Platform.openNbtData(item);
        tag.setString("encryptionKey", encKey);
        tag.setString("name", name);
    }

    /**
     * The terminal is named for what it is doing right now, because nothing else on the screen says so - one
     * item wearing five faces would otherwise be five identical items in a chest.
     */
    @Override
    public String getItemStackDisplayName(final ItemStack stack) {
        final String name = super.getItemStackDisplayName(stack);
        if (this.getLegacyMode() != null) {
            return name;
        }

        final IWirelessTerminalMode mode = WirelessTerminalModes.getActiveMode(stack);
        if (mode == null) {
            return name;
        }

        return I18n.translateToLocalFormatted(GuiText.WirelessTerminalModeName.getUnlocalized(), name,
                I18n.translateToLocal(mode.getUnlocalizedName()));
    }

    /**
     * Every mode there is, so a creative terminal is the whole thing rather than something to be finished at
     * a crafting table. Read from the registry each time the tab is opened, so a mode added by an addon is in
     * there too.
     */
    @Override
    protected void getCheckedSubItems(final CreativeTabs creativeTab, final NonNullList<ItemStack> itemStacks) {
        // The three terminals from before hand out nothing: no tab, no HEI entry, no recipe. The only ones
        // left in the world are the ones already in it, and those convert themselves.
        if (this.getLegacyMode() != null) {
            return;
        }

        super.getCheckedSubItems(creativeTab, itemStacks);

        final List<ResourceLocation> all = new ArrayList<>();
        for (final IWirelessTerminalMode mode : AEApi.instance().registries().wirelessTerminalModes().getModes()) {
            all.add(mode.getId());
        }

        for (final ItemStack stack : itemStacks) {
            if (stack.getItem() == this) {
                WirelessTerminalModes.setUnlocked(stack, all);
            }
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged;
    }

    @Override
    public void onUpdate(ItemStack stack, World worldIn, Entity entityIn, int itemSlot, boolean isSelected) {
        super.onUpdate(stack, worldIn, entityIn, itemSlot, isSelected);
        if (Platform.isServer()) {
            if (this.getLegacyMode() != null) {
                WirelessTerminalMigration.convertInPlace(stack, entityIn instanceof EntityPlayer player ? player : null);
                return;
            }

            magnetLogic(stack, worldIn, entityIn);
        }
    }

    @Override
    public IGuiHandler getGuiHandler(ItemStack is) {
        final IWirelessTerminalMode mode = WirelessTerminalModes.getActiveMode(is);
        return mode == null ? GuiBridge.GUI_WIRELESS_TERM : mode.getGuiHandler();
    }

    @Optional.Method(modid = "baubles")
    @Override
    public BaubleType getBaubleType(ItemStack itemStack) {
        return BaubleType.TRINKET;
    }

    @Optional.Method(modid = "baubles")
    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        if (Platform.isServer()) {
            if (this.getLegacyMode() != null) {
                this.convertBauble(itemstack, player);
                return;
            }

            magnetLogic(itemstack, player.world, player);
        }
    }

    /**
     * A bauble slot is not part of the player inventory, so the old terminal has to be found and swapped in
     * the handler it actually sits in.
     */
    @Optional.Method(modid = "baubles")
    private void convertBauble(final ItemStack legacy, final EntityLivingBase wearer) {
        if (!(wearer instanceof EntityPlayer)) {
            return;
        }

        final ItemStack converted = WirelessTerminalMigration.convert(legacy);
        if (converted.isEmpty()) {
            return;
        }

        final IItemHandler baubles = BaublesApi.getBaublesHandler((EntityPlayer) wearer);
        for (int slot = 0; slot < baubles.getSlots(); slot++) {
            if (baubles.getStackInSlot(slot) == legacy) {
                ((IBaublesItemHandler) baubles).setStackInSlot(slot, converted);
                return;
            }
        }
    }

    /**
     * Resizes this terminal's battery from the energy cards in it. One card is worth another terminal's
     * charge, where the same card is worth eight of a portable cell's - a terminal starts out with far more
     * than a cell does, and the card is one dense cell either way.
     */
    public void applyEnergyCards(final ItemStack stack, final IUpgradeInventory upgrades) {
        this.setAEMaxPowerMultiplier(stack, 1 + upgrades.getInstalledPoints(CardTraits.ENERGY)
                * AEConfig.instance().getEnergyCardWirelessTerminal());
    }

    public void magnetLogic(ItemStack stack, World worldIn, Entity entityIn) {
        if (entityIn instanceof EntityPlayer player) {
            // An Item is a singleton, so a counter field would be shared by every player. The entity id
            // offset gives each their own beat.
            if ((worldIn.getTotalWorldTime() + entityIn.getEntityId()) % MAGNET_INTERVAL != 0) {
                return;
            }

            if (!entityIn.isSneaking()) {
                NBTTagCompound upgradeNBT = Platform.openNbtData(stack).getCompoundTag("upgrades");
                ItemStackHandler siu = new ItemStackHandler(0);
                siu.deserializeNBT(upgradeNBT);
                for (int s = 0; s < siu.getSlots(); s++) {
                    ItemStack is = siu.getStackInSlot(s);
                    if (AEApi.instance().definitions().materials().cardMagnet().isSameAs(is)) {
                        if (!ItemToggle.isEnabled(is)) {
                            return;
                        }
                        ItemMaterial im = (ItemMaterial) is.getItem();
                        CellConfig c = (CellConfig) im.getConfigInventory(is);
                        CellUpgrades u = (CellUpgrades) im.getUpgradesInventory(is);
                        FuzzyMode fz = null;
                        boolean isFuzzy = u.isInstalled(CardTraits.FUZZY);
                        if (isFuzzy) {
                            fz = im.getFuzzyMode(is);
                        }
                        boolean inverted = u.isInstalled(CardTraits.INVERTER);

                        List<EntityItem> ei = worldIn.getEntitiesWithinAABB(EntityItem.class,
                                new AxisAlignedBB(
                                        entityIn.posX - 5, entityIn.posY - 5, entityIn.posZ - 5,
                                        entityIn.posX + 5, entityIn.posY + 5, entityIn.posZ + 5
                                ));
                        boolean emptyFilter = true;

                        // Resolved lazily: it walks every access point in the grid.
                        WirelessTerminalGuiObject network = null;
                        boolean triedNetwork = false;

                        for (EntityItem i : ei) {
                            if (i.isDead) {
                                continue;
                            }

                            NBTTagCompound itemTag = i.getEntityData();
                            if (itemTag.hasKey("PreventRemoteMovement")) {
                                continue;
                            }

                            if (i.getThrower() != null && i.getThrower().equals(entityIn.getName()) && i.cannotPickup()) {
                                continue;
                            }

                            boolean matched = false;
                            for (int ss = 0; ss < c.getSlots(); ss++) {
                                ItemStack filter = c.getStackInSlot(ss);
                                if (filter.isEmpty()) continue;
                                emptyFilter = false;
                                if (isFuzzy) {
                                    if (Platform.itemComparisons().isFuzzyEqualItem(filter, i.getItem(), fz)) {
                                        matched = true;
                                        break;
                                    }
                                } else {
                                    if (Platform.itemComparisons().isSameItem(filter, i.getItem())) {
                                        matched = true;
                                        break;
                                    }
                                }
                            }

                            final boolean wanted = emptyFilter || matched != inverted;
                            if (!wanted) {
                                continue;
                            }

                            if (!triedNetwork) {
                                triedNetwork = true;
                                network = this.openNetwork(stack, player);
                            }

                            if (!this.storeInNetwork(network, player, i)) {
                                teleportItem(i, entityIn);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The linked network, or null when it is out of range - the magnet reaches exactly as far as opening
     * the terminal does.
     */
    @Nullable
    private WirelessTerminalGuiObject openNetwork(final ItemStack stack, final EntityPlayer player) {
        // The slot arguments are only read back when re-opening a screen, which never happens here.
        final WirelessTerminalGuiObject terminal = new WirelessTerminalGuiObject(this, stack, player, player.world, 0, 0, 0);

        return terminal.rangeCheck() ? terminal : null;
    }

    /**
     * @return true when the whole entity went in. A partial insert shrinks what is left and answers false,
     *         so the remainder still reaches the player.
     */
    private boolean storeInNetwork(@Nullable final WirelessTerminalGuiObject terminal, final EntityPlayer player, final EntityItem entity) {
        if (terminal == null) {
            return false;
        }

        final ItemStack stack = entity.getItem();
        final AEItemKey what = AEItemKey.of(stack);
        if (what == null) {
            return false;
        }

        final long stored = Platform.poweredInsert(terminal, terminal.getInventory(), what, stack.getCount(), new PlayerSource(player, terminal));
        if (stored <= 0) {
            return false;
        }

        if (stored < stack.getCount()) {
            stack.shrink((int) stored);
            return false;
        }

        // What vanilla plays on pickup.
        player.world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ITEM_PICKUP,
                SoundCategory.PLAYERS, 0.2f, ((itemRand.nextFloat() - itemRand.nextFloat()) * 0.7f + 1.0f) * 2.0f);
        entity.setDead();
        return true;
    }

    private void teleportItem(EntityItem i, Entity entityIn) {
        i.motionX = i.motionY = i.motionZ = 0;
        i.setPosition(entityIn.posX, entityIn.posY, entityIn.posZ);
    }
}
