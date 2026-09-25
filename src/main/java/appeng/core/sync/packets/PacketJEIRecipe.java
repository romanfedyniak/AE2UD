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
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.storage.IStorageService;
import appeng.api.patterns.PatternEncodingMode;
import appeng.api.patterns.PatternEncodingModes;
import appeng.api.patterns.PatternGrid;
import appeng.api.patterns.RecipePlacement;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.MEStorage;
import appeng.container.AEBaseContainer;
import appeng.container.ContainerNull;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.core.AELog;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.crafting.VirtualPatternDetails;
import appeng.helpers.IContainerCraftingPacket;
import appeng.helpers.ICraftingGridContainer;
import appeng.hooks.TickHandler;
import appeng.items.storage.ItemViewCell;
import appeng.util.IWorldCallable;
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
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

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

    /**
     * A recipe bound for a pattern terminal: the mode it is encoded in, and what goes in each slot of that mode's
     * grids, by grid and then by slot. Null for the crafting terminals, which read {@link #recipe}.
     */
    private ResourceLocation mode;
    private Map<String, Map<Integer, List<ItemStack>>> grids;
    static ItemStack[] emptyArray = {ItemStack.EMPTY};

    /**
     * Set by a Ctrl+Move Items transfer - see RecipeTransferHandler. Craft whatever this recipe is
     * missing instead of just leaving the matching slots empty. Adapted from
     * https://github.com/NotMyWing/NAE2.
     */
    private boolean craftMissing;
    /** Ctrl+Shift: skip the confirm screen and start that craft immediately. */
    private boolean craftMissingAutoStart;

    // automatic.
    public PacketJEIRecipe(final ByteBuf stream) throws IOException {
        final ByteArrayInputStream bytes = this.getPacketByteArray(stream);
        bytes.skip(stream.readerIndex());
        final NBTTagCompound comp = CompressedStreamTools.readCompressed(bytes);
        if (comp != null && comp.hasKey("mode")) {
            this.mode = new ResourceLocation(comp.getString("mode"));
            this.grids = new HashMap<>();
            final NBTTagCompound gridsTag = comp.getCompoundTag("grids");
            for (final String grid : gridsTag.getKeySet()) {
                final NBTTagCompound gridTag = gridsTag.getCompoundTag(grid);
                final Map<Integer, List<ItemStack>> slots = new HashMap<>();
                for (final String slot : gridTag.getKeySet()) {
                    final NBTTagList list = gridTag.getTagList(slot, 10);
                    final List<ItemStack> options = new ArrayList<>(list.tagCount());
                    for (int y = 0; y < list.tagCount(); y++) {
                        final ItemStack option = stackFromNBT(list.getCompoundTagAt(y));
                        if (!option.isEmpty()) {
                            options.add(option);
                        }
                    }
                    if (!options.isEmpty()) {
                        slots.put(Integer.parseInt(slot.substring(1)), options);
                    }
                }
                this.grids.put(grid, slots);
            }
        } else if (comp != null) {
            this.recipe = new ArrayList<>();

            // By the slot's own number: a recipe laid out in a bigger grid names only some of its squares.
            for (final String key : comp.getKeySet()) {
                if (!key.startsWith("#")) {
                    continue;
                }
                final int x = Integer.parseInt(key.substring(1));
                while (this.recipe.size() <= x) {
                    this.recipe.add(emptyArray);
                }
                final NBTTagList list = comp.getTagList(key, 10);
                if (list.tagCount() > 0) {
                    final ItemStack[] options = new ItemStack[list.tagCount()];
                    for (int y = 0; y < list.tagCount(); y++) {
                        options[y] = stackFromNBT(list.getCompoundTagAt(y));
                    }
                    this.recipe.set(x, options);
                }
            }

            if (comp.hasKey("outputs")) {
                final NBTTagList outputList = comp.getTagList("outputs", 10);
                this.output = new ArrayList<>();
                for (int z = 0; z < outputList.tagCount(); z++) {
                    this.output.add(stackFromNBT(outputList.getCompoundTagAt(z)));
                }
            }

            this.craftMissing = comp.getBoolean("craftMissing");
            this.craftMissingAutoStart = comp.getBoolean("craftMissingAutoStart");
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

        if (this.mode != null) {
            if (con instanceof ContainerPatternEncoder && inv != null && security != null) {
                this.placeIntoEncoder((ContainerPatternEncoder) con, player, inv, security, crafting);
            }
            return;
        }
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

            if (this.craftMissing) {
                this.tryCraftMissing(pmp, con, cct, grid, crafting, craftMatrix);
            }
        }
    }

    /**
     * Writes the recipe into every grid of the mode it is bound for. A slot given nothing is emptied; a slot given
     * alternatives takes the first the network can craft or holds, then the first the player carries, and
     * otherwise the first of them: a pattern names what a recipe takes, whether or not it is at hand.
     */
    private void placeIntoEncoder(final ContainerPatternEncoder encoder, final EntityPlayer player,
            final IStorageService inv, final ISecurityGrid security, final ICraftingGrid crafting) {
        final PatternEncodingMode target = PatternEncodingModes.get(this.mode);
        if (target == null) {
            return;
        }

        final RecipePlacement placement = new RecipePlacement();
        for (final Map.Entry<String, Map<Integer, List<ItemStack>>> grid : this.grids.entrySet()) {
            for (final Map.Entry<Integer, List<ItemStack>> slot : grid.getValue().entrySet()) {
                final List<GenericStack> options = new ArrayList<>();
                for (final ItemStack option : slot.getValue()) {
                    final GenericStack stack = GenericStack.resolveItemStack(option);
                    if (stack != null) {
                        options.add(stack);
                    }
                }
                placement.put(grid.getKey(), slot.getKey(), options);
            }
        }

        encoder.setEncodingMode(target.getId());
        target.beforeRecipePlaced(encoder, placement);

        final boolean mayLook = security.hasPermission(player, SecurityPermissions.EXTRACT);
        final IItemHandler playerInventory = encoder.getInventoryByName("player");

        for (final PatternGrid grid : target.getGrids()) {
            final IItemHandler slots = encoder.getEncodingGrid(target, grid.getName());
            final Map<Integer, List<ItemStack>> placed = this.grids.getOrDefault(grid.getName(), Collections.emptyMap());
            for (int x = 0; x < slots.getSlots(); x++) {
                final List<ItemStack> options = placed.get(x);
                final ItemStack chosen = options == null ? ItemStack.EMPTY
                        : grid.getRole() == PatternGrid.Role.OUTPUT ? options.get(0).copy()
                        : this.choose(options, inv, crafting, mayLook, playerInventory, encoder);
                ItemHandlerUtil.setStackInSlot(slots, x, chosen);
            }
            encoder.onCraftMatrixChanged(new WrapperInvItemHandler(slots));
        }

        encoder.markPatternLoaded();
    }

    private ItemStack choose(final List<ItemStack> options, final IStorageService inv, final ICraftingGrid crafting,
            final boolean mayLook, final IItemHandler playerInventory, final ContainerPatternEncoder encoder) {
        if (mayLook) {
            final AEKeyFilter filter = ItemViewCell.createFilter(encoder.getViewCells());
            for (final ItemStack option : options) {
                final GenericStack stack = GenericStack.resolveItemStack(option);
                if (stack == null || !filter.matches(stack.what())) {
                    continue;
                }
                if (!crafting.getCraftingFor(stack.what(), null, 0, null).isEmpty()
                        || inv.getInventory().extract(stack.what(), 1, Actionable.SIMULATE, encoder.getActionSource()) > 0) {
                    return option.copy();
                }
            }
        }

        final AdaptorItemHandler carried = new AdaptorItemHandler(playerInventory);
        for (final ItemStack option : options) {
            if (GenericStack.unwrapItemStack(option) == null
                    && !carried.simulateSimilarRemove(option.getCount(), option, FuzzyMode.IGNORE_ALL, null).isEmpty()) {
                return option.copy();
            }
        }

        return options.get(0).copy();
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

    /**
     * Ctrl+Move Items: the crafting matrix still has empty slots after the fill pass above, but every
     * one of them can be filled by a network-craftable item. Builds a {@link VirtualPatternDetails}
     * for the whole recipe - what's already sitting in the matrix plus one craftable variant per
     * still-empty slot - and starts a crafting job for it, exactly like requesting an autocraft from
     * a terminal. Adapted from https://github.com/NotMyWing/NAE2.
     */
    private void tryCraftMissing(final EntityPlayerMP player, final Container con, final IContainerCraftingPacket cct,
            final IGrid grid, final ICraftingGrid crafting, final IItemHandler craftMatrix) {
        if (!(con instanceof AEBaseContainer) || con instanceof ContainerPatternEncoder) {
            return;
        }

        // A terminal with a grid of its own - a bigger bench, an arcane workbench - says how big it is and which
        // recipes it knows; anything else is the plain three by three.
        final ICraftingGridContainer bench = con instanceof ICraftingGridContainer g ? g : null;
        final int width = bench == null ? 3 : bench.getGridWidth();
        final int height = bench == null ? 3 : bench.getGridHeight();
        if (craftMatrix.getSlots() != width * height || this.output == null || this.output.size() != 1) {
            return;
        }

        final ItemStack wantedOutput = this.output.get(0);
        if (wantedOutput == null || wantedOutput.isEmpty()) {
            return;
        }

        // The client only claims to be missing something; verify it against the real recipe before
        // trusting it with a crafting job.
        final InventoryCrafting testFrame = new InventoryCrafting(new ContainerNull(), width, height);
        for (int x = 0; x < craftMatrix.getSlots() && x < this.recipe.size(); x++) {
            if (this.recipe.get(x) != null && this.recipe.get(x).length > 0) {
                testFrame.setInventorySlotContents(x, this.recipe.get(x)[0]);
            }
        }

        final IRecipe matchingRecipe = bench == null ? CraftingManager.findMatchingRecipe(testFrame, player.world)
                : bench.findRecipe(testFrame, player.world);
        if (matchingRecipe == null || !ItemStack.areItemStacksEqual(matchingRecipe.getRecipeOutput(), wantedOutput)) {
            return;
        }

        final KeyCounter ingredients = new KeyCounter();
        boolean anyMissing = false;

        for (int x = 0; x < craftMatrix.getSlots(); x++) {
            final ItemStack inSlot = craftMatrix.getStackInSlot(x);

            if (!inSlot.isEmpty()) {
                final AEKey key = AEItemKey.of(inSlot);
                if (key != null) {
                    ingredients.add(key, inSlot.getCount());
                }
                continue;
            }

            if (x >= this.recipe.size() || this.recipe.get(x) == null) {
                continue;
            }

            for (final ItemStack variant : this.recipe.get(x)) {
                if (variant == null || variant.isEmpty()) {
                    continue;
                }

                final AEKey key = AEItemKey.of(variant);
                if (key == null || crafting.getCraftingFor(key, null, 0, player.world).isEmpty()) {
                    continue;
                }

                ingredients.add(key, variant.getCount());
                anyMissing = true;
                break;
            }
        }

        if (!anyMissing) {
            return;
        }

        final GenericStack[] inputs = new GenericStack[ingredients.size()];
        int i = 0;
        for (final var entry : ingredients) {
            inputs[i++] = new GenericStack(entry.getKey(), entry.getLongValue());
        }

        final AEKey outputKey = AEItemKey.of(wantedOutput);
        if (outputKey == null) {
            return;
        }

        final GenericStack craftWhat = new GenericStack(outputKey, wantedOutput.getCount());
        final VirtualPatternDetails pattern = new VirtualPatternDetails(inputs, new GenericStack[] { craftWhat });

        Future<ICraftingJob> futureJob = null;
        try {
            futureJob = crafting.beginCraftingJobFromDetails(player.world, grid, cct.getActionSource(), craftWhat, pattern, null);

            if (this.craftMissingAutoStart) {
                // Ctrl+Shift: opening the confirm screen just to auto-submit and immediately close it
                // again would flash it on screen for a frame. Wait for the job off-screen instead.
                TickHandler.INSTANCE.addCallable(null, new DeferredCraftSubmit(futureJob, crafting, cct.getActionSource()));
                return;
            }

            final AEBaseContainer base = (AEBaseContainer) con;
            final ContainerOpenContext context = base.getOpenContext();
            if (context == null) {
                futureJob.cancel(true);
                return;
            }

            final TileEntity te = context.getTile();
            if (te != null) {
                Platform.openGUI(player, te, context.getSide(), GuiBridge.GUI_CRAFTING_CONFIRM);
            } else if (base instanceof IInventorySlotAware) {
                final IInventorySlotAware slotAware = (IInventorySlotAware) base;
                Platform.openGUI(player, slotAware.getInventorySlot(), GuiBridge.GUI_CRAFTING_CONFIRM, slotAware.isBaubleSlot());
            }

            if (player.openContainer instanceof ContainerCraftConfirm) {
                final ContainerCraftConfirm ccc = (ContainerCraftConfirm) player.openContainer;
                ccc.setAutoStart(this.craftMissingAutoStart);
                ccc.setJob(futureJob);
                // No amount was ever chosen for this job, so Cancel has nothing to step back to.
                ccc.hasAmountScreen = false;
            } else {
                futureJob.cancel(true);
            }
        } catch (final Throwable e) {
            if (futureJob != null) {
                futureJob.cancel(true);
            }
            AELog.debug(e);
        }
    }

    /**
     * Polls a Ctrl+Shift missing-ingredients job every tick until it resolves, then submits it -
     * without ever opening a GUI for it, so there is nothing on screen to flash.
     */
    private static final class DeferredCraftSubmit implements IWorldCallable<Void> {

        private final Future<ICraftingJob> futureJob;
        private final ICraftingGrid crafting;
        private final IActionSource actionSrc;

        DeferredCraftSubmit(final Future<ICraftingJob> futureJob, final ICraftingGrid crafting, final IActionSource actionSrc) {
            this.futureJob = futureJob;
            this.crafting = crafting;
            this.actionSrc = actionSrc;
        }

        @Override
        public Void call(final World world) {
            if (!this.futureJob.isDone()) {
                TickHandler.INSTANCE.addCallable(null, this);
                return null;
            }

            try {
                final ICraftingJob job = this.futureJob.get();
                if (job != null) {
                    this.crafting.submitJob(job, null, null, true, this.actionSrc);
                }
            } catch (final Exception e) {
                AELog.debug(e);
            }

            return null;
        }
    }

}
