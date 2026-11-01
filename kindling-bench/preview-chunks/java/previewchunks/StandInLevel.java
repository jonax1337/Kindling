package previewchunks;

import io.papermc.paper.configuration.WorldConfiguration;
import java.lang.reflect.Field;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import sun.misc.Unsafe;

/**
 * A Level for WorldGenerationContext where no server runs. Paper's flat-bedrock material condition reads
 * level().paperConfig().environment.generateFlatBedrock and nothing else; this stand-in answers false, Paper's default.
 * It is a ServerLevel whose constructor never ran: any other use of it fails.
 */
final class StandInLevel {
    static final Level INSTANCE = create();

    private StandInLevel() {
    }

    private static Level create() {
        try {
            Unsafe unsafe = unsafe();
            WorldConfiguration config = (WorldConfiguration) unsafe.allocateInstance(WorldConfiguration.class);
            config.environment = (WorldConfiguration.Environment) unsafe.allocateInstance(WorldConfiguration.Environment.class);
            Level level = (Level) unsafe.allocateInstance(ServerLevel.class);
            Field paperConfig = Level.class.getDeclaredField("paperConfig");
            paperConfig.setAccessible(true);
            paperConfig.set(level, config);
            return level;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot build the stand-in level", e);
        }
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
