package net.kindlingmc.bench;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.util.Vector;

/**
 * A TNT chamber as cannons and TNT farms build them: water in a closed box of obsidian, so the explosions break no
 * block and push nothing outside, but every explosion still traces its rays and pushes the TNT around it. The run's
 * clock lights TNT in it; vanilla lets every one of them explode {@value #FUSE_TICKS} ticks later.
 */
final class TntChamber implements Contraption, Clocked {

    /** The vanilla fuse of TNT lit by fire or redstone. */
    static final int FUSE_TICKS = 80;

    private static final int WALL_MIN = 5;
    private static final int WALL_MAX = 9;
    private static final int ROOF = 1 + WALL_MAX - WALL_MIN;
    private static final double CENTRE = (WALL_MIN + WALL_MAX + 1) / 2.0;
    private static final int WATER_BOTTOM = 2;

    private final ChunkSite site;
    private final int tntPerLighting;
    private final int stepsBetweenLightings;
    private int steps;

    private TntChamber(ChunkSite site, int tntPerLighting, int stepsBetweenLightings) {
        this.site = site;
        this.tntPerLighting = tntPerLighting;
        this.stepsBetweenLightings = stepsBetweenLightings;
    }

    /**
     * One TNT every eighth clock step, as often as one fuse lasts, so at most one is alive at a time: the 32 chambers
     * of a default run together stay well below Spigot's {@code max-tnt-per-tick} of 100, and an unloaded server
     * ticks every one of them.
     */
    static TntChamber counted(ChunkSite site) {
        return new TntChamber(site, 1, FUSE_TICKS / STEP_TICKS);
    }

    /** Four TNT at every clock step, about 32 alive at a time: a lag machine. */
    static TntChamber lagMachine(ChunkSite site) {
        return new TntChamber(site, 4, 1);
    }

    @Override
    public void build() {
        for (int x = WALL_MIN; x <= WALL_MAX; x++) {
            for (int y = 1; y <= ROOF; y++) {
                for (int z = WALL_MIN; z <= WALL_MAX; z++) {
                    site.place(x, y, z, isWall(x, y, z) ? Material.OBSIDIAN : Material.WATER);
                }
            }
        }
    }

    private static boolean isWall(int x, int y, int z) {
        return x == WALL_MIN || x == WALL_MAX || z == WALL_MIN || z == WALL_MAX || y == 1 || y == ROOF;
    }

    /** Lights TNT at rest in the middle of the water, without the random push lit TNT gets, so every run is alike. */
    @Override
    public void clockStep() {
        if (steps++ % stepsBetweenLightings != 0) {
            return;
        }
        for (int i = 0; i < tntPerLighting; i++) {
            site.spawnAt(CENTRE, WATER_BOTTOM, CENTRE, TNTPrimed.class, tnt -> {
                tnt.setVelocity(new Vector());
                tnt.setFuseTicks(FUSE_TICKS);
            });
        }
    }

    /** The explosions are counted by the {@link TntTally} the layout reports. */
    @Override
    public Map<String, Long> totals() {
        return Map.of();
    }
}
