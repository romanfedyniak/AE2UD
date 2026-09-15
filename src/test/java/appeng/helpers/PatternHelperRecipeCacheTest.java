package appeng.helpers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Bootstrap;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.fml.relauncher.Side;

import appeng.core.api.AEItemKeyType;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKeyTypes;
import appeng.container.ContainerNull;

/**
 * What it costs to read a pattern, which is what a chunk full of interfaces pays every time it loads.
 * <p>
 * Only vanilla's recipes are registered here - a few hundred, where a modded server has tens of thousands -
 * so the scan measured is the cheapest one that will ever happen in a real game.
 */
class PatternHelperRecipeCacheTest {

    private static final int WARMUP = 3;
    private static final int ROUNDS = 7;

    private static List<ItemStack> patterns;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        Bootstrap.register();
        fakeServerSide();
        registerItemKeyType();
        installPlainWrapper();
        patterns = encodeEveryVanillaRecipe();
    }

    @Test
    void readingThemAgainIsFreeOnceTheRecipeIsKnown() {
        assertFalse(patterns.isEmpty(), "no vanilla recipe could be encoded as a pattern");

        // Class loading, the ore-ingredient pass and the JIT would otherwise all land on the first round.
        for (int round = 0; round < WARMUP; round++) {
            PatternHelper.clearRecipeCache();
            read(patterns);
            read(patterns);
        }

        final double[] scanned = new double[ROUNDS];
        final double[] remembered = new double[ROUNDS];

        for (int round = 0; round < ROUNDS; round++) {
            PatternHelper.clearRecipeCache();
            scanned[round] = timeRead();
            remembered[round] = timeRead();
        }

        final double scan = median(scanned);
        final double recall = median(remembered);

        System.out.println("PatternHelper over " + patterns.size() + " patterns, "
                + CraftingManager.REGISTRY.getKeys().size() + " recipes registered");
        System.out.printf("  recipe scanned:    %7.2f ms  (%6.1f us each)%n",
                scan, scan * 1000 / patterns.size());
        System.out.printf("  recipe remembered: %7.2f ms  (%6.1f us each)   %.1fx%n",
                recall, recall * 1000 / patterns.size(), scan / recall);

        assertTrue(recall * 2 < scan,
                "reading known patterns should be far cheaper than scanning for their recipes: scanned "
                        + scan + " ms, remembered " + recall + " ms");
    }

    private static double timeRead() {
        final long start = System.nanoTime();
        read(patterns);
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    private static void read(final List<ItemStack> stacks) {
        for (final ItemStack stack : stacks) {
            new PatternHelper(stack, null);
        }
    }

    private static double median(final double[] values) {
        final double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    /**
     * One encoded pattern per vanilla recipe that can be laid out in a three by three grid, built from the
     * first stack each of its ingredients accepts.
     */
    private static List<ItemStack> encodeEveryVanillaRecipe() {
        final List<ItemStack> encoded = new ArrayList<>();

        for (final IRecipe recipe : CraftingManager.REGISTRY) {
            final NonNullList<Ingredient> ingredients = recipe.getIngredients();
            if (ingredients.isEmpty() || ingredients.size() > 9) {
                continue;
            }

            final int width = recipe instanceof IShapedRecipe
                    ? ((IShapedRecipe) recipe).getRecipeWidth()
                    : 3;
            final InventoryCrafting grid = new InventoryCrafting(new ContainerNull(), 3, 3);
            boolean laid = true;

            for (int i = 0; i < ingredients.size(); i++) {
                final ItemStack[] options = ingredients.get(i).getMatchingStacks();
                if (options.length == 0) {
                    continue; // an empty slot of a shaped recipe
                }
                final int slot = width < 1 ? i : i / width * 3 + i % width;
                if (slot > 8) {
                    laid = false;
                    break;
                }
                grid.setInventorySlotContents(slot, options[0].copy());
            }

            if (!laid || CraftingManager.findMatchingRecipe(grid, null) == null) {
                continue;
            }

            encoded.add(encode(grid));
        }

        return encoded;
    }

    private static ItemStack encode(final InventoryCrafting grid) {
        final NBTTagList in = new NBTTagList();
        for (int slot = 0; slot < 9; slot++) {
            final ItemStack held = grid.getStackInSlot(slot);
            // An empty slot is an empty tag; writing ItemStack.EMPTY out would read back as a broken one.
            in.appendTag(held.isEmpty() ? new NBTTagCompound() : ItemStackHelper.stackToNBT(held));
        }

        final NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("in", in);
        tag.setTag("out", new NBTTagList());
        tag.setBoolean("crafting", true);

        final ItemStack pattern = new ItemStack(Item.getItemById(1));
        pattern.setTagCompound(tag);
        return pattern;
    }

    /** {@code Platform} reads the side in a static initialiser, and there is no FML around to answer. */
    private static void fakeServerSide() throws Exception {
        final Class<?> handlerType = Class.forName("net.minecraftforge.fml.common.IFMLSidedHandler");
        final Object delegate = Proxy.newProxyInstance(handlerType.getClassLoader(),
                new Class<?>[] { handlerType },
                (proxy, method, args) -> "getSide".equals(method.getName()) ? Side.SERVER : null);

        final Field field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        field.set(FMLCommonHandler.instance(), delegate);
    }

    /** Nothing here is a wrapped key, so the placeholder item the mod installs is not needed - only its hook. */
    private static void installPlainWrapper() {
        GenericStack.setWrapper(new GenericStack.Wrapper() {
            @Override
            public ItemStack wrap(final AEKey what, final long amount) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isWrapped(final ItemStack stack) {
                return false;
            }

            @Override
            public GenericStack unwrap(final ItemStack stack) {
                return null;
            }
        });
    }

    /** The registry is normally created during mod construction, which does not happen here. */
    private static void registerItemKeyType() {
        if (GameRegistry.findRegistry(AEKeyType.class) != null) {
            return;
        }
        new RegistryBuilder<AEKeyType>()
                .setName(AEKeyTypes.REGISTRY_NAME)
                .setType(AEKeyType.class)
                .setIDRange(0, 127)
                .create();
        AEKeyTypes.register(new AEItemKeyType());
    }
}
