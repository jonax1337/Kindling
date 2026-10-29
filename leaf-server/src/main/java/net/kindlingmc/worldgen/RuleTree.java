package net.kindlingmc.worldgen;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.condition.NotCondition;
import net.minecraft.world.level.levelgen.material.rule.BlockRule;
import net.minecraft.world.level.levelgen.material.rule.ConditionRule;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.SequenceRule;

/**
 * A material rule tree split into its {@link RuleShape} and its leaves. The leaves are listed in the order vanilla's
 * {@code compile} visits them (depth first, a condition before the rule it guards), so binding them per chunk makes
 * the same calls in the same order as vanilla.
 */
record RuleTree(RuleShape root, List<Leaf> leaves, List<BlockState> blocks) {

    static RuleTree of(MaterialRule root) {
        return new Reader().read(root);
    }

    sealed interface Leaf {

        Object compile(MaterialRuleContext context);

        record Rule(MaterialRule rule) implements Leaf {
            @Override
            public Object compile(MaterialRuleContext context) {
                return this.rule.compile(context);
            }
        }

        record Condition(MaterialCondition condition) implements Leaf {
            @Override
            public Object compile(MaterialRuleContext context) {
                return this.condition.compile(context);
            }
        }
    }

    private static final class Reader {
        private final List<Leaf> leaves = new ArrayList<>();
        private final List<BlockState> blocks = new ArrayList<>();
        private final Map<BlockState, Integer> blockIndexes = new IdentityHashMap<>();

        RuleTree read(MaterialRule root) {
            RuleShape shape = this.rule(root);
            return new RuleTree(shape, List.copyOf(this.leaves), List.copyOf(this.blocks));
        }

        private RuleShape rule(MaterialRule rule) {
            return switch (rule) {
                case MaterialRule.HolderHolder(Holder<MaterialRule> holder) -> this.rule(holder.value());
                case SequenceRule sequence when sequence.sequence().size() == 1 -> this.rule(sequence.sequence().getFirst());
                case SequenceRule sequence -> this.sequence(sequence.sequence());
                case ConditionRule guarded -> new RuleShape.Guarded(this.condition(guarded.ifTrue()), this.rule(guarded.thenRun()));
                case BlockRule block -> new RuleShape.Block(this.blockIndex(block.resultState()));
                default -> new RuleShape.Leaf(this.addLeaf(new Leaf.Rule(rule)));
            };
        }

        private RuleShape sequence(List<MaterialRule> rules) {
            List<RuleShape> steps = new ArrayList<>(rules.size());
            for (MaterialRule rule : rules) {
                steps.add(this.rule(rule));
            }
            return new RuleShape.Sequence(List.copyOf(steps));
        }

        private RuleShape.ConditionShape condition(MaterialCondition condition) {
            return switch (condition) {
                case MaterialCondition.HolderHolder(Holder<MaterialCondition> holder) -> this.condition(holder.value());
                case NotCondition not -> new RuleShape.ConditionShape.Negated(this.condition(not.target()));
                default -> new RuleShape.ConditionShape.Leaf(this.addLeaf(new Leaf.Condition(condition)));
            };
        }

        private int blockIndex(BlockState state) {
            return this.blockIndexes.computeIfAbsent(state, added -> {
                this.blocks.add(added);
                return this.blocks.size() - 1;
            });
        }

        private int addLeaf(Leaf leaf) {
            this.leaves.add(leaf);
            return this.leaves.size() - 1;
        }
    }
}
