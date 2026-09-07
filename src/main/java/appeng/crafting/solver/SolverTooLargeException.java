/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.crafting.solver;


/**
 * The request cannot be planned, and saying so is the answer.
 * <p>
 * Thrown for a graph past {@link SolverLimits} and for arithmetic that will not fit in a {@code long}. The
 * second one matters more than it looks: saturating instead would hand back a plan that looks finished and
 * is nonsense, and the nonsense would only surface in the crafting cpu.
 */
public class SolverTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SolverTooLargeException(final String message) {
        super(message);
    }
}
