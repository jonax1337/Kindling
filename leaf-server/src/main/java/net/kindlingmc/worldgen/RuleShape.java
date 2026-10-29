package net.kindlingmc.worldgen;

import java.util.List;

/**
 * The structure of a material rule tree: the sequences, conditions, negations and blocks the compiler writes as
 * bytecode. Everything else is a leaf, evaluated by the evaluator vanilla compiles for it.
 */
sealed interface RuleShape {

    record Sequence(List<RuleShape> steps) implements RuleShape {
    }

    record Guarded(ConditionShape condition, RuleShape then) implements RuleShape {
    }

    /** A block rule; {@code index} points into the block states of the tree. */
    record Block(int index) implements RuleShape {
    }

    /** {@code index} points into the leaves of the tree. */
    record Leaf(int index) implements RuleShape {
    }

    sealed interface ConditionShape {

        record Negated(ConditionShape target) implements ConditionShape {
        }

        /** {@code index} points into the leaves of the tree. */
        record Leaf(int index) implements ConditionShape {
        }
    }
}
