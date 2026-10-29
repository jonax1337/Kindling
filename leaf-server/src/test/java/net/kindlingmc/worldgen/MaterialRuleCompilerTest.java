package net.kindlingmc.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.condition.NotCondition;
import net.minecraft.world.level.levelgen.material.rule.BlockRule;
import net.minecraft.world.level.levelgen.material.rule.ConditionRule;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import net.minecraft.world.level.levelgen.material.rule.SequenceRule;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The compiled evaluator against vanilla's tree of lambdas: the game's own rule trees compile, and random trees of
 * probe leaves give the same block, ask their leaves in the same order and compile them in the same order.
 */
@Normal
class MaterialRuleCompilerTest {

    private static final int TREES = 2_000;
    private static final int ASSIGNMENTS_PER_TREE = 64;
    private static final int MAX_DEPTH = 5;
    private static final int MAX_SEQUENCE_LENGTH = 4;
    private static final BlockState[] BLOCKS = {
        Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.SAND.defaultBlockState()
    };

    @Test
    void compilesEveryMaterialRuleOfTheGame() {
        Registry<MaterialRule> rules = RegistryHelper.registryAccess().lookupOrThrow(Registries.MATERIAL_RULE);
        assertFalse(rules.entrySet().isEmpty());
        for (Map.Entry<ResourceKey<MaterialRule>, MaterialRule> rule : rules.entrySet()) {
            assertDoesNotThrow(() -> CompiledRuleTemplate.of(rule.getValue()), rule.getKey().toString());
        }
    }

    @Test
    void randomTreesEvaluateLikeVanilla() throws ReflectiveOperationException {
        SplittableRandom random = new SplittableRandom(1);
        for (int tree = 0; tree < TREES; tree++) {
            Probes probes = new Probes();
            MaterialRule rule = new TreeBuilder(random, probes).rule(0);

            RuleEvaluator vanilla = rule.compile(null);
            List<Integer> vanillaCompileOrder = probes.takeCalls();
            RuleEvaluator compiled = CompiledRuleTemplate.of(rule).bind(null);
            assertEquals(vanillaCompileOrder, probes.takeCalls(), "compile order of tree " + tree);

            for (int assignment = 0; assignment < ASSIGNMENTS_PER_TREE; assignment++) {
                probes.answers = random.nextLong();
                BlockState vanillaBlock = vanilla.tryApply(1, 2, 3);
                List<Integer> vanillaAsked = probes.takeCalls();
                BlockState compiledBlock = compiled.tryApply(1, 2, 3);
                assertSame(vanillaBlock, compiledBlock, "block of tree " + tree);
                assertEquals(vanillaAsked, probes.takeCalls(), "evaluation order of tree " + tree);
            }
        }
    }

    /** The leaves of one tree: each answers from one bit of {@link #answers} and records when it is compiled or asked. */
    private static final class Probes {
        private final List<Integer> calls = new ArrayList<>();
        private int leaves;
        long answers;

        int nextId() {
            return this.leaves++;
        }

        boolean answer(int id) {
            this.calls.add(id);
            return (this.answers >>> (id % Long.SIZE) & 1) == 1;
        }

        void compiled(int id) {
            this.calls.add(-1 - id);
        }

        List<Integer> takeCalls() {
            List<Integer> taken = List.copyOf(this.calls);
            this.calls.clear();
            return taken;
        }
    }

    private record ProbeCondition(Probes probes, int id) implements MaterialCondition {
        @Override
        public ConditionEvaluator compile(MaterialRuleContext context) {
            this.probes.compiled(this.id);
            return () -> this.probes.answer(this.id);
        }

        @Override
        public MapCodec<? extends MaterialCondition> codec() {
            throw new UnsupportedOperationException();
        }
    }

    private record ProbeRule(Probes probes, int id, BlockState block) implements MaterialRule {
        @Override
        public RuleEvaluator compile(MaterialRuleContext context) {
            this.probes.compiled(this.id);
            return (blockX, blockY, blockZ) -> this.probes.answer(this.id) ? this.block : null;
        }

        @Override
        public MapCodec<? extends MaterialRule> codec() {
            throw new UnsupportedOperationException();
        }
    }

    private record TreeBuilder(SplittableRandom random, Probes probes) {

        MaterialRule rule(int depth) {
            int kinds = depth >= MAX_DEPTH ? 2 : 5;
            return switch (this.random.nextInt(kinds)) {
                case 0 -> new BlockRule(this.block());
                case 1 -> new ProbeRule(this.probes, this.probes.nextId(), this.block());
                case 2 -> this.sequence(depth);
                case 3 -> new ConditionRule(this.condition(depth), this.rule(depth + 1));
                default -> new MaterialRule.HolderHolder(Holder.direct(this.rule(depth + 1)));
            };
        }

        private MaterialRule sequence(int depth) {
            int length = this.random.nextInt(MAX_SEQUENCE_LENGTH + 1);
            List<MaterialRule> steps = new ArrayList<>(length);
            for (int step = 0; step < length; step++) {
                steps.add(this.rule(depth + 1));
            }
            return new SequenceRule(steps);
        }

        private MaterialCondition condition(int depth) {
            int kinds = depth >= MAX_DEPTH ? 1 : 3;
            return switch (this.random.nextInt(kinds)) {
                case 0 -> new ProbeCondition(this.probes, this.probes.nextId());
                case 1 -> new NotCondition(this.condition(depth + 1));
                default -> new MaterialCondition.HolderHolder(Holder.direct(this.condition(depth + 1)));
            };
        }

        private BlockState block() {
            return BLOCKS[this.random.nextInt(BLOCKS.length)];
        }
    }
}
