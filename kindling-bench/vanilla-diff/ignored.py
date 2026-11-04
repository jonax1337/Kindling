"""What the comparer deliberately does not compare, in one place.

The comparer looks at block states, biomes, block entities, entities and scheduled ticks only. Everything else in a
chunk file is outside the diff by construction (see ONLY_COMPARED_FOR_CHUNKS). The fields below are dropped from the
parts that are compared, because they hold run-specific or server-specific values and not game state.
"""

VOLATILE_KEYS = frozenset({
    "UUID",        # entity ids are random per run
    "keepPacked",  # a marker of how a block entity was written, not game state
    "Leash",       # references a UUID
    "WorldUUIDLeast", "WorldUUIDMost",  # CraftBukkit stamps the world's random UUID on every entity it saves
})
VOLATILE_KEY_PREFIXES = ("Paper.", "Spigot.", "Bukkit", "Purpur.", "Leaf.", "Kindling.")

# (what, why), printed by --list-ignored and at the top of every report
IGNORED_FIELDS = (
    ("chunk: LastUpdate, InhabitedTime", "wall-clock and player-presence counters"),
    ("chunk: DataVersion, Status of chunks that are not full", "format and generation bookkeeping"),
    ("chunk: Heightmaps, structures, CarvingMasks, PostProcessing, UpgradeData, blending_data",
     "derived or generation-time data"),
    ("chunk: isLightOn, sections' BlockLight and SkyLight",
     "light may legitimately be recomputed; compare it only after adding it deliberately"),
    ("block entities and entities: UUID, keepPacked, Leash, WorldUUIDLeast/Most", "random per run or a marker, not state"),
    ("block entities and entities: keys starting with Paper., Spigot., Bukkit, Purpur., Leaf., Kindling.",
     "tags that only Paper, Spigot, Bukkit, Purpur, Leaf or Kindling write (Paper.FailedSpawnAttempts, Spigot.ticksLived,"
     " Purpur.ShouldBurnInDay, ...)"),
    ("region files: header timestamps, sector layout, compression", "storage details; the decoded chunk is compared"),
    ("everything else in a chunk file: poi, player data, level.dat, scoreboard, raids",
     "not read at all"),
)

ONLY_COMPARED_FOR_CHUNKS = (
    "block states per position", "biomes per quart cell", "block entities", "entities",
    "scheduled block and fluid ticks",
)


def is_volatile_key(key: str) -> bool:
    return key in VOLATILE_KEYS or key.startswith(VOLATILE_KEY_PREFIXES)
