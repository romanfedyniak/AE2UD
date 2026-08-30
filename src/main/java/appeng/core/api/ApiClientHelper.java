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
import appeng.core.AEClientConfig;
import appeng.core.localization.GuiText;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.text.NumberFormat;
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

        // The share, not the free space, is what stops a cell taking more once the card is in - and it is
        // not something the two lines above can be read to mean. The divisor is named as well as the share,
        // since every question about this number is really a question about what it was divided by.
        if (cellInventory.isEqualDistribution()) {
            lines.add(I18n.format(GuiText.EqualDistributionOf.getUnlocalized(),
                    NumberFormat.getInstance().format(cellInventory.getBytesPerShare()),
                    cellInventory.getShares(), cellInventory.getTotalItemTypes()));
        }

        // Red where it is dangerous. On a partitioned cell the card destroys the overflow of the types
        // the player named; on an unpartitioned one it destroys the overflow of all sixty-three the cell
        // happens to have picked up, which is not what anyone means to set up.
        if (cellInventory.isVoidOverflow()) {
            final String line = GuiText.OverflowDestruction.getLocal();
            lines.add(cellInventory.isPreformatted() ? line : TextFormatting.RED + line);
        }

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

                        // The configured item's own name, for every case.
                        //
                        // This used to print the ore-dictionary names of a non-damageable item instead -
                        // "[{dyeBlack, dye}]" for an ink sac - which was both unreadable and misleading:
                        // a fuzzy partition matches through AEKey.fuzzyEquals, which for items means "same
                        // item, damage ignored". It has nothing to do with the ore dictionary, so those
                        // names described a grouping the cell does not actually use.
                        //
                        // It also had no else branch, so a non-damageable item with no ore-dictionary
                        // entry at all printed no line whatsoever.
                        lines.add("[" + is.getDisplayName() + "]" + ": " + key.formatAmount(size, AmountFormat.FULL));
                    }
                }
            }
        } else {
            if (!AEClientConfig.instance().showCellContentsPreview()) {
                return;
            }

            if (showAdvanced) {
                for (final Object2LongMap.Entry<AEKey> entry : cellInventory.getAvailableStacks()) {
                    final AEKey key = entry.getKey();
                    // Unformatted: see WrappedGenericStack.getItemStackDisplayName - the trailing RESET
                    // that getFormattedText() adds would recolour the rest of this line.
                    lines.add(key.getDisplayName().getUnformattedText() + ": " + key.formatAmount(entry.getLongValue(), AmountFormat.FULL));
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
