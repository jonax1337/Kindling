package net.kindlingmc.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.AdultSensor;
import net.minecraft.world.entity.ai.sensing.NearestItemSensor;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.sensing.VillagerBabiesSensor;
import net.minecraft.world.level.Level;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@VanillaFeature
class UselessSensorsTest {

    private static final int SCAN_RATE = 20;

    private final LivingEntity adult = livingEntity(false);
    private final LivingEntity baby = livingEntity(true);

    @Test
    void theAdultSensorIsUselessForAdultsOnly() {
        assertTrue(UselessSensors.isUseless(new AdultSensor(), this.adult));
        assertFalse(UselessSensors.isUseless(new AdultSensor(), this.baby));
    }

    @Test
    void theVillagerBabiesSensorIsUselessForAdultsOnly() {
        assertTrue(UselessSensors.isUseless(new VillagerBabiesSensor(), this.adult));
        assertFalse(UselessSensors.isUseless(new VillagerBabiesSensor(), this.baby));
    }

    @Test
    void theItemSensorIsUselessForGoatsOnly() {
        final LivingEntity goat = livingEntity(false);
        when(goat.is(EntityTypes.GOAT)).thenReturn(true);

        assertTrue(UselessSensors.isUseless(new NearestItemSensor(), goat));
        assertFalse(UselessSensors.isUseless(new NearestItemSensor(), this.adult));
    }

    @Test
    void otherSensorsAreNeverUseless() {
        assertFalse(UselessSensors.isUseless(new NearestLivingEntitySensor<>(), this.adult));
    }

    @Test
    void aMobThatGrewUpForgetsWhatItsBabySensorsFound() {
        final Brain<?> brain = mock(Brain.class);

        UselessSensors.onBabyFlagChanged(ageableMob(brain, false, false));

        verify(brain).eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT);
        verify(brain).eraseMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES);
        verify(brain, never()).tickSensorOnNextCall(SensorType.NEAREST_ADULT);
    }

    @Test
    void aMobThatBecameABabyGetsItsBabySensorsToSenseAtOnce() {
        final Brain<?> brain = mock(Brain.class);

        UselessSensors.onBabyFlagChanged(ageableMob(brain, true, false));

        verify(brain).tickSensorOnNextCall(SensorType.NEAREST_ADULT);
        verify(brain).tickSensorOnNextCall(SensorType.NEAREST_ADULT_ANY_TYPE);
        verify(brain).tickSensorOnNextCall(SensorType.VILLAGER_BABIES);
        verify(brain, never()).eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT);
    }

    @Test
    void theClientTouchesNoBrain() {
        final Brain<?> brain = mock(Brain.class);

        UselessSensors.onBabyFlagChanged(ageableMob(brain, false, true));

        verify(brain, never()).eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT);
    }

    @Test
    void aSensorSkippedAsUselessKeepsItsScheduleAndSensesAgainWhenWoken() {
        final CountingAdultSensor sensor = new CountingAdultSensor();

        tick(sensor, this.adult, 1);
        assertEquals(0, sensor.senses);

        tick(sensor, this.adult, SCAN_RATE - 1);
        sensor.tickOnNextCall();
        tick(sensor, this.baby, 1);
        assertEquals(1, sensor.senses);

        tick(sensor, this.baby, SCAN_RATE - 1);
        assertEquals(1, sensor.senses);
        tick(sensor, this.baby, 1);
        assertEquals(2, sensor.senses);
    }

    private static void tick(final Sensor<LivingEntity> sensor, final LivingEntity body, final int times) {
        for (int i = 0; i < times; i++) {
            sensor.tick(mock(ServerLevel.class), body);
        }
    }

    private static LivingEntity livingEntity(final boolean baby) {
        final LivingEntity entity = mock(LivingEntity.class);
        when(entity.isBaby()).thenReturn(baby);
        return entity;
    }

    private static AgeableMob ageableMob(final Brain<?> brain, final boolean baby, final boolean clientSide) {
        final Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(clientSide);
        final AgeableMob mob = mock(AgeableMob.class);
        when(mob.level()).thenReturn(level);
        when(mob.isBaby()).thenReturn(baby);
        when(mob.getBrain()).thenAnswer(invocation -> brain);
        return mob;
    }

    private static final class CountingAdultSensor extends AdultSensor {

        private int senses;

        private CountingAdultSensor() {
            this.scanRateCache = SCAN_RATE;
        }

        @Override
        protected void doTick(final ServerLevel level, final LivingEntity body) {
            this.senses++;
        }
    }
}
