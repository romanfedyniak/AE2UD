package appeng.parts.reporting;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.parts.IPartModel;
import appeng.core.sync.GuiBridge;
import appeng.helpers.PatternHelper;
import appeng.helpers.IPatternUploadHost;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.InvOperation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.List;

public abstract class AbstractPartEncoder extends AbstractPartTerminal implements IPatternUploadHost {

    protected AppEngInternalInventory crafting;
    protected AppEngInternalInventory processing;
    protected AppEngInternalInventory output;
    protected AppEngInternalInventory pattern;

    protected boolean craftingMode = true;
    protected boolean substitute = false;
    protected boolean fluidSubstitute = false;
    protected boolean inverted = false;
    private int patternLoads = 0;

    public AbstractPartEncoder(ItemStack is) {
        super(is);
    }

    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        for (final ItemStack is : this.pattern) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.pattern.readFromNBT(data, "pattern");
        this.output.readFromNBT(data, "outputList");
        this.crafting.readFromNBT(data, "crafting");
        this.processing.readFromNBT(data, "processing");
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.pattern.writeToNBT(data, "pattern");
        this.output.writeToNBT(data, "outputList");
        this.crafting.writeToNBT(data, "crafting");
        this.processing.writeToNBT(data, "processing");
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc, final ItemStack removedStack, final ItemStack newStack) {
        if (inv == this.pattern && slot == 1) {
            final ItemStack is = this.pattern.getStackInSlot(1);
            if (!is.isEmpty() && is.getItem() instanceof ICraftingPatternItem) {
                final ICraftingPatternItem pattern = (ICraftingPatternItem) is.getItem();
                final ICraftingPatternDetails details = pattern.getPatternForItem(is, this.getHost().getTile().getWorld());
                if (details != null) {
                    this.setCraftingRecipe(details.isCraftable());
                    this.setSubstitution(details.canSubstitute());
                    this.setFluidSubstitution(details.canSubstituteFluids());
                    // Before decoding, never after: setInverted empties the side the orientation cannot
                    // reach, and would take the pattern we are about to lay out with it.
                    this.setInverted(PatternHelper.shouldInvert(details.getInputs(), details.getOutputs()));

                    PatternHelper.decodeInto(details, details.isCraftable() ? this.crafting : this.processing, this.output);
                    this.markPatternLoaded();
                }
            }
        } else if (inv == this.crafting) {
            this.fixCraftingRecipes();
        }

        this.getHost().markForSave();
    }

    private void fixCraftingRecipes() {
        if (this.isCraftingRecipe()) {
            for (int x = 0; x < this.crafting.getSlots(); x++) {
                final ItemStack is = this.crafting.getStackInSlot(x);
                if (!is.isEmpty()) {
                    is.setCount(1);
                }
            }
        }
    }

    public boolean isCraftingRecipe() {
        return this.craftingMode;
    }

    public void setCraftingRecipe(final boolean craftingMode) {
        this.craftingMode = craftingMode;
        this.fixCraftingRecipes();
    }

    public boolean isSubstitution() {
        return this.substitute;
    }

    public void setSubstitution(final boolean canSubstitute) {
        this.substitute = canSubstitute;
    }

    public boolean isFluidSubstitution() {
        return this.fluidSubstitute;
    }

    public void setFluidSubstitution(final boolean canSubstituteFluids) {
        this.fluidSubstitute = canSubstituteFluids;
    }

    /**
     * Counts how many times a whole pattern has been laid out over the grid. Only changes matter: the
     * screen watches this to send the terminal back to its first page, since a pattern always fills the
     * grid from the start and landing on a later page reads as nothing having happened.
     */
    public int getPatternLoads() {
        return this.patternLoads;
    }

    public void markPatternLoaded() {
        this.patternLoads++;
    }

    public boolean isInverted() {
        return this.inverted;
    }

    public void setInverted(final boolean inverted) {
        this.inverted = inverted;
        PatternHelper.clearUnreachable(this.processing, this.output, inverted);
    }

    @Override
    public ItemStack getEncodedPattern() {
        return this.pattern.getStackInSlot(1);
    }

    @Override
    public void setEncodedPattern(final ItemStack encoded) {
        this.pattern.setStackInSlot(1, encoded);
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        if (name.equals("crafting")) {
            return this.crafting;
        }

        if (name.equals("processing")) {
            return this.processing;
        }

        if (name.equals("output")) {
            return this.output;
        }

        if (name.equals("pattern")) {
            return this.pattern;
        }

        return super.getInventoryByName(name);
    }

    @Override
    public GuiBridge getGui(final EntityPlayer p) {
        int x = (int) p.posX;
        int y = (int) p.posY;
        int z = (int) p.posZ;
        if (this.getHost().getTile() != null) {
            x = this.getTile().getPos().getX();
            y = this.getTile().getPos().getY();
            z = this.getTile().getPos().getZ();
        }

        if (getGuiBridge().hasPermissions(this.getHost().getTile(), x, y, z, this.getSide(), p)) {
            return getGuiBridge();
        }
        return GuiBridge.GUI_ME;
    }

    abstract public GuiBridge getGuiBridge();

    @Nonnull
    @Override
    abstract public IPartModel getStaticModels();
}
