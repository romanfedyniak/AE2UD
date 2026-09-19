/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.integrations.hei;

import java.util.List;

import appeng.api.stacks.GenericStack;

/**
 * Says what else a recipe uses that its recipe screen draws but does not list as an ingredient - a bar of
 * energy, of mana, a number written under the arrow - so that moving the recipe into a processing pattern
 * does not leave it out.
 * <p>
 * Register with {@link ExtraInputProviders#register} for the recipe category that draws it. The recipe is
 * handed over as what its screen shows rather than as the viewer's own recipe object, which a recipe layout
 * does not give out, so a provider finds the recipe again by what it makes.
 */
@FunctionalInterface
public interface ExtraInputProvider {

    /**
     * @param inputs  the recipe's inputs as its screen currently shows them, of every kind a converter is
     *                registered for; each alternative shown is the one on screen at the moment.
     * @param outputs its outputs, the same way.
     * @return what else the recipe takes, in the order it should go into the pattern; empty if nothing, or if
     *         the recipe is not one this provider knows.
     */
    List<GenericStack> getExtraInputs(List<GenericStack> inputs, List<GenericStack> outputs);
}
