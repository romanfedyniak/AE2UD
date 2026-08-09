package appeng.api.networking.crafting;

import com.github.bsideup.jabel.Desugar;

/**
 * How many CPUs fell out for which reason when no CPU could take a job. Detail for
 * {@link CraftingSubmitErrorCode#NO_SUITABLE_CPU_FOUND}.
 *
 * @param excluded CPUs kept for players or for automation, which this request is not.
 */
@Desugar
public record UnsuitableCpus(int offline, int busy, int tooSmall, int excluded) {
}
