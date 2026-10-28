package net.kindlingmc.ai;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalSelectorOrderTest {

    private final GoalSelector selector = new GoalSelector();
    private final List<String> canUseCalls = new ArrayList<>();

    @Test
    void iteratesGoalsInInsertionOrderRegardlessOfPriority() {
        final Goal high = this.lookGoal("high");
        final Goal low = this.lookGoal("low");
        final Goal middle = this.lookGoal("middle");
        this.selector.addGoal(3, high);
        this.selector.addGoal(1, low);
        this.selector.addGoal(2, middle);

        assertEquals(List.of(high, low, middle), this.goalsInIterationOrder());
    }

    @Test
    void iteratesTheFirstAddedOfEqualPriorityFirst() {
        final Goal lookAtPlayer = this.lookGoal("lookAtPlayer");
        final Goal randomLookAround = this.lookGoal("randomLookAround");
        this.selector.addGoal(8, lookAtPlayer);
        this.selector.addGoal(8, randomLookAround);

        assertEquals(List.of(lookAtPlayer, randomLookAround), this.goalsInIterationOrder());
    }

    @Test
    void startsTheFirstAddedOfTwoEqualPriorityGoalsSharingAFlag() {
        this.selector.addGoal(8, this.lookGoal("lookAtPlayer"));
        this.selector.addGoal(8, this.lookGoal("randomLookAround"));

        this.selector.tick();

        final List<WrappedGoal> goals = new ArrayList<>(this.selector.getAvailableGoals());
        assertTrue(goals.get(0).isRunning());
        assertFalse(goals.get(1).isRunning());
    }

    @Test
    void asksGoalsWhetherTheyCanBeUsedInInsertionOrder() {
        this.selector.addGoal(2, this.lookGoal("second-priority"));
        this.selector.addGoal(1, this.moveGoal("first-priority"));

        this.selector.tick();

        assertEquals(List.of("second-priority", "first-priority"), this.canUseCalls);
    }

    @Test
    void removesAGoalAndKeepsTheOrderOfTheOthers() {
        final Goal first = this.lookGoal("first");
        final Goal removed = this.lookGoal("removed");
        final Goal last = this.lookGoal("last");
        this.selector.addGoal(1, first);
        this.selector.addGoal(1, removed);
        this.selector.addGoal(1, last);

        this.selector.removeGoal(removed);

        assertEquals(List.of(first, last), this.goalsInIterationOrder());
    }

    @Test
    void keepsOnlyTheFirstWrapperOfTheSameGoal() {
        final Goal goal = this.lookGoal("goal");
        this.selector.addGoal(5, goal);
        this.selector.addGoal(1, goal);

        assertEquals(1, this.selector.getAvailableGoals().size());
        assertEquals(5, this.selector.getAvailableGoals().iterator().next().getPriority());
    }

    private List<Goal> goalsInIterationOrder() {
        final List<Goal> goals = new ArrayList<>();
        for (final WrappedGoal wrapped : this.selector.getAvailableGoals()) {
            goals.add(wrapped.getGoal());
        }
        return goals;
    }

    private Goal lookGoal(final String name) {
        return this.recordingGoal(name, Goal.Flag.LOOK);
    }

    private Goal moveGoal(final String name) {
        return this.recordingGoal(name, Goal.Flag.MOVE);
    }

    private Goal recordingGoal(final String name, final Goal.Flag flag) {
        final Goal goal = new Goal() {
            @Override
            public boolean canUse() {
                GoalSelectorOrderTest.this.canUseCalls.add(name);
                return true;
            }
        };
        goal.setFlags(EnumSet.of(flag));
        return goal;
    }
}
