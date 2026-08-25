package appeng.core.features.registries;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.IGuiHandler;

import appeng.api.definitions.IItemDefinition;
import appeng.api.features.IWirelessTerminalMode;
import appeng.core.AppEng;

/**
 * A mode that is the wireless form of one of AE2's own terminal parts: the part is both the picture on the
 * button and what is crafted in to unlock it.
 */
public class WirelessTerminalMode implements IWirelessTerminalMode {

    private final ResourceLocation id;
    private final IItemDefinition part;
    private final String unlocalizedName;
    private final IGuiHandler guiHandler;

    public WirelessTerminalMode(final ResourceLocation id, final IItemDefinition part,
            final String unlocalizedName, final IGuiHandler guiHandler) {
        this.id = id;
        this.part = part;
        this.unlocalizedName = unlocalizedName;
        this.guiHandler = guiHandler;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    /**
     * Resolved on every call rather than kept: a disabled feature has no item to hand out, and the answer to
     * that is an empty stack every time, not one cached before the registry was finished.
     */
    @Override
    public ItemStack getIcon() {
        return this.part.maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public String getUnlocalizedName() {
        return this.unlocalizedName;
    }

    @Override
    public IGuiHandler getGuiHandler() {
        return this.guiHandler;
    }

    @Override
    public ItemStack getUnlockIngredient() {
        return this.getIcon();
    }

    @Override
    public String toString() {
        return this.id.toString();
    }

    /**
     * The ids AE2's own modes are registered under. Kept here because the terminal item, the migration of the
     * old separate terminals and the recipes all have to name the same five strings.
     */
    public static final class Ids {

        public static final ResourceLocation TERMINAL = of("terminal");
        public static final ResourceLocation CRAFTING = of("crafting_terminal");
        public static final ResourceLocation PATTERN = of("pattern_terminal");
        public static final ResourceLocation INTERFACE = of("interface_terminal");

        private Ids() {
        }

        private static ResourceLocation of(final String path) {
            return new ResourceLocation(AppEng.MOD_ID, path);
        }
    }
}
