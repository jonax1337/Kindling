package net.kindlingmc.worldgen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;

/** The class written for one material rule tree, bound to the leaf evaluators of each chunk. */
final class CompiledRuleTemplate {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodType CONSTRUCTOR = MethodType.methodType(void.class, Object[].class);
    private static final MethodType FACTORY = MethodType.methodType(RuleEvaluator.class, Object[].class);

    private final List<RuleTree.Leaf> leaves;
    private final MethodHandle factory;

    private CompiledRuleTemplate(List<RuleTree.Leaf> leaves, MethodHandle factory) {
        this.leaves = leaves;
        this.factory = factory;
    }

    static CompiledRuleTemplate of(MaterialRule rule) throws ReflectiveOperationException {
        RuleTree tree = RuleTree.of(rule);
        MethodHandles.Lookup compiled = LOOKUP.defineHiddenClassWithClassData(RuleClassWriter.write(tree), tree.blocks(), true);
        MethodHandle constructor = compiled.findConstructor(compiled.lookupClass(), CONSTRUCTOR);
        return new CompiledRuleTemplate(tree.leaves(), constructor.asType(FACTORY));
    }

    /** Compiles every leaf for this chunk, in vanilla's order, and returns the evaluator of the whole tree. */
    RuleEvaluator bind(MaterialRuleContext context) {
        Object[] evaluators = new Object[this.leaves.size()];
        for (int leaf = 0; leaf < evaluators.length; leaf++) {
            evaluators[leaf] = this.leaves.get(leaf).compile(context);
        }
        try {
            return (RuleEvaluator) this.factory.invokeExact(evaluators);
        } catch (Error error) {
            throw error;
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not create the compiled material rule evaluator", failure);
        }
    }
}
