/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.me.storage;


import appeng.api.stacks.AEKey;


/**
 * A storage that keeps a count of what it holds between rescans, so it can say whether it holds something without
 * looking. {@link MEInventoryHandler} asks it, on every insertion into the network, in place of a simulated
 * extraction that walks every slot of a big inventory.
 */
public interface ICountedStorage {

    /** Whether the last count found any of {@code what}. */
    boolean holds(AEKey what);
}
