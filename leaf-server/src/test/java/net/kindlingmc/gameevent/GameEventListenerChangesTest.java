package net.kindlingmc.gameevent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@VanillaFeature
class GameEventListenerChangesTest {

    private static final int LISTENER_RADIUS = 8;

    private final ServerLevel level = mock(ServerLevel.class, RETURNS_DEEP_STUBS);
    private final EuclideanGameEventListenerRegistry registry = new EuclideanGameEventListenerRegistry(this.level, 0, sectionY -> {});
    private final GameEventListener first = listenerAtOrigin();
    private final GameEventListener second = listenerAtOrigin();

    @Test
    void aListenerAddedWhileHandlingAnEventJoinsAfterTheVisit() {
        this.registry.register(this.first);

        assertEquals(List.of(this.first), this.visit(listener -> this.registry.register(this.second)));
        assertEquals(List.of(this.first, this.second), this.visit(listener -> {}));
    }

    @Test
    void aListenerRemovingItselfWhileHandlingAnEventLeavesAfterTheVisit() {
        this.registry.register(this.first);
        this.registry.register(this.second);

        assertEquals(List.of(this.first, this.second), this.visit(this.registry::unregister));
        assertEquals(List.of(), this.visit(listener -> {}));
    }

    @Test
    void aListenerRemovedBeforeItsTurnIsNotVisited() {
        this.registry.register(this.first);
        this.registry.register(this.second);

        assertEquals(List.of(this.first), this.visit(listener -> this.registry.unregister(this.second)));
        assertEquals(List.of(this.first), this.visit(listener -> {}));
    }

    private List<GameEventListener> visit(final Consumer<GameEventListener> whileHandling) {
        final List<GameEventListener> visited = new ArrayList<>();
        this.registry.visitInRangeListeners(null, Vec3.ZERO, null, (listener, position) -> {
            visited.add(listener);
            whileHandling.accept(listener);
        });
        return visited;
    }

    private static GameEventListener listenerAtOrigin() {
        final PositionSource source = mock(PositionSource.class);
        when(source.getPosition(any())).thenReturn(Optional.of(Vec3.ZERO));
        final GameEventListener listener = mock(GameEventListener.class);
        when(listener.getListenerSource()).thenReturn(source);
        when(listener.getListenerRadius()).thenReturn(LISTENER_RADIUS);
        return listener;
    }
}
