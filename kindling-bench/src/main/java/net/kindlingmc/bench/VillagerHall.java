package net.kindlingmc.bench;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;

/**
 * Twelve unemployed villagers in a walled chunk with exactly twelve beds and twelve job sites, so that in the end every
 * villager can have a bed and a job site of its own and none can have one that another holds. Two beds in a row lie
 * foot to head along x; the villagers start in the middle row between beds and job sites. The wall keeps the hall apart
 * from its neighbours, so supply and demand match inside each hall.
 */
final class VillagerHall implements Contraption {

    private static final int WIDTH = ChunkSite.SIZE;
    private static final int WALL_HEIGHT = 3;
    private static final int FIRST_X = 2;
    private static final int LAST_X = 13;
    private static final int BED_STEP = 2;
    private static final int[] BED_ROWS = {2, 5};
    private static final int JOB_SITE_ROW = 12;
    private static final int VILLAGER_ROW = 8;
    private static final int LAYER = 1;

    private final ChunkSite site;
    private final List<Villager> villagers = new ArrayList<>();

    VillagerHall(ChunkSite site) {
        this.site = site;
    }

    @Override
    public void build() {
        buildWall();
        for (int z : BED_ROWS) {
            buildBedRow(z);
        }
        for (int x = FIRST_X; x <= LAST_X; x++) {
            site.place(x, LAYER, JOB_SITE_ROW, Material.COMPOSTER);
            villagers.add(site.spawn(x, LAYER, VILLAGER_ROW, Villager.class));
        }
    }

    private void buildWall() {
        for (int y = LAYER; y < LAYER + WALL_HEIGHT; y++) {
            for (int i = 0; i < WIDTH; i++) {
                site.place(i, y, 0, Material.STONE_BRICKS);
                site.place(i, y, WIDTH - 1, Material.STONE_BRICKS);
                site.place(0, y, i, Material.STONE_BRICKS);
                site.place(WIDTH - 1, y, i, Material.STONE_BRICKS);
            }
        }
    }

    private void buildBedRow(int z) {
        for (int x = FIRST_X; x < LAST_X; x += BED_STEP) {
            site.place(x, LAYER, z, BlockStates.bed(Bed.Part.FOOT, BlockFace.EAST));
            site.place(x + 1, LAYER, z, BlockStates.bed(Bed.Part.HEAD, BlockFace.EAST));
        }
    }

    /**
     * How many villagers hold a bed and a job site, and for how many of those places more than one villager holds the
     * same one: in vanilla a place is taken in the very tick it is chosen, so none ever is.
     */
    @Override
    public Map<String, Long> totals() {
        Map<Location, Integer> beds = claimsPerPlace(MemoryKey.HOME);
        Map<Location, Integer> jobSites = claimsPerPlace(MemoryKey.JOB_SITE);
        return Map.of("bed_claims", claimsIn(beds), "job_site_claims", claimsIn(jobSites),
                "shared_beds", sharedPlacesIn(beds), "shared_job_sites", sharedPlacesIn(jobSites));
    }

    private Map<Location, Integer> claimsPerPlace(MemoryKey<Location> memory) {
        Map<Location, Integer> claims = new HashMap<>();
        for (Villager villager : villagers) {
            Location place = villager.getMemory(memory);
            if (place != null) {
                claims.merge(place, 1, Integer::sum);
            }
        }
        return claims;
    }

    private static long claimsIn(Map<Location, Integer> claimsPerPlace) {
        return claimsPerPlace.values().stream().mapToLong(Integer::longValue).sum();
    }

    private static long sharedPlacesIn(Map<Location, Integer> claimsPerPlace) {
        return claimsPerPlace.values().stream().filter(claims -> claims > 1).count();
    }
}
