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

package appeng.client.gui.implementations;


import appeng.api.config.*;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.IJEITargetSlot;
import appeng.container.slot.SlotFake;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.parts.automation.PartExportBus;
import appeng.parts.automation.PartImportBus;
import mezz.jei.api.gui.IGhostIngredientHandler.Target;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import org.lwjgl.input.Mouse;

import javax.annotation.Nonnull;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;


public class GuiUpgradeable extends AEBaseGui implements IJEIGhostIngredients {
    protected final Map<Target<?>, Object> mapTargetSlot = new HashMap<>();
    protected final ContainerUpgradeable cvb;
    protected final IUpgradeableHost bc;

    protected GuiImgButton redstoneMode;
    protected GuiImgButton fuzzyMode;
    protected GuiImgButton craftMode;
    protected GuiImgButton schedulingMode;

    public GuiUpgradeable(final InventoryPlayer inventoryPlayer, final IUpgradeableHost te) {
        this(new ContainerUpgradeable(inventoryPlayer, te));
    }

    public GuiUpgradeable(final ContainerUpgradeable te) {
        super(te);
        this.cvb = te;

        this.bc = (IUpgradeableHost) te.getTarget();
        this.xSize = this.hasToolbox() ? 246 : 211;
        this.ySize = 184;
    }

    protected boolean hasToolbox() {
        return ((ContainerUpgradeable) this.inventorySlots).hasToolbox();
    }

    @Override
    public void initGui() {
        super.initGui();
        this.addButtons();
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> exclusionArea = new ArrayList<>();

        int yOffset = guiTop + 8;

        int visibleButtons = (int) this.buttonList.stream().filter(v -> v.enabled && v.x < guiLeft).count();
        Rectangle sortDir = new Rectangle(guiLeft - 18, yOffset, 18, visibleButtons * 18 + visibleButtons - 2);
        exclusionArea.add(sortDir);

        return exclusionArea;
    }

    protected void addButtons() {
        this.redstoneMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.fuzzyMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 28, Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.craftMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 48, Settings.CRAFT_ONLY, YesNo.NO);
        this.schedulingMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 68, Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT);

        this.buttonList.add(this.craftMode);
        this.buttonList.add(this.redstoneMode);
        this.buttonList.add(this.fuzzyMode);
        this.buttonList.add(this.schedulingMode);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(this.getName().getLocal()), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752);

        if (this.redstoneMode != null) {
            this.redstoneMode.set(this.cvb.getRedStoneMode());
        }

        if (this.fuzzyMode != null) {
            this.fuzzyMode.set(this.cvb.getFuzzyMode());
        }

        if (this.craftMode != null) {
            this.craftMode.set(this.cvb.getCraftingMode());
        }

        if (this.schedulingMode != null) {
            this.schedulingMode.set(this.cvb.getSchedulingMode());
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.handleButtonVisibility();

        this.bindTexture(this.getBackground());
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, 211 - 34, this.ySize);
        if (this.drawUpgrades()) {
            this.drawTexturedModalRect(offsetX + 177, offsetY, 177, 0, 35, 14 + this.cvb.availableUpgrades() * 18);
        }
        if (this.hasToolbox()) {
            this.drawTexturedModalRect(offsetX + 178, offsetY + this.ySize - 90, 178, this.ySize - 90, 68, 68);
        }
    }

    protected void handleButtonVisibility() {
        if (this.redstoneMode != null) {
            this.redstoneMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.REDSTONE) > 0);
        }
        if (this.fuzzyMode != null) {
            this.fuzzyMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.FUZZY) > 0);
        }
        if (this.craftMode != null) {
            this.craftMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.CRAFTING) > 0);
        }
        if (this.schedulingMode != null) {
            this.schedulingMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.CAPACITY) > 0 && this.bc instanceof PartExportBus);
        }
    }

    protected String getBackground() {
        return "guis/bus.png";
    }

    protected boolean drawUpgrades() {
        return true;
    }

    protected GuiText getName() {
        return this.bc instanceof PartImportBus ? GuiText.ImportBus : GuiText.ExportBus;
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.redstoneMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.redstoneMode.getSetting(), backwards));
        }

        if (btn == this.craftMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.craftMode.getSetting(), backwards));
        }

        if (btn == this.fuzzyMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.fuzzyMode.getSetting(), backwards));
        }

        if (btn == this.schedulingMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.schedulingMode.getSetting(), backwards));
        }
    }

    /**
     * @return true if the drop that is being handled right now came from the right mouse button, meaning
     *         the container item itself is wanted rather than its contents.
     *         <p>
     *         JEI hands a {@code Target} no button, so the live mouse state is the only thing left to read.
     *         It is accurate here because {@code accept} runs synchronously inside the click that ends the
     *         drag, while that button is still down.
     */
    private static boolean dropsContainerItself() {
        return Mouse.getEventButton() == 1 || Mouse.isButtonDown(1);
    }

    @Override
    public List<Target<?>> getPhantomTargets(Object ingredient) {
        mapTargetSlot.clear();

        FluidStack fluidStack = null;
        ItemStack itemStack = ItemStack.EMPTY;

        if (ingredient instanceof ItemStack) {
            itemStack = (ItemStack) ingredient;
            fluidStack = FluidUtil.getFluidContained(itemStack);
        } else if (ingredient instanceof FluidStack) {
            fluidStack = (FluidStack) ingredient;
        }

        if (!(ingredient instanceof ItemStack) && !(ingredient instanceof FluidStack)) {
            return Collections.emptyList();
        }

        List<Target<?>> targets = new ArrayList<>();

        List<IJEITargetSlot> slots = new ArrayList<>();
        if (!this.inventorySlots.inventorySlots.isEmpty()) {
            for (Slot slot : this.inventorySlots.inventorySlots) {
                // A filter slot is a valid drop target for a fluid too, not just for an item. This used to
                // be allowed only in the cell workbench, because that was the one screen whose filter could
                // express a fluid at all; every other filter silently offered no target, so a dragged fluid
                // simply did nothing. Config inventories hold any key now, so the exception is the rule.
                if (slot instanceof SlotFake && (!itemStack.isEmpty() || fluidStack != null)) {
                    slots.add((IJEITargetSlot) slot);
                }
            }
        }
        for (IJEITargetSlot slot : slots) {
            ItemStack finalItemStack = itemStack;
            FluidStack finalFluidStack = fluidStack;
            Target<Object> targetItem = new Target<>() {
                @Nonnull
                @Override
                public Rectangle getArea() {
                    if (slot instanceof SlotFake && ((SlotFake) slot).isSlotEnabled()) {
                        return new Rectangle(getGuiLeft() + ((SlotFake) slot).xPos, getGuiTop() + ((SlotFake) slot).yPos, 16, 16);
                    }
                    return new Rectangle();
                }

                @Override
                public void accept(@Nonnull Object ingredient) {
                    PacketInventoryAction p = null;
                    try {
                        if (slot instanceof SlotFake && ((SlotFake) slot).isSlotEnabled()) {
                            // Same rule as clicking a filter slot by hand: left button takes what the
                            // container HOLDS, right button takes the container itself. A dragged fluid
                            // has no container to fall back to, so it goes in either way.
                            //
                            // Both used to end up as a filled bucket, because a filter slot could only
                            // ever express an item - and a bucket filter is a different thing, matching a
                            // bucket in a chest rather than water in a tank, so it looked right and
                            // quietly matched nothing.
                            if (finalFluidStack != null && !(dropsContainerItself() && !finalItemStack.isEmpty())) {
                                p = new PacketInventoryAction(InventoryAction.PLACE_JEI_GHOST_ITEM, slot, new GenericStack(AEFluidKey.of(finalFluidStack), finalFluidStack.amount));
                            } else if (!finalItemStack.isEmpty()) {
                                p = new PacketInventoryAction(InventoryAction.PLACE_JEI_GHOST_ITEM, slot, GenericStack.fromItemStack(finalItemStack));
                            }
                        } else {
                            if (finalFluidStack == null) {
                                return;
                            }
                            // The fluid key travels directly in the packet now - no more smuggling it
                            // through a dummy item's NBT (AEFluidStack.fromFluidStack(...).asItemStackRepresentation()).
                            p = new PacketInventoryAction(InventoryAction.PLACE_JEI_GHOST_ITEM, slot, new GenericStack(AEFluidKey.of(finalFluidStack), finalFluidStack.amount));
                        }
                        NetworkHandler.instance().sendToServer(p);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
            targets.add(targetItem);
            mapTargetSlot.putIfAbsent(targetItem, slot);
        }
        return targets;
    }

    @Override
    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return mapTargetSlot;
    }
}
