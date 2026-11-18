/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.core.migration;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.init.Bootstrap;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

class LegacyWorldTest {

    @TempDir
    Path saves;

    @BeforeAll
    static void bootstrap() throws Exception {
        Bootstrap.register();
        final Class<?> handlerType = Class.forName("net.minecraftforge.fml.common.IFMLSidedHandler");
        final Object delegate = Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[] { handlerType },
                (proxy, method, args) -> "getSide".equals(method.getName()) ? Side.SERVER : null);
        final Field field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        field.setAccessible(true);
        field.set(FMLCommonHandler.instance(), delegate);
    }

    /** A world folder may be called "100% Hardcore"; its unreadable level.dat must be logged, not thrown. */
    @Test
    void anUnreadableLevelDatInAFolderWithAPercentSign() throws Exception {
        final File world = this.saves.resolve("100% Hardcore").toFile();
        world.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(world, "level.dat"))) {
            out.write(new byte[] { 1, 2, 3, 4 });
        }
        final Method savedVersion = LegacyWorld.class.getDeclaredMethod("savedVersion", File.class);
        savedVersion.setAccessible(true);
        assertNull(savedVersion.invoke(null, world));
    }
}
