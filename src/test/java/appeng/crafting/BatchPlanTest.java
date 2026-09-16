/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.registries.RegistryBuilder;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.IPatternInput;
import appeng.api.networking.crafting.IPatternInputs;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.api.AEItemKeyType;
import appeng.me.helpers.BaseActionSource;

/**
 * How a batch picks its ingredients, on the cases where one table shared by every copy is easy to get wrong.
 */
class BatchPlanTest {

    private static final BaseActionSource SRC = new BaseActionSource();

    private static AEItemKey iron;
    private static AEItemKey gold;
    private static AEItemKey stick;
    private static AEItemKey clay;
    private static AEItemKey bucket;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        Bootstrap.register();
        fakeServerSide();
        registerItemKeyType();
        iron = AEItemKey.of(Items.IRON_INGOT);
        gold = AEItemKey.of(Items.GOLD_INGOT);
        stick = AEItemKey.of(Items.STICK);
        clay = AEItemKey.of(Items.CLAY_BALL);
        bucket = AEItemKey.of(Items.WATER_BUCKET);
    }

    @Test
    void nineSlotsOfOneItemAreCountedTogether() {
        final MECraftingInventory inv = inventory(iron, 100);
        final Pattern block = Pattern.crafting().slots(iron, iron, iron, iron, iron, iron, iron, iron, iron);

        final BatchPlan plan = BatchPlan.of(block, inv.getItemList(), 50, null);

        assertNotNull(plan);
        assertEquals(11, plan.getCopies(), "100 iron is eleven blocks of nine, not a hundred");
        assertTrue(plan.draw(inv, plan.getCopies(), SRC));
        assertEquals(1, inv.getItemList().get(iron));
    }

    @Test
    void theScarcestIngredientSizesTheBatch() {
        final MECraftingInventory inv = inventory(iron, 1000, stick, 30);
        final Pattern pick = Pattern.processing().slots(iron, iron, iron, null, stick, null, null, stick);

        final BatchPlan plan = BatchPlan.of(pick, inv.getItemList(), 500, null);

        assertNotNull(plan);
        assertEquals(15, plan.getCopies());
    }

    @Test
    void wantedCapsTheBatch() {
        final BatchPlan plan = BatchPlan.of(Pattern.processing().slots(iron), inventory(iron, 1000).getItemList(),
                7, null);

        assertNotNull(plan);
        assertEquals(7, plan.getCopies());
    }

    @Test
    void notEvenOneCopyIsNoPlan() {
        final Pattern block = Pattern.crafting().slots(iron, iron, iron, iron, iron, iron, iron, iron, iron);

        assertNull(BatchPlan.of(block, inventory(iron, 8).getItemList(), 10, null));
    }

    @Test
    void aSubstituteShortOfTheBatchMakesASmallerOneAndTheNextTakesTheOther() {
        final MECraftingInventory inv = inventory(iron, 3, gold, 100);
        final Pattern either = Pattern.crafting().substituting().slot(0, iron, gold);

        final BatchPlan first = BatchPlan.of(either, inv.getItemList(), 10, null);
        assertNotNull(first);
        assertEquals(3, first.getCopies(), "the preferred option is used while it lasts");
        assertEquals(iron, stackKey(first.getTable(), 0));
        assertTrue(first.draw(inv, first.getCopies(), SRC));

        final BatchPlan second = BatchPlan.of(either, inv.getItemList(), 7, null);
        assertNotNull(second);
        assertEquals(7, second.getCopies());
        assertEquals(gold, stackKey(second.getTable(), 0));
    }

    @Test
    void anOptionClaimedByOneSlotIsNotPromisedToTheNext() {
        final MECraftingInventory inv = inventory(iron, 1, gold, 100);
        final Pattern twice = Pattern.crafting().substituting().slot(0, iron, gold).slot(1, iron, gold);

        final BatchPlan plan = BatchPlan.of(twice, inv.getItemList(), 50, null);

        assertNotNull(plan);
        final InventoryCrafting table = plan.getTable();
        assertEquals(iron, stackKey(table, 0));
        assertEquals(gold, stackKey(table, 1));
        assertEquals(1, plan.getCopies(), "one iron is one copy, however much gold there is");
    }

    @Test
    void aSlotThatRefusesAnItemSkipsIt() {
        final MECraftingInventory inv = inventory(iron, 100, gold, 100);
        final Pattern picky = Pattern.crafting().substituting().slot(0, iron, gold)
                .accepting((slot, stack) -> stack.getItem() != Items.IRON_INGOT);

        final BatchPlan plan = BatchPlan.of(picky, inv.getItemList(), 10, null);

        assertNotNull(plan);
        assertEquals(gold, stackKey(plan.getTable(), 0));
    }

    @Test
    void aWornToolStandsInForANewOne() {
        final AEItemKey fresh = AEItemKey.of(Items.SHEARS, 0);
        final AEItemKey worn = AEItemKey.of(Items.SHEARS, 40);
        final MECraftingInventory inv = inventory(worn, 5);
        final Pattern shear = Pattern.crafting().slots(fresh);

        final BatchPlan plan = BatchPlan.of(shear, inv.getItemList(), 20, null);

        assertNotNull(plan);
        assertEquals(5, plan.getCopies());
        assertEquals(worn, stackKey(plan.getTable(), 0));
    }

    @Test
    void aFabricatedContainerDrawsWhatFillsItAndLaysTheContainer() {
        final MECraftingInventory inv = inventory(clay, 10);
        final Pattern wet = Pattern.crafting().slots(bucket).fabricated(0, new GenericStack(clay, 4));

        final BatchPlan plan = BatchPlan.of(wet, inv.getItemList(), 10, null);

        assertNotNull(plan);
        assertEquals(2, plan.getCopies());
        assertEquals(bucket, stackKey(plan.getTable(), 0));
        assertEquals(Collections.singletonList(clay), new ArrayList<>(plan.getDrawnKeys()));
        assertTrue(plan.draw(inv, 2, SRC));
        assertEquals(2, inv.getItemList().get(clay));
    }

    @Test
    void puttingBackReturnsExactlyWhatWasDrawn() {
        final MECraftingInventory inv = inventory(iron, 100, stick, 100);
        final Pattern pick = Pattern.processing().slots(iron, iron, iron, stick, stick);

        final BatchPlan plan = BatchPlan.of(pick, inv.getItemList(), 20, null);
        assertNotNull(plan);
        assertTrue(plan.draw(inv, 20, SRC));
        assertEquals(40, inv.getItemList().get(iron));
        assertEquals(60, inv.getItemList().get(stick));

        plan.putBack(inv, 20, SRC);
        assertEquals(100, inv.getItemList().get(iron));
        assertEquals(100, inv.getItemList().get(stick));
    }

    @Test
    void drawingAfterTheInventoryChangedTakesNothing() {
        final MECraftingInventory inv = inventory(iron, 100, stick, 100);
        final Pattern pick = Pattern.processing().slots(iron, stick);

        final BatchPlan plan = BatchPlan.of(pick, inv.getItemList(), 100, null);
        assertNotNull(plan);
        inv.extract(stick, 1, Actionable.MODULATE, SRC);

        assertTrue(!plan.draw(inv, 100, SRC));
        assertEquals(100, inv.getItemList().get(iron));
    }

    /**
     * The half of a push a batch shares out: choosing, drawing and laying the table. The rest - power and
     * the bookkeeping of what is owed - was already once per push and is multiplied the same way.
     */
    @Test
    void oneBatchIsCheaperThanTheSameCopiesOneByOne() {
        final int copies = 1000;
        final Pattern block = Pattern.crafting().slots(iron, iron, iron, iron, iron, iron, iron, iron, iron);

        for (int round = 0; round < 5; round++) {
            oneByOne(block, copies);
            together(block, copies);
        }

        final double[] single = new double[9];
        final double[] batch = new double[9];
        for (int round = 0; round < single.length; round++) {
            single[round] = oneByOne(block, copies);
            batch[round] = together(block, copies);
        }

        final double one = median(single);
        final double all = median(batch);
        System.out.printf("%d copies of a nine-slot pattern%n", copies);
        System.out.printf("  one by one: %8.3f ms%n", one);
        System.out.printf("  one batch:  %8.3f ms   %.0fx%n", all, one / all);

        assertTrue(all * 10 < one, "a batch should cost a small fraction of its copies: " + one + " vs " + all);
    }

    private static double oneByOne(final Pattern pattern, final int copies) {
        final MECraftingInventory inv = inventory(iron, 9L * copies);
        final long start = System.nanoTime();
        for (int i = 0; i < copies; i++) {
            final BatchPlan plan = BatchPlan.of(pattern, inv.getItemList(), 1, null);
            plan.draw(inv, 1, SRC);
            plan.getTable();
        }
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    private static double together(final Pattern pattern, final int copies) {
        final MECraftingInventory inv = inventory(iron, 9L * copies);
        final long start = System.nanoTime();
        final BatchPlan plan = BatchPlan.of(pattern, inv.getItemList(), copies, null);
        plan.draw(inv, plan.getCopies(), SRC);
        plan.getTable();
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    private static double median(final double[] values) {
        final double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static AEKey stackKey(final InventoryCrafting table, final int slot) {
        return AEItemKey.of(table.getStackInSlot(slot));
    }

    private static MECraftingInventory inventory(final Object... keysAndAmounts) {
        final KeyCounter counter = new KeyCounter();
        for (int i = 0; i < keysAndAmounts.length; i += 2) {
            counter.add((AEKey) keysAndAmounts[i], ((Number) keysAndAmounts[i + 1]).longValue());
        }
        return new MECraftingInventory(counter);
    }

    /** A pattern built by hand, so a test says exactly what each slot allows. */
    private static final class Pattern implements ICraftingPatternDetails {

        private final boolean crafting;
        private GenericStack[] inputs = new GenericStack[0];
        private final Map<Integer, List<GenericStack>> options = new LinkedHashMap<>();
        private final Map<Integer, GenericStack> fabricated = new LinkedHashMap<>();
        private boolean substitute;
        private BiPredicate<Integer, ItemStack> accepts = (slot, stack) -> true;

        private Pattern(final boolean crafting) {
            this.crafting = crafting;
        }

        static Pattern crafting() {
            return new Pattern(true);
        }

        static Pattern processing() {
            return new Pattern(false);
        }

        Pattern slots(final AEKey... keys) {
            this.inputs = new GenericStack[keys.length];
            for (int x = 0; x < keys.length; x++) {
                this.inputs[x] = keys[x] == null ? null : new GenericStack(keys[x], 1);
            }
            return this;
        }

        Pattern substituting() {
            this.substitute = true;
            return this;
        }

        /** A slot encoded with the first key, which will take any of them. */
        Pattern slot(final int x, final AEKey... choices) {
            if (this.inputs.length <= x) {
                this.inputs = Arrays.copyOf(this.inputs, x + 1);
            }
            this.inputs[x] = new GenericStack(choices[0], 1);
            final List<GenericStack> list = new ArrayList<>();
            for (final AEKey choice : choices) {
                list.add(new GenericStack(choice, 1));
            }
            this.options.put(x, list);
            return this;
        }

        Pattern fabricated(final int x, final GenericStack supplied) {
            this.fabricated.put(x, supplied);
            return this;
        }

        Pattern accepting(final BiPredicate<Integer, ItemStack> accepts) {
            this.accepts = accepts;
            return this;
        }

        @Override
        public IPatternInputs getPatternInputs() {
            final IPatternInput[] slots = new IPatternInput[this.inputs.length];
            for (int x = 0; x < slots.length; x++) {
                if (this.inputs[x] == null) {
                    continue;
                }
                final GenericStack supplied = this.fabricated.get(x);
                final List<GenericStack> choices = supplied != null
                        ? Collections.singletonList(supplied)
                        : this.options.getOrDefault(x, Collections.singletonList(new GenericStack(this.inputs[x].what(), 1)));
                slots[x] = new IPatternInput() {
                    @Override
                    public List<GenericStack> getOptions() {
                        return choices;
                    }

                    @Override
                    public boolean isFabricated() {
                        return supplied != null;
                    }
                };
            }
            return new IPatternInputs.Fixed(slots, IPatternInputs.of(this.inputs).getCondensed());
        }

        @Override
        public boolean isValidItemForSlot(final int slotIndex, final ItemStack itemStack, final World world) {
            return this.accepts.test(slotIndex, itemStack);
        }

        @Override
        public boolean isCraftable() {
            return this.crafting;
        }

        @Override
        public GenericStack[] getInputs() {
            return this.inputs;
        }

        @Override
        public boolean canSubstitute() {
            return this.substitute;
        }

        @Override
        public ItemStack getPattern() {
            return ItemStack.EMPTY;
        }

        @Override
        public GenericStack[] getCondensedOutputs() {
            return new GenericStack[0];
        }

        @Override
        public GenericStack[] getOutputs() {
            return new GenericStack[0];
        }

        @Override
        public ItemStack getOutput(final InventoryCrafting craftingInv, final World world) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public void setPriority(final int priority) {
        }
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
