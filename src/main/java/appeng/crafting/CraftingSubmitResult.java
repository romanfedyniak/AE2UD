package appeng.crafting;

import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.api.stacks.GenericStack;
import com.github.bsideup.jabel.Desugar;

import javax.annotation.Nullable;


@Desugar
public record CraftingSubmitResult(@Nullable CraftingSubmitErrorCode errorCode, @Nullable Object errorDetail,
                                   @Nullable ICraftingLink link) implements ICraftingSubmitResult {

    private static final CraftingSubmitResult SUCCESS_WITHOUT_LINK = new CraftingSubmitResult(null, null, null);

    public static ICraftingSubmitResult successful(@Nullable final ICraftingLink link) {
        return link == null ? SUCCESS_WITHOUT_LINK : new CraftingSubmitResult(null, null, link);
    }

    public static ICraftingSubmitResult failed(final CraftingSubmitErrorCode errorCode) {
        return new CraftingSubmitResult(errorCode, null, null);
    }

    public static ICraftingSubmitResult noSuitableCpu(final UnsuitableCpus cpus) {
        return new CraftingSubmitResult(CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND, cpus, null);
    }

    public static ICraftingSubmitResult missingIngredient(@Nullable final GenericStack missing) {
        return new CraftingSubmitResult(CraftingSubmitErrorCode.MISSING_INGREDIENT, missing, null);
    }
}
