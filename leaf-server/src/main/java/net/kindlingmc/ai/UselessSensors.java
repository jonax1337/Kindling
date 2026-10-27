package net.kindlingmc.ai;

import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.AdultSensor;
import net.minecraft.world.entity.ai.sensing.NearestItemSensor;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.sensing.VillagerBabiesSensor;

/**
 * Sensors whose memory nothing reads, ported from Lithium's useless sensors. Only babies follow an adult
 * ({@code BabyFollowAdult} returns early for adults) and only baby villagers play with other baby villagers; goats
 * have no behaviour that goes for items. Such a sensor still runs its schedule and refreshes the targeting ranges
 * as always, it only skips the search.
 * <p>
 * Unlike Lithium, which parks the sensor by rewriting its timer when the mob is created, the decision is taken
 * whenever the sensor is due. That also covers brains made again on loading or after a villager's profession
 * changed, and an adult turned into a baby senses again at the next tick, as vanilla does within one scan interval.
 */
public final class UselessSensors {

    private UselessSensors() {
    }

    public static boolean isUseless(final Sensor<?> sensor, final LivingEntity body) {
        if (sensor instanceof AdultSensor || sensor instanceof VillagerBabiesSensor) {
            return !body.isBaby();
        }
        return sensor instanceof NearestItemSensor && body.is(EntityTypes.GOAT);
    }

    /**
     * Called when the baby flag of a mob changed. A mob that grew up forgets what its baby sensors found; a mob
     * that became a baby gets them to sense again at the next tick.
     */
    public static void onBabyFlagChanged(final AgeableMob mob) {
        if (mob.level().isClientSide()) {
            return;
        }

        final Brain<?> brain = mob.getBrain();
        if (mob.isBaby()) {
            brain.tickSensorOnNextCall(SensorType.NEAREST_ADULT);
            brain.tickSensorOnNextCall(SensorType.VILLAGER_BABIES);
        } else {
            brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT);
            brain.eraseMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES);
        }
    }
}
