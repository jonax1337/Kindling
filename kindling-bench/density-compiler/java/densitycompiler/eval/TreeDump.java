package densitycompiler.eval;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;

/** Prints a sampler tree, one node per line; a node seen before (the tree is a DAG) prints as a back reference. */
public final class TreeDump {
    private final StringBuilder text = new StringBuilder();
    private final Map<DensitySampler, Integer> numbers = new IdentityHashMap<>();

    private TreeDump() {
    }

    public static String of(DensitySampler root) {
        TreeDump dump = new TreeDump();
        dump.node(root, 0, "");
        return dump.text.toString();
    }

    private void node(DensitySampler sampler, int depth, String role) {
        this.text.append("  ".repeat(depth)).append(role.isEmpty() ? "" : role + ": ");
        Integer seen = this.numbers.get(sampler);
        if (seen != null) {
            this.text.append("-> #").append(seen).append('\n');
            return;
        }
        int number = this.numbers.size();
        this.numbers.put(sampler, number);
        this.text.append('#').append(number).append(' ').append(label(sampler)).append('\n');
        List<Object> components = SamplerRecords.components(sampler);
        for (int i = 0; i < components.size(); ++i) {
            if (components.get(i) instanceof DensitySampler child) {
                this.node(child, depth + 1, SamplerRecords.componentName(sampler, i));
            }
        }
    }

    private static String label(DensitySampler sampler) {
        String name = sampler.getClass().getName();
        String shortName = name.substring(name.lastIndexOf('.') + 1);
        StringBuilder scalars = new StringBuilder();
        List<Object> components = SamplerRecords.components(sampler);
        for (int i = 0; i < components.size(); ++i) {
            Object value = components.get(i);
            if (value instanceof Number || value instanceof Enum<?> || value instanceof Boolean) {
                scalars.append(scalars.isEmpty() ? "(" : ", ").append(SamplerRecords.componentName(sampler, i)).append('=').append(value);
            }
        }
        return scalars.isEmpty() ? shortName : shortName + scalars + ")";
    }
}
