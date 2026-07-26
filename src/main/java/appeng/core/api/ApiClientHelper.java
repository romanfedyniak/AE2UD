package appeng.core.api;


import java.util.Collection;
import java.util.List;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.input.Keyboard;

import appeng.api.config.IncludeExclude;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.StorageCell;
import appeng.api.util.IClientHelper;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import appeng.me.storage.BasicCellInventory;


/**
 * Renders the storage-cell tooltip (byte/type usage, partitioning, sticky, contents preview).
 * <p/>
 * Only {@link BasicCellInventory} carries the byte/type accounting and partition-list state this tooltip shows;
 * {@link appeng.me.storage.CreativeCellInventory} never reaches this method - {@code ItemCreativeStorageCell} has
 * always rendered its own, simpler tooltip directly from its configuration, bypassing
 * {@link appeng.api.storage.cells.ICellHandler} entirely (see the old {@code ItemCreativeStorageCell}). The
 * {@code instanceof} check below is a direct consequence of {@link StorageCell} - unlike the old generic
 * {@code ICellInventoryHandler<T>} - carrying no such detail on the common interface.
 */
public class ApiClientHelper implements IClientHelper {

    @Override
    public void addCellInformation(final StorageCell handler, final List<String> lines) {
        if (handler == null) {
            return;
        }

        if (!(handler instanceof BasicCellInventory)) {
            // Creative cells (and any future cell that is not a BasicCellInventory) have nothing more to add here.
            return;
        }

        final BasicCellInventory cellInventory = (BasicCellInventory) handler;

        lines.add(Tooltips.bytesUsed(cellInventory.getUsedBytes(), cellInventory.getTotalBytes()).getFormattedText());
        lines.add(Tooltips.typesUsed(cellInventory.getStoredItemTypes(), cellInventory.getTotalItemTypes()).getFormattedText());

        final boolean showAdvanced = Minecraft.getMinecraft().gameSettings.advancedItemTooltips
                || Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        if (cellInventory.isPreformatted()) {
            final String list = (cellInventory.getPartitionListMode() == IncludeExclude.WHITELIST ? GuiText.Included : GuiText.Excluded).getLocal();

            if (cellInventory.isFuzzy()) {
                lines.add("[" + GuiText.Partitioned.getLocal() + "]" + " - " + list + ' ' + GuiText.Fuzzy.getLocal());
            } else {
                lines.add("[" + GuiText.Partitioned.getLocal() + "]" + " - " + list + ' ' + GuiText.Precise.getLocal());
            }

            if (cellInventory.isSticky()) {
                lines.add(GuiText.Sticky.getLocal());
            }

            if (showAdvanced) {
                final KeyCounter stored = cellInventory.getAvailableStacks();
                final IItemHandler inv = cellInventory.getConfigInventory();

                for (int i = 0; i < inv.getSlots(); i++) {
                    final ItemStack is = inv.getStackInSlot(i);
                    if (is.isEmpty()) {
                        continue;
                    }

                    final AEKey key = keyOf(is);
                    if (key == null) {
                        continue;
                    }

                    if (!cellInventory.isFuzzy()) {
                        final long stocked = stored.get(key);
                        lines.add("[" + is.getDisplayName() + "]" + ": " + key.formatAmount(stocked, AmountFormat.FULL));
                    } else {
                        final Collection<Object2LongMap.Entry<AEKey>> matches = stored.findFuzzy(key, cellInventory.getFuzzyMode());

                        long size = 0;
                        for (final Object2LongMap.Entry<AEKey> entry : matches) {
                            size += entry.getLongValue();
                        }

                        final int[] ids = OreDictionary.getOreIDs(is);
                        if (is.getItem().isDamageable()) {
                            lines.add("[" + is.getDisplayName() + "]" + ": " + size);
                        } else if (ids.length > 0) {
                            final StringBuilder sb = new StringBuilder();
                            for (final int j : ids) {
                                sb.append(OreDictionary.getOreName(j)).append(", ");
                            }
                            lines.add("[{" + sb.substring(0, sb.length() - 2) + "}]" + ": " + key.formatAmount(size, AmountFormat.FULL));
                        }
                    }
                }
            }
        } else {
            if (!AEConfig.instance().showCellContentsPreview()) {
                return;
            }

            if (showAdvanced) {
                for (final Object2LongMap.Entry<AEKey> entry : cellInventory.getAvailableStacks()) {
                    final AEKey key = entry.getKey();
                    lines.add(key.getDisplayName().getFormattedText() + ": " + key.formatAmount(entry.getLongValue(), AmountFormat.FULL));
                }
            }
        }
    }

    /**
     * Resolves a cell config slot's {@link ItemStack} back into the {@link AEKey} it stands for: the item itself
     * for ordinary item stacks, or the wrapped key for a {@link GenericStack} placeholder (fluids and any other
     * non-item type; see {@link GenericStack.Wrapper}).
     */
    private static AEKey keyOf(final ItemStack is) {
        if (GenericStack.isWrapped(is)) {
            final GenericStack wrapped = GenericStack.unwrapItemStack(is);
            return wrapped == null ? null : wrapped.what();
        }
        return AEItemKey.of(is);
    }
}
