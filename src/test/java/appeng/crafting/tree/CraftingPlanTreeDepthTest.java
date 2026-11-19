/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.crafting.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.registries.RegistryBuilder;

import appeng.api.stacks.AEItemKey;
import appeng.core.api.AEItemKeyType;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;

class CraftingPlanTreeDepthTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        Bootstrap.register();
        final Class<?> handlerType = Class.forName("net.minecraftforge.fml.common.IFMLSidedHandler");
        final Object delegate = Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[] { handlerType },
                (proxy, method, args) -> "getSide".equals(method.getName()) ? Side.SERVER : null);
        final Field field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        field.set(FMLCommonHandler.instance(), delegate);
        if (GameRegistry.findRegistry(AEKeyType.class) == null) {
            new RegistryBuilder<AEKeyType>().setName(AEKeyTypes.REGISTRY_NAME).setType(AEKeyType.class)
                    .setIDRange(0, 127).create();
            AEKeyTypes.register(new AEItemKeyType());
        }
    }

    @Test
    void aChainOfThreeHundredStepsComesThroughThePacket() throws Exception {
        final AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        final CraftingPlanNode root = new CraftingPlanNode(iron, 1);
        CraftingPlanNode node = root;
        for (int level = 0; level < 300; level++) {
            final CraftingPlanSource source = new CraftingPlanSource(CraftingPlanSource.Kind.CRAFT, iron, 1, 1);
            node.getSources().add(source);
            final CraftingPlanNode child = new CraftingPlanNode(iron, 1);
            source.getInputs().add(child);
            node = child;
        }

        final ByteBuf buf = Unpooled.buffer();
        new CraftingPlanTree(root).write(buf);
        final CraftingPlanTree read = CraftingPlanTree.read(buf);

        int depth = 0;
        for (CraftingPlanNode at = read.getRoot(); !at.getSources().isEmpty(); at = at.getSources().get(0).getInputs().get(0)) {
            depth++;
        }
        assertEquals(300, depth);
    }
}
