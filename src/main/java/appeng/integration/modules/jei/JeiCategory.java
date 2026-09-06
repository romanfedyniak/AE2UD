/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.integration.modules.jei;


/**
 * A recipe category this mod offers JEI, and the switch that hides it.
 * <p>
 * A category switched off is never registered rather than registered empty, because an empty one still
 * leaves a tab in the list. The switches are display only: hiding the grindstone's recipes does not stop
 * the grindstone grinding.
 */
public enum JeiCategory {

    GRINDER("grinder", "Recipes for the Grindstone."),
    CONDENSER("condenser", "What the Matter Condenser makes, and how much it costs."),
    INSCRIBER("inscriber", "Recipes for the Inscriber."),
    FACADES("facades", "The facade a block can be cut into."),
    CHARGER("charger", "Charged Certus Quartz, made in the Charger."),
    CHARGER_CHARGING("chargerCharging", "The Charger filling up any tool that holds power."),
    TRANSFORM("transform", "Items dropped in the world that turn into something else."),
    CERTUS_GROWTH("certusGrowth", "Crystal seeds growing in a liquid."),
    ATTUNEMENT("attunement", "Which item attunes a ME P2P Tunnel to which kind."),
    ENTROPY("entropy", "Blocks the Entropy Manipulator heats or cools.");

    private final String key;
    private final String comment;

    JeiCategory(final String key, final String comment) {
        this.key = key;
        this.comment = comment;
    }

    public String key() {
        return this.key;
    }

    public String comment() {
        return this.comment;
    }
}
