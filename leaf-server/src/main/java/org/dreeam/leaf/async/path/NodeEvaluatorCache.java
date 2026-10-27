package org.dreeam.leaf.async.path;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap; // Kindling - one pool per generator (Leaf PR 943)
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.apache.commons.lang3.Validate;

import java.util.ArrayDeque;

public final class NodeEvaluatorCache {

    private static final Reference2ObjectOpenHashMap<NodeEvaluatorGenerator, Int2ObjectOpenHashMap<ArrayDeque<NodeEvaluator>>> NODE_EVALUATORS = new Reference2ObjectOpenHashMap<>(); // Kindling - one pool per generator (Leaf PR 943)
    private static final Reference2ObjectOpenHashMap<NodeEvaluator, ArrayDeque<NodeEvaluator>> NODE_EVALUATOR_TO_POOL = new Reference2ObjectOpenHashMap<>(); // Kindling - one pool per generator (Leaf PR 943)

    public static final ThreadLocal<BinaryHeap> HEAP_LOCAL = ThreadLocal.withInitial(BinaryHeap::new);
    public static final ThreadLocal<Node[]> NEIGHBORS_LOCAL = ThreadLocal.withInitial(() -> new Node[32]);

    private NodeEvaluatorCache() {
    }

    public static synchronized NodeEvaluator takeNodeEvaluator(NodeEvaluatorGenerator generator, NodeEvaluator localNodeEvaluator) {
        final int nodeEvaluatorFeatures = NodeEvaluatorFeatures.fromNodeEvaluator(localNodeEvaluator);
        final ArrayDeque<NodeEvaluator> pool = NODE_EVALUATORS.computeIfAbsent(generator, key -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(nodeEvaluatorFeatures, key -> new ArrayDeque<>()); // Kindling - one pool per generator (Leaf PR 943)
        NodeEvaluator nodeEvaluator = pool.poll(); // Kindling - one pool per generator (Leaf PR 943)

        if (nodeEvaluator == null) {
            nodeEvaluator = generator.generate(NodeEvaluatorFeatures.unpack(nodeEvaluatorFeatures));
        }

        NODE_EVALUATOR_TO_POOL.put(nodeEvaluator, pool); // Kindling - one pool per generator (Leaf PR 943)

        return nodeEvaluator;
    }

    public static synchronized void returnNodeEvaluator(final NodeEvaluator nodeEvaluator) {
        final ArrayDeque<NodeEvaluator> pool = NODE_EVALUATOR_TO_POOL.remove(nodeEvaluator); // Kindling - one pool per generator (Leaf PR 943)
        Validate.notNull(pool, "NodeEvaluator already returned"); // Kindling - one pool per generator (Leaf PR 943)

        pool.offer(nodeEvaluator); // Kindling - one pool per generator (Leaf PR 943)
    }

    public static synchronized void removeNodeEvaluator(final NodeEvaluator nodeEvaluator) {
        NODE_EVALUATOR_TO_POOL.remove(nodeEvaluator); // Kindling - one pool per generator (Leaf PR 943)
    }
}
