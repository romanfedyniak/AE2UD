package appeng.api.networking.crafting;

import javax.annotation.Nullable;

/**
 * The result of {@link ICraftingGrid#submitJob}: either a started job, or the reason it did not start.
 */
public interface ICraftingSubmitResult {

    default boolean successful() {
        return this.errorCode() == null;
    }

    /**
     * @return why the job did not start, or null if it did.
     */
    @Nullable
    CraftingSubmitErrorCode errorCode();

    /**
     * @return more about the failure, of a type the {@link #errorCode()} decides, or null if there is nothing
     * to add.
     */
    @Nullable
    Object errorDetail();

    /**
     * @return the link of the started job, present only for a successful request made by a requester. Keep
     * track of it the way {@link ICraftingRequester#getRequestedJobs()} expects.
     */
    @Nullable
    ICraftingLink link();
}
