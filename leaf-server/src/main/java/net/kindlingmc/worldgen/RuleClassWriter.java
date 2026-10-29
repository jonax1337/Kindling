package net.kindlingmc.worldgen;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;

/**
 * Writes a {@link RuleEvaluator} class for a {@link RuleTree}. Sequences, conditions, negations and blocks become plain
 * bytecode; every leaf is a field holding the evaluator vanilla compiled for it, called from a call site of its own.
 * Vanilla calls all leaves of a kind through one shared site, which the JIT can only dispatch, never inline.
 *
 * <p>The class takes the leaf evaluators as one {@code Object[]} in its constructor, in the order of
 * {@link RuleTree#leaves()}, and reads the block states from its class data, a list in the order of
 * {@link RuleTree#blocks()}.
 */
final class RuleClassWriter {
    /** Methods with more bytecode than HotSpot's {@code HugeMethodLimit} are never JIT compiled. */
    static final int JIT_HUGE_METHOD_BYTES = 8000;
    static final MethodTypeDesc CONSTRUCTOR = MethodTypeDesc.of(CD_void, CD_Object.arrayType());

    private static final ClassDesc SELF = ClassDesc.of(RuleClassWriter.class.getPackageName(), "CompiledMaterialRule");
    private static final ClassDesc BLOCK_STATE = describe(BlockState.class);
    private static final ClassDesc RULE_EVALUATOR = describe(RuleEvaluator.class);
    private static final ClassDesc CONDITION_EVALUATOR = describe(ConditionEvaluator.class);
    private static final MethodTypeDesc TRY_APPLY = MethodTypeDesc.of(BLOCK_STATE, CD_int, CD_int, CD_int);
    private static final MethodTypeDesc TEST = MethodTypeDesc.of(CD_boolean);
    private static final int THIS_SLOT = 0;
    private static final int LEAVES_SLOT = 1;
    private static final int X_SLOT = 1;
    private static final int Y_SLOT = 2;
    private static final int Z_SLOT = 3;

    private final RuleTree tree;
    private final Map<RuleShape.Sequence, Integer> sequenceMethods = new IdentityHashMap<>();
    private final List<RuleShape.Sequence> sequences = new ArrayList<>();

    private RuleClassWriter(RuleTree tree) {
        this.tree = tree;
        this.collectSequences(tree.root());
    }

    static byte[] write(RuleTree tree) {
        byte[] bytes = classFile().build(SELF, new RuleClassWriter(tree)::writeClass);
        checkMethodSizes(bytes);
        return bytes;
    }

    private static ClassFile classFile() {
        ClassHierarchyResolver self = ClassHierarchyResolver.of(Set.of(), Map.of(SELF, CD_Object));
        ClassHierarchyResolver loaded = ClassHierarchyResolver.ofClassLoading(RuleClassWriter.class.getClassLoader());
        return ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(self.orElse(loaded)));
    }

    /** The constructor runs once per chunk, so only the evaluation methods have to stay small enough for the JIT. */
    private static void checkMethodSizes(byte[] bytes) {
        for (MethodModel method : ClassFile.of().parse(bytes).methods()) {
            if (method.methodName().equalsString(ConstantDescs.INIT_NAME)) {
                continue;
            }
            method.findAttribute(Attributes.code()).ifPresent(code -> {
                if (code.codeLength() > JIT_HUGE_METHOD_BYTES) {
                    throw new IllegalStateException("Method " + method.methodName().stringValue() + " has " + code.codeLength()
                        + " bytes of code, more than the JIT compiles");
                }
            });
        }
    }

    private void collectSequences(RuleShape shape) {
        switch (shape) {
            case RuleShape.Sequence sequence -> {
                this.sequenceMethods.put(sequence, this.sequences.size());
                this.sequences.add(sequence);
                sequence.steps().forEach(this::collectSequences);
            }
            case RuleShape.Guarded guarded -> this.collectSequences(guarded.then());
            case RuleShape.Block ignored -> { }
            case RuleShape.Leaf ignored -> { }
        }
    }

    private void writeClass(ClassBuilder clazz) {
        clazz.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL)
            .withSuperclass(CD_Object)
            .withInterfaceSymbols(RULE_EVALUATOR);
        for (int leaf = 0; leaf < this.tree.leaves().size(); leaf++) {
            clazz.withField(leafField(leaf), this.leafType(leaf), ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
        }
        clazz.withMethodBody(ConstantDescs.INIT_NAME, CONSTRUCTOR, ClassFile.ACC_PUBLIC, this::writeConstructor);
        clazz.withMethodBody("tryApply", TRY_APPLY, ClassFile.ACC_PUBLIC, code -> this.writeBody(code, List.of(this.tree.root())));
        for (int method = 0; method < this.sequences.size(); method++) {
            List<RuleShape> steps = this.sequences.get(method).steps();
            clazz.withMethodBody(sequenceMethod(method), TRY_APPLY, ClassFile.ACC_PRIVATE, code -> this.writeBody(code, steps));
        }
    }

    private void writeConstructor(CodeBuilder code) {
        code.aload(THIS_SLOT).invokespecial(CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
        for (int leaf = 0; leaf < this.tree.leaves().size(); leaf++) {
            ClassDesc type = this.leafType(leaf);
            code.aload(THIS_SLOT)
                .aload(LEAVES_SLOT)
                .loadConstant(leaf)
                .aaload()
                .checkcast(type)
                .putfield(SELF, leafField(leaf), type);
        }
        code.return_();
    }

    /** Writes the steps of a sequence: the first step that finds a block returns it, otherwise the method returns null. */
    private void writeBody(CodeBuilder code, List<RuleShape> steps) {
        for (RuleShape step : steps) {
            if (this.writeRule(code, step)) {
                return;
            }
        }
        code.aconst_null().areturn();
    }

    /** Writes code that returns the block the rule finds and falls through when it finds none; true if it never falls through. */
    private boolean writeRule(CodeBuilder code, RuleShape shape) {
        switch (shape) {
            case RuleShape.Sequence sequence -> {
                code.aload(THIS_SLOT).iload(X_SLOT).iload(Y_SLOT).iload(Z_SLOT)
                    .invokespecial(SELF, sequenceMethod(this.sequenceMethods.get(sequence)), TRY_APPLY);
                returnUnlessNull(code);
                return false;
            }
            case RuleShape.Guarded guarded -> {
                Label skip = code.newLabel();
                this.writeCondition(code, guarded.condition());
                code.ifeq(skip);
                this.writeRule(code, guarded.then());
                code.labelBinding(skip);
                return false;
            }
            case RuleShape.Block block -> {
                code.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT, ConstantDescs.DEFAULT_NAME, BLOCK_STATE, block.index()))
                    .areturn();
                return true;
            }
            case RuleShape.Leaf leaf -> {
                code.aload(THIS_SLOT).getfield(SELF, leafField(leaf.index()), RULE_EVALUATOR)
                    .iload(X_SLOT).iload(Y_SLOT).iload(Z_SLOT)
                    .invokeinterface(RULE_EVALUATOR, "tryApply", TRY_APPLY);
                returnUnlessNull(code);
                return false;
            }
        }
    }

    /** Writes code that leaves the condition's result on the stack. */
    private void writeCondition(CodeBuilder code, RuleShape.ConditionShape shape) {
        switch (shape) {
            case RuleShape.ConditionShape.Negated negated -> {
                this.writeCondition(code, negated.target());
                code.iconst_1().ixor();
            }
            case RuleShape.ConditionShape.Leaf leaf -> code.aload(THIS_SLOT)
                .getfield(SELF, leafField(leaf.index()), CONDITION_EVALUATOR)
                .invokeinterface(CONDITION_EVALUATOR, "test", TEST);
        }
    }

    private static void returnUnlessNull(CodeBuilder code) {
        Label notFound = code.newLabel();
        code.dup().ifnull(notFound).areturn();
        code.labelBinding(notFound);
        code.pop();
    }

    private ClassDesc leafType(int leaf) {
        return this.tree.leaves().get(leaf) instanceof RuleTree.Leaf.Rule ? RULE_EVALUATOR : CONDITION_EVALUATOR;
    }

    private static String leafField(int leaf) {
        return "leaf" + leaf;
    }

    private static String sequenceMethod(int method) {
        return "sequence" + method;
    }

    private static ClassDesc describe(Class<?> type) {
        return type.describeConstable().orElseThrow();
    }
}
