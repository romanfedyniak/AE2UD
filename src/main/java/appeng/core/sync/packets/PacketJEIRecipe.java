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

package appeng.core.sync.packets;


import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.MEStorage;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.IContainerCraftingPacket;
import appeng.items.storage.ItemViewCell;
import appeng.util.Platform;
import appeng.util.helpers.ItemHandlerUtil;
import appeng.util.inv.AdaptorItemHandler;
import appeng.util.inv.WrapperInvItemHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.items.IItemHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static appeng.helpers.ItemStackHelper.stackFromNBT;


/**
 * Transfers a HEI/JEI recipe layout into the crafting terminal or the expanded processing pattern
 * terminal (both implement {@link IContainerCraftingPacket}), pulling ingredients from the network (or,
 * in preview mode, merely checking whether the network could supply/craft them).
 * <p/>
 * Ported from the {@code IAEItemStack}/{@code IMEMonitor}/{@code IStorageGrid}/{@code IPartitionList}
 * model to {@code AEKey}/{@code MEStorage}/{@code IStorageService}/{@code AEKeyFilter}. The per-slot fill
 * algorithm (exact match -> put away mismatched item -> extract by identity -> fuzzy-damage fallback ->
 * player-inventory fallback -> preview-only placeholder) is unchanged; only the storage calls are ported.
 * <p/>
 * The old fuzzy fallback used {@code IMEMonitor#getStorageList()#findFuzzy}. Its replacement is the
 * network's own live cache, {@link IStorageService#getCachedInventory()} (a {@code KeyCounter}), which is
 * exactly the fuzzy-search entry point {@code CONTRACT.md} pointed wave 4 at -- nothing needed reporting
 * here.
 */
public class PacketJEIRecipe extends AppEngPacket {

    private List<ItemStack[]> recipe;
    private List<ItemStack> output;
    static ItemStack[] emptyArray = {ItemStack.EMPTY};


    // automatic.
    public PacketJEIRecipe(final ByteBuf stream) throws IOException {
        final ByteArrayInputStream bytes = this.getPacketByteArray(stream);
        bytes.skip(stream.readerIndex());
        final NBTTagCompound comp = CompressedStreamTools.readCompressed(bytes);
        if (comp != null) {
            this.recipe = new ArrayList<>();

            for (int x = 0; x < comp.getKeySet().size(); x++) {
                if (comp.hasKey("#" + x)) {
                    final NBTTagList list = comp.getTagList("#" + x, 10);
                    if (list.tagCount() > 0) {
                        this.recipe.add(new ItemStack[list.tagCount()]);
                        for (int y = 0; y < list.tagCount(); y++) {
                            this.recipe.get(x)[y] = stackFromNBT(list.getCompoundTagAt(y));
                        }
                    } else {
                        this.recipe.add(emptyArray);
                    }
                }
            }

            if (comp.hasKey("outputs")) {
                final NBTTagList outputList = comp.getTagList("outputs", 10);
                this.output = new ArrayList<>();
                for (int z = 0; z < outputList.tagCount(); z++) {
                    this.output.add(stackFromNBT(outputList.getCompoundTagAt(z)));
                }
            }
        }

    }

    // api
    public PacketJEIRecipe(final NBTTagCompound recipe) throws IOException {
        final ByteBuf data = Unpooled.buffer();

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DataOutputStream outputStream = new DataOutputStream(bytes);

        data.writeInt(this.getPacketID());

        CompressedStreamTools.writeCompressed(recipe, outputStream);
        data.writeBytes(bytes.toByteArray());

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        final EntityPlayerMP pmp = (EntityPlayerMP) player;
        final Container con = pmp.openContainer;

        if (!(con instanceof IContainerCraftingPacket)) {
            return;
        }

        final IContainerCraftingPacket cct = (IContainerCraftingPacket) con;
        final IGridNode node = cct.getNetworkNode();

        if (node == null) {
            return;
        }

        final IGrid grid = node.getGrid();
        if (grid == null) {
            return;
        }

        final IStorageService inv = grid.getCache(IStorageService.class);
        final IEnergyGrid energy = grid.getCache(IEnergyGrid.class);
        final ISecurityGrid security = grid.getCache(ISecurityGrid.class);
        final ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
        final IItemHandler craftMatrix = cct.getInventoryByName("crafting");
        final IItemHandler playerInventory = cct.getInventoryByName("player");

        if (inv != null && this.recipe != null && security != null) {
            final MEStorage storage = inv.getInventory();
            final AEKeyFilter filter = ItemViewCell.createFilter(cct.getViewCells());

            for (int x = 0; x < craftMatrix.getSlots(); x++) {
                ItemStack currentItem = craftMatrix.getStackInSlot(x);

                if (x >= this.recipe.size()) {
                    currentItem = ItemStack.EMPTY;
                }

                // prepare slots
                if (!currentItem.isEmpty()) {
                    // already the correct item?
                    ItemStack newItem = this.canUseInSlot(x, currentItem);

                    if (!cct.useRealItems() && this.recipe.get(x) != null) {
                        if (this.recipe.get(x).length > 0) {
                            currentItem.setCount(recipe.get(x)[0].getCount());
                        }
                    }

                    // put away old item
                    if (newItem != currentItem && security.hasPermission(player, SecurityPermissions.INJECT)) {
                        final AEItemKey in = AEItemKey.of(currentItem);
                        long insertedAmount = 0;
                        if (cct.useRealItems() && in != null) {
                            insertedAmount = Platform.poweredInsert(energy, storage, in, currentItem.getCount(), cct.getActionSource());
                        }
                        final long leftover = currentItem.getCount() - insertedAmount;
                        if (cct.useRealItems() && in != null && leftover > 0) {
                            currentItem = in.toStack((int) leftover);
                        } else {
                            currentItem = ItemStack.EMPTY;
                        }
                    }
                }

                if (currentItem.isEmpty() && recipe.size() > x && recipe.get(x) != null) {
                    // for each variant
                    for (int y = 0; y < this.recipe.get(x).length && currentItem.isEmpty(); y++) {
                        final AEItemKey request = AEItemKey.of(this.recipe.get(x)[y]);
                        if (request != null) {
                            // try ae
                            if (filter.matches(request) && security.hasPermission(player, SecurityPermissions.EXTRACT)) {
                                AEItemKey outKey = null;

                                if (cct.useRealItems()) {
                                    long extracted = Platform.poweredExtraction(energy, storage, request, 1, cct.getActionSource());
                                    if (extracted > 0) {
                                        outKey = request;
                                    } else if (request.getItem().isDamageable() || Platform.isGTDamageableItem(request.getItem())) {
                                        for (Object2LongMap.Entry<AEKey> entry : inv.getCachedInventory().findFuzzy(request, FuzzyMode.IGNORE_ALL)) {
                                            if (!(entry.getKey() instanceof AEItemKey candidate)) {
                                                continue;
                                            }
                                            if (entry.getLongValue() == 0) {
                                                continue;
                                            }
                                            if (Platform.isGTDamageableItem(request.getItem())) {
                                                if (candidate.getDamage() != request.getDamage()) {
                                                    continue;
                                                }
                                            }
                                            extracted = Platform.poweredExtraction(energy, storage, candidate, 1, cct.getActionSource());
                                            if (extracted > 0) {
                                                outKey = candidate;
                                                break;
                                            }
                                        }
                                    }
                                } else {
                                    // Query the crafting grid if there is a pattern providing the item
                                    if (!crafting.getCraftingFor(request, null, 0, null).isEmpty()) {
                                        outKey = request;
                                    } else {
                                        // Fall back using an existing item
                                        long simulated = storage.extract(request, 1, Actionable.SIMULATE, cct.getActionSource());
                                        if (simulated > 0) {
                                            outKey = request;
                                        }
                                    }
                                }

                                if (outKey != null) {
                                    final int displayCount = cct.useRealItems() ? 1 : recipe.get(x)[y].getCount();
                                    currentItem = outKey.toStack(displayCount);
                                }
                            }

                            // try inventory
                            if (currentItem.isEmpty()) {
                                AdaptorItemHandler ad = new AdaptorItemHandler(playerInventory);

                                if (cct.useRealItems()) {
                                    currentItem = ad.removeSimilarItems(1, this.recipe.get(x)[y], FuzzyMode.IGNORE_ALL, null);
                                } else {
                                    currentItem = ad.simulateSimilarRemove(recipe.get(x)[y].getCount(), this.recipe.get(x)[y], FuzzyMode.IGNORE_ALL, null);
                                }
                            }
                        }
                    }
                    if (!cct.useRealItems()) {
                        if (currentItem.isEmpty() && recipe.size() > x && this.recipe.get(x) != null) {
                            currentItem = this.recipe.get(x)[0].copy();
                        }
                    }
                }
                ItemHandlerUtil.setStackInSlot(craftMatrix, x, currentItem);
            }

            con.onCraftMatrixChanged(new WrapperInvItemHandler(craftMatrix));

            if (this.output != null && ((con instanceof ContainerPatternEncoder && !((ContainerPatternEncoder) con).isCraftingMode()))) {
                IItemHandler outputSlots = cct.getInventoryByName("output");
                for (int i = 0; i < outputSlots.getSlots(); ++i) {
                    ItemHandlerUtil.setStackInSlot(outputSlots, i, ItemStack.EMPTY);
                }
                for (int i = 0; i < this.output.size() && i < outputSlots.getSlots(); ++i) {
                    if (this.output.get(i) == null || this.output.get(i) == ItemStack.EMPTY) {
                        continue;
                    }
                    ItemHandlerUtil.setStackInSlot(outputSlots, i, this.output.get(i));
                }
            }
        }
    }

    /**
     * @param slot
     * @param is   itemstack
     * @return is if it can be used, else EMPTY
     */
    private ItemStack canUseInSlot(int slot, ItemStack is) {
        if (this.recipe.get(slot) != null) {
            for (ItemStack option : this.recipe.get(slot)) {
                if (ItemStack.areItemStacksEqual(is, option)) {
                    return is;
                }
            }
        }
        return ItemStack.EMPTY;
    }

}
