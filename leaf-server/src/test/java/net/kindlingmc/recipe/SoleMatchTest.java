package net.kindlingmc.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

class SoleMatchTest {

    /** A candidate is a set of accepted items written as a string, "oak birch" accepts both. */
    private static final BiPredicate<String, String> ACCEPTS = (candidate, item) -> List.of(candidate.split(" ")).contains(item);

    @Test
    void findsTheOnlyCandidateThatAcceptsTheItem() {
        assertEquals(1, SoleMatch.indexOf(List.of("oak", "birch"), "birch", ACCEPTS));
    }

    @Test
    void saysNoneWhenNoCandidateAcceptsTheItem() {
        assertEquals(SoleMatch.NONE, SoleMatch.indexOf(List.of("oak", "birch"), "stone", ACCEPTS));
    }

    @Test
    void saysNoneForNoCandidates() {
        assertEquals(SoleMatch.NONE, SoleMatch.indexOf(new ArrayList<String>(), "oak", ACCEPTS));
    }

    @Test
    void saysAmbiguousWhenAnOverlappingIngredientAlsoAcceptsTheItem() {
        assertEquals(SoleMatch.AMBIGUOUS, SoleMatch.indexOf(List.of("oak birch", "oak"), "oak", ACCEPTS));
    }

    @Test
    void saysAmbiguousWhenTheOverlapComesLast() {
        assertEquals(SoleMatch.AMBIGUOUS, SoleMatch.indexOf(List.of("oak", "oak birch"), "oak", ACCEPTS));
    }

    @Test
    void isNotAmbiguousForTheItemOnlyOneOfOverlappingIngredientsAccepts() {
        assertEquals(0, SoleMatch.indexOf(List.of("oak birch", "oak"), "birch", ACCEPTS));
    }

    @Test
    void takesTheFirstOfEqualCandidates() {
        assertEquals(0, SoleMatch.indexOf(List.of("iron", "iron"), "iron", ACCEPTS));
    }

    @Test
    void takesTheFirstOfEqualCandidatesAfterANonMatchingOne() {
        assertEquals(1, SoleMatch.indexOf(List.of("gold", "iron", "gold", "iron"), "iron", ACCEPTS));
    }
}
