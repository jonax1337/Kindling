package net.kindlingmc.recipe;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * First-fit matching of items to recipe ingredients is only as good as vanilla's full matching when no item can go to two
 * different ingredients. This finds the one ingredient an item goes to, and says so when there is a choice.
 */
public final class SoleMatch {

    public static final int NONE = -1;
    public static final int AMBIGUOUS = -2;

    private SoleMatch() {
    }

    /**
     * The index of the one candidate that accepts the stack, {@link #NONE} when no candidate does and {@link #AMBIGUOUS}
     * when two candidates that are not equal to each other do. Equal candidates accept the same stacks, so any of them is
     * as good as another and the first is returned.
     */
    public static <C, S> int indexOf(final List<C> candidates, final S stack, final BiPredicate<C, S> accepts) {
        int found = NONE;
        for (int i = 0, size = candidates.size(); i < size; i++) {
            final C candidate = candidates.get(i);
            if (!accepts.test(candidate, stack)) {
                continue;
            }
            if (found == NONE) {
                found = i;
            } else if (!candidates.get(found).equals(candidate)) {
                return AMBIGUOUS;
            }
        }
        return found;
    }
}
