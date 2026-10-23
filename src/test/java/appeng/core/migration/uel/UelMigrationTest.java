/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.migration.uel;


import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.registries.RegistryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import appeng.api.migration.LegacyRewrites;
import appeng.api.migration.MigrationLog;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.core.api.AEFluidKeyType;
import appeng.core.api.AEItemKeyType;


public final class UelMigrationTest {

    private static final MigrationLog QUIET = new MigrationLog() {
        @Override
        public void note(final String message) {
        }

        @Override
        public void lost(final String message) {
        }

        @Override
        public void failed(final String what, final NBTTagCompound original, final Throwable error) {
            throw new AssertionError(what, error);
        }
    };

    @BeforeAll
    public static void bootstrap() throws Exception {
        Bootstrap.register();
        fakeServerSide();
        registerKeyTypes();
        UelMigration.register();
    }

    @BeforeEach
    public void start() {
        LegacyRewrites.startWorld(QUIET);
    }

    @AfterEach
    public void stop() {
        LegacyRewrites.stopWorld();
    }

    @Test
    public void cellEntriesBecomeTheEntryList() throws NBTException {
        final NBTTagCompound cell = nbt("{id:\"appliedenergistics2:storage_cell_4k\",Count:1b,Damage:0s,tag:{"
                + "it:2s,ic:301L,\"#0\":{id:\"minecraft:dye\",Count:1b,Damage:4s,Cnt:9L,Req:0L,Craft:0b},\"@0\":1L,"
                + "\"#1\":{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s,Cnt:1L,Req:0L,Craft:0b},\"@1\":300L,"
                + "\"#2\":{id:\"minecraft:stone\",Count:1b,Damage:0s,Cnt:5L,Req:0L,Craft:0b},\"@2\":5L,"
                + "FuzzyMode:\"IGNORE_ALL\"}}");
        LegacyRewrites.rewriteStack(cell);

        final NBTTagCompound tag = cell.getCompoundTag("tag");
        assertThat(tag.getTag("Items").toString(), is("[{\"#t\":\"appliedenergistics2:item\",\"#\":1L,"
                + "id:\"minecraft:dye\",dmg:4s},{\"#t\":\"appliedenergistics2:item\",\"#\":300L,"
                + "id:\"minecraft:iron_ingot\",dmg:0s}]"));
        assertThat(tag.hasKey("#2"), is(false));
        assertThat(tag.hasKey("@0"), is(false));
        assertThat(tag.getInteger("it"), is(2));
        assertThat(tag.getLong("ic"), is(301L));
        assertThat(tag.getString("FuzzyMode"), is("IGNORE_ALL"));
    }

    @Test
    public void fluidCellEntries() throws NBTException {
        final NBTTagCompound cell = nbt("{id:\"appliedenergistics2:fluid_storage_cell_1k\",Count:1b,Damage:0s,tag:{"
                + "it:1s,\"#0\":{FluidName:\"lava\",Count:0b,Cnt:1L,Req:0L,Craft:0b},\"@0\":4000L}}");
        LegacyRewrites.rewriteStack(cell);

        assertThat(cell.getCompoundTag("tag").getTag("Items").toString(),
                is("[{\"#t\":\"appliedenergistics2:fluid\",\"#\":4000L,fluid:\"lava\"}]"));
    }

    @Test
    public void interfaceStorageAndConfig() throws NBTException {
        final NBTTagCompound tile = nbt("{id:\"appliedenergistics2:interface\",x:1,y:2,z:3,"
                + "config:{\"#0\":{id:\"minecraft:dye\",Count:1b,Damage:4s,Cnt:32L,Req:0L,Craft:0b},\"#1\":{}},"
                + "storage:{Size:9,Items:[{Slot:5,id:\"minecraft:gold_ingot\",Count:17b,Damage:0s},"
                + "{Slot:0,id:\"minecraft:cobblestone\",Count:1b,Damage:0s,stackSize:300}]},"
                + "inv:{item0:{id:\"minecraft:dye\",Count:1b,Damage:4s}}}");
        final NBTTagCompound result = LegacyRewrites.rewriteTile(tile);

        assertThat(result.getCompoundTag("config").getCompoundTag("#0").toString(),
                is("{\"#t\":\"appliedenergistics2:item\",\"#\":32L,id:\"minecraft:dye\",dmg:4s}"));
        assertThat(result.getCompoundTag("storage").getCompoundTag("#5").getLong("#"), is(17L));
        assertThat(result.getCompoundTag("storage").getCompoundTag("#0").getLong("#"), is(300L));
        assertThat(result.hasKey("inv"), is(false));
    }

    @Test
    public void fluidInterfaceBecomesAnInterface() throws NBTException {
        final NBTTagCompound tile = nbt("{id:\"appliedenergistics2:fluid_interface\",x:1,y:2,z:3,"
                + "config:{\"#0\":{FluidName:\"water\",Count:0b,Cnt:1L,Req:0L,Craft:0b}},"
                + "storage:{\"#0\":{FluidName:\"water\",Count:0b,Cnt:12000L,Req:0L,Craft:0b}},"
                + "upgrades:{Size:2,Items:[{Slot:0,id:\"appliedenergistics2:material\",Count:1b,Damage:27s}]}}");
        final NBTTagCompound result = LegacyRewrites.rewriteTile(tile);

        assertThat(result.getString("id"), is("appliedenergistics2:interface"));
        assertThat(result.getCompoundTag("config").getCompoundTag("#0").getLong("#"), is(16000L));
        assertThat(result.getCompoundTag("storage").getCompoundTag("#0").getString("fluid"), is("water"));
        assertThat(result.getCompoundTag("storage").getCompoundTag("#0").getLong("#"), is(12000L));
    }

    @Test
    public void fluidBusIsNarrowedToFluids() throws NBTException {
        final NBTTagCompound bus = nbt("{id:\"appliedenergistics2:blockcablebus\",x:0,y:0,z:0,"
                + "\"def:1\":{id:\"appliedenergistics2:part\",Count:1b,Damage:221s},"
                + "\"extra:1\":{priority:2,config:{\"#0\":{FluidName:\"water\",Count:0b,Cnt:1000L,Req:0L,Craft:0b}}},"
                + "\"def:2\":{id:\"appliedenergistics2:part\",Count:1b,Damage:301s},\"extra:2\":{}}");
        final NBTTagCompound result = LegacyRewrites.rewriteTile(bus);

        final NBTTagCompound fluidBus = result.getCompoundTag("extra:1");
        assertThat(fluidBus.getTag("enabledKeyTypes").toString(), is("[\"appliedenergistics2:fluid\"]"));
        assertThat(fluidBus.getCompoundTag("config").getCompoundTag("#0").getString("fluid"), is("water"));
        assertThat(fluidBus.getInteger("priority"), is(2));
        assertThat(result.getCompoundTag("extra:2").getTag("Enchantments").toString(), is("[{lvl:1s,id:33s}]"));

        final NBTTagCompound item = result.getCompoundTag("def:1");
        LegacyRewrites.rewriteStack(item);
        assertThat(item.getShort("Damage"), is((short) 220));
    }

    @Test
    public void patternLosesItsEmptyTail() throws NBTException {
        final StringBuilder in = new StringBuilder("{id:\"minecraft:iron_ore\",Count:1b,Damage:0s}");
        for (int i = 1; i < 81; i++) {
            in.append(",{}");
        }
        final NBTTagCompound pattern = nbt("{id:\"appliedenergistics2:encoded_pattern\",Count:1b,Damage:0s,tag:{"
                + "crafting:0b,in:[" + in + "],out:[{id:\"minecraft:iron_ingot\",Count:2b,Damage:0s}]}}");
        LegacyRewrites.rewriteStack(pattern);

        assertThat(pattern.getCompoundTag("tag").getTagList("in", 10).tagCount(), is(32));
    }

    @Test
    public void runningJobIsCancelled() throws NBTException {
        final NBTTagCompound core = nbt("{id:\"appliedenergistics2:crafting_storage\",x:0,y:0,z:0,core:1b,"
                + "isComplete:0b,waiting:1b,inventory:[{id:\"minecraft:iron_ore\",Count:1b,Damage:0s,Cnt:3L,"
                + "Req:0L,Craft:0b}],tasks:[{id:\"appliedenergistics2:encoded_pattern\",Count:1b,Damage:0s,"
                + "craftingProgress:2L}],waitingFor:[{id:\"minecraft:iron_ingot\",Count:1b,Damage:0s,Cnt:6L,"
                + "Req:0L,Craft:0b}],link:{CraftID:\"x\"}}");
        final NBTTagCompound result = LegacyRewrites.rewriteTile(core);

        assertThat(result.getBoolean("isComplete"), is(true));
        assertThat(result.getTag("inventory").toString(),
                is("[{\"#t\":\"appliedenergistics2:item\",\"#\":3L,id:\"minecraft:iron_ore\",dmg:0s}]"));
        assertThat(result.hasKey("tasks") || result.hasKey("waitingFor") || result.hasKey("link"), is(false));
    }

    private static NBTTagCompound nbt(final String snbt) throws NBTException {
        return JsonToNBT.getTagFromJson(snbt);
    }

    private static void fakeServerSide() throws Exception {
        final Class<?> handlerType = Class.forName("net.minecraftforge.fml.common.IFMLSidedHandler");
        final Object delegate = Proxy.newProxyInstance(handlerType.getClassLoader(),
                new Class<?>[] { handlerType },
                (proxy, method, args) -> "getSide".equals(method.getName()) ? Side.SERVER : null);

        final Field field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        field.set(FMLCommonHandler.instance(), delegate);
    }

    private static void registerKeyTypes() {
        if (GameRegistry.findRegistry(AEKeyType.class) == null) {
            new RegistryBuilder<AEKeyType>()
                    .setName(AEKeyTypes.REGISTRY_NAME)
                    .setType(AEKeyType.class)
                    .setIDRange(0, 127)
                    .create();
        }
        if (AEKeyTypes.get(new ResourceLocation("appliedenergistics2", "item")) == null) {
            AEKeyTypes.register(new AEItemKeyType());
        }
        if (AEKeyTypes.get(new ResourceLocation("appliedenergistics2", "fluid")) == null) {
            AEKeyTypes.register(new AEFluidKeyType());
        }
    }
}
