package net.kindlingmc.path;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.Target;
import org.dreeam.leaf.async.path.NodeEvaluatorCache;
import org.dreeam.leaf.async.path.NodeEvaluatorGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class NodeEvaluatorCacheTest {

    private final NodeEvaluator template = new FrogRules();

    @Test
    void aGeneratorGetsBackTheEvaluatorItReturned() {
        NodeEvaluatorGenerator frogs = features -> new FrogRules();

        NodeEvaluator returned = NodeEvaluatorCache.takeNodeEvaluator(frogs, this.template);
        NodeEvaluatorCache.returnNodeEvaluator(returned);
        NodeEvaluator taken = NodeEvaluatorCache.takeNodeEvaluator(frogs, this.template);
        NodeEvaluatorCache.returnNodeEvaluator(taken);

        assertSame(returned, taken);
    }

    @Test
    void anotherGeneratorWithTheSameFeaturesNeverGetsThatEvaluator() {
        NodeEvaluatorGenerator frogs = features -> new FrogRules();
        NodeEvaluatorGenerator axolotls = features -> new AxolotlRules();

        NodeEvaluator frogEvaluator = NodeEvaluatorCache.takeNodeEvaluator(frogs, this.template);
        NodeEvaluatorCache.returnNodeEvaluator(frogEvaluator);
        NodeEvaluator axolotlEvaluator = NodeEvaluatorCache.takeNodeEvaluator(axolotls, this.template);
        NodeEvaluatorCache.returnNodeEvaluator(axolotlEvaluator);

        assertNotSame(frogEvaluator, axolotlEvaluator);
        assertInstanceOf(AxolotlRules.class, axolotlEvaluator);
    }

    private static class FrogRules extends NodeEvaluator {

        @Override
        public Node getStart() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Target getTarget(final double x, final double y, final double z) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getNeighbors(final Node[] neighbors, final Node pos) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PathType getPathTypeOfMob(final PathfindingContext context, final int x, final int y, final int z, final Mob mob) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PathType getPathType(final PathfindingContext context, final int x, final int y, final int z) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class AxolotlRules extends FrogRules {
    }
}
