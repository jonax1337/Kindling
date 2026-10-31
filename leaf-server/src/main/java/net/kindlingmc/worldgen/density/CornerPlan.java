package net.kindlingmc.worldgen.density;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What a corner sampler must leave behind besides its output: Paper evaluates every node of the corner graph, so after
 * its call every cache cell of the graph holds the region of that cache, and every leaf that reaches a cell has run.
 * The plan lists each cache and each such leaf with its region path, so the corner sampler can install the cells and
 * run the leaves that pruning skipped. A graph where one cache id or one such leaf sits below two different slices
 * would need Paper's evaluation order to know the last region; such a graph is not compiled (Paper evaluates it).
 */
final class CornerPlan {
    final CacheNode[] caches;
    final RegionPath[] cachePaths;
    final OpaqueNode[] leaves;
    final RegionPath[] leafPaths;

    private CornerPlan(Map<CacheNode, RegionPath> caches, Map<OpaqueNode, RegionPath> leaves) {
        this.caches = caches.keySet().toArray(new CacheNode[0]);
        this.cachePaths = caches.values().toArray(new RegionPath[0]);
        this.leaves = leaves.keySet().toArray(new OpaqueNode[0]);
        this.leafPaths = leaves.values().toArray(new RegionPath[0]);
    }

    /** The plan of the graph below root, or null if a cache id or a touching leaf is reached with two regions. */
    static @Nullable CornerPlan of(Node root) {
        Analysis analysis = new Analysis();
        return analysis.visit(root, RegionPath.ROOT) ? new CornerPlan(analysis.caches, analysis.leaves) : null;
    }

    private static final class Analysis {
        private final Map<CacheNode, RegionPath> caches = new LinkedHashMap<>();
        private final Map<Integer, RegionPath> cacheIds = new HashMap<>();
        private final Map<OpaqueNode, RegionPath> leaves = new LinkedHashMap<>();
        private final Set<Visit> visited = new HashSet<>();

        private record Visit(Node node, RegionPath path) {
        }

        boolean visit(Node node, RegionPath path) {
            if (!this.visited.add(new Visit(node, path))) {
                return true;
            }
            if (node instanceof CacheNode cache) {
                RegionPath previous = this.cacheIds.putIfAbsent(cache.cacheId, path);
                if (previous != null && !previous.equals(path)) {
                    return false;
                }
                this.caches.putIfAbsent(cache, path);
            } else if (node instanceof OpaqueNode leaf && leaf.touchIndex >= 0) {
                RegionPath previous = this.leaves.putIfAbsent(leaf, path);
                if (previous != null && !previous.equals(path)) {
                    return false;
                }
            }
            RegionPath childPath = node instanceof SliceNode slice ? slice.then(path) : path;
            for (Node child : node.children()) {
                if (!this.visit(child, childPath)) {
                    return false;
                }
            }
            return true;
        }
    }
}
