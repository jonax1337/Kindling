package net.kindlingmc.worldgen;

import com.mojang.logging.LogUtils;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import org.dreeam.leaf.config.modules.opt.CompiledMaterialRules;
import org.slf4j.Logger;

/**
 * Evaluates material rules (the surface, ore vein and underground block rules of world generation) through a class
 * written once per rule tree instead of vanilla's tree of lambdas. Every leaf is still vanilla's own evaluator, called
 * in the same order with the same arguments, so the blocks are the same; see {@link RuleClassWriter}.
 */
public final class MaterialRuleCompiler {
    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final boolean VERIFY = Boolean.getBoolean("kindling.verifyMaterialRules");
    private static final Map<MaterialRule, Optional<CompiledRuleTemplate>> TEMPLATES = new IdentityHashMap<>();

    private MaterialRuleCompiler() {
    }

    public static RuleEvaluator compile(MaterialRule rule, MaterialRuleContext context) {
        if (!CompiledMaterialRules.enabled) {
            return rule.compile(context);
        }
        return templateFor(rule).map(template -> bind(template, rule, context)).orElseGet(() -> rule.compile(context));
    }

    private static RuleEvaluator bind(CompiledRuleTemplate template, MaterialRule rule, MaterialRuleContext context) {
        RuleEvaluator compiled = template.bind(context);
        return VERIFY ? new CheckedRuleEvaluator(compiled, rule.compile(context)) : compiled;
    }

    private static Optional<CompiledRuleTemplate> templateFor(MaterialRule rule) {
        synchronized (TEMPLATES) {
            return TEMPLATES.computeIfAbsent(rule, MaterialRuleCompiler::createTemplate);
        }
    }

    private static Optional<CompiledRuleTemplate> createTemplate(MaterialRule rule) {
        try {
            return Optional.of(CompiledRuleTemplate.of(rule));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError | StackOverflowError failure) {
            LOGGER.warn("Could not compile a material rule tree; world generation evaluates it the vanilla way", failure);
            return Optional.empty();
        }
    }
}
