/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.services.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class ModListCheckerTest {

    /** An export that dies after the comparison must run again next time: the digest is kept only once it is done. */
    @Test
    void comparingDoesNotStoreTheDigest() {
        final String[] stored = { null };
        final ExportConfig config = (ExportConfig) Proxy.newProxyInstance(ExportConfig.class.getClassLoader(),
                new Class<?>[] { ExportConfig.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getCache":
                            return "old";
                        case "setCache":
                            stored[0] = (String) args[0];
                            return null;
                        default:
                            return null;
                    }
                });
        final ModListChecker checker = new ModListChecker(config);
        assertEquals(CheckType.UNEQUAL, checker.isEqual(Collections.emptyList()));
        assertNull(stored[0]);
    }
}
