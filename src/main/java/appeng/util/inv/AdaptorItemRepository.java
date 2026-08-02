package appeng.util.inv;

import appeng.api.config.FuzzyMode;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;
import net.minecraft.item.ItemStack;

import java.util.Iterator;


public class AdaptorItemRepository extends InventoryAdaptor {
    protected final IItemRepository itemRepository;

    public AdaptorItemRepository(IItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    public ItemStack removeItems(int amount, ItemStack filter, IInventoryDestination destination) {
        final ItemStack candidate = this.findExtractable(amount, filter, destination, null);
        return candidate.isEmpty() ? ItemStack.EMPTY : this.itemRepository.extractItem(candidate, amount, false);
    }

    @Override
    public ItemStack simulateRemove(int amount, ItemStack filter, IInventoryDestination destination) {
        return this.findExtractable(amount, filter, destination, null);
    }

    @Override
    public ItemStack removeSimilarItems(int amount, ItemStack filter, FuzzyMode fuzzyMode, IInventoryDestination destination) {
        final ItemStack candidate = this.findExtractable(amount, filter, destination, fuzzyMode);
        return candidate.isEmpty() ? ItemStack.EMPTY : this.itemRepository.extractItem(candidate, amount, false);
    }

    @Override
    public ItemStack simulateSimilarRemove(int amount, ItemStack filter, FuzzyMode fuzzyMode, IInventoryDestination destination) {
        return this.findExtractable(amount, filter, destination, fuzzyMode);
    }

    private ItemStack findExtractable(int amount, ItemStack filter, IInventoryDestination destination,
            FuzzyMode fuzzyMode) {
        for (IItemRepository.ItemRecord record : this.itemRepository.getAllItems()) {
            if (!filter.isEmpty()) {
                final boolean matches = fuzzyMode == null
                        ? Platform.itemComparisons().isSameItem(record.itemPrototype, filter)
                        : Platform.itemComparisons().isFuzzyEqualItem(record.itemPrototype, filter, fuzzyMode);
                if (!matches) {
                    continue;
                }
            }

            final ItemStack extracted = this.itemRepository.extractItem(record.itemPrototype, amount, true);
            if (!extracted.isEmpty() && (destination == null || destination.canInsert(extracted))) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack addItems(ItemStack toBeAdded) {
        return this.addItems(toBeAdded, false);
    }

    protected ItemStack addItems(ItemStack itemsToAdd, final boolean simulate) {
        if (itemsToAdd.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (!simulate) {
            itemsToAdd = itemsToAdd.copy();
        }

        itemsToAdd = this.itemRepository.insertItem(itemsToAdd, simulate);

        if (itemsToAdd.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return itemsToAdd;
    }

    @Override
    public ItemStack simulateAdd(ItemStack toBeSimulated) {
        return this.addItems(toBeSimulated, true);
    }

    @Override
    public boolean containsItems() {
        return !this.itemRepository.getAllItems().isEmpty();
    }

    @Override
    public boolean hasSlots() {
        return true;
    }

    @Override
    public Iterator<ItemSlot> iterator() {
        return null;
    }
}
