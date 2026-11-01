package net.kindlingmc.preview;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import org.jspecify.annotations.Nullable;

/**
 * The WorldGenLevel that vanilla features decorate one preview chunk through, in place of the WorldGenRegion of the
 * 3 x 3 chunks around it. Inside the chunk it reads and writes the preview chunk as WorldGenRegion does; the chunks
 * around are {@link Surroundings} that stand in for them. The real world is never touched; the methods that would need
 * it fail or answer as for an empty world:
 * <ul>
 * <li>{@link #getLevel()} answers null: PlacementContext reads it eagerly but only the surface rules use it (for Paper's
 * flat bedrock), so a feature that does need the server level fails with a NullPointerException.
 * <li>{@link #getMinecraftWorld()}, {@link #getChunkSource()}, {@link #getLightEngine()}, {@link #getLevelData()} and
 * {@link #getCurrentDifficultyAt} throw. The world border is a default one that belongs to no world.
 * <li>Asked for a neighbouring chunk, it hands out one empty chunk that stands for all of them; what a feature writes
 * into it directly (post-processing marks, mostly) is thrown away with it.
 * <li>Light is 0 everywhere, as WorldGenRegion answers for a chunk whose light is not ready yet; game time is 0.
 * <li>Scheduled ticks are dropped, entities are not added, sounds, particles and events are ignored.
 * </ul>
 *
 * <p>Not thread-safe: one instance serves one decoration on one thread.
 */
final class PreviewRegion implements WorldGenLevel {
    /** The positional random WorldGenRegion seeds its own random from. */
    private static final Identifier REGION_RANDOM = Identifier.withDefaultNamespace("worldgen_region_random");
    /** Never changed, so shared: it has no world, which Purpur's fluid check in LiquidBlock.updateShape reads as "tick fluids". */
    private static final WorldBorder NO_BORDER = new WorldBorder();

    /** What a preview region needs of its world; all of it is shared by the server's own chunk workers, too. */
    record World(long seed, int seaLevel, RegistryAccess registries, DimensionType dimension, FeatureFlagSet enabledFeatures,
                 RandomState randomState, PalettedContainerFactory containerFactory) {
    }

    private final World world;
    private final ProtoChunk chunk;
    private final ChunkBiomes biomes;
    private final BiomeManager biomeManager;
    private final Surroundings surroundings;
    private final RandomSource random;
    private final int chunkX;
    private final int chunkZ;
    private @Nullable EnvironmentAttributeReader environmentAttributes;
    private @Nullable ProtoChunk neighbours;
    private long subTickCount;

    PreviewRegion(World world, ProtoChunk chunk, ChunkBiomes biomes) {
        this.world = world;
        this.chunk = chunk;
        this.biomes = biomes;
        this.biomeManager = biomes.manager();
        this.surroundings = new Surroundings(chunk);
        this.random = world.randomState().getOrCreateRandomFactory(REGION_RANDOM).at(chunk.getPos().getWorldPosition());
        this.chunkX = chunk.getPos().x();
        this.chunkZ = chunk.getPos().z();
    }

    private boolean isInChunk(int blockX, int blockZ) {
        return SectionPos.blockToSectionCoord(blockX) == this.chunkX && SectionPos.blockToSectionCoord(blockZ) == this.chunkZ;
    }

    private boolean isInChunk(BlockPos pos) {
        return isInChunk(pos.getX(), pos.getZ());
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return isInChunk(pos) ? this.chunk.getBlockState(pos) : this.surroundings.blockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return isInChunk(pos) ? this.chunk.getFluidState(pos) : this.surroundings.blockState(pos).getFluidState();
    }

    @Override
    public @Nullable BlockState getBlockStateIfLoaded(BlockPos pos) {
        return isInChunk(pos) ? this.chunk.getBlockState(pos) : null;
    }

    @Override
    public @Nullable FluidState getFluidIfLoaded(BlockPos pos) {
        return isInChunk(pos) ? this.chunk.getFluidState(pos) : null;
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
        return predicate.test(getBlockState(pos));
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
        return predicate.test(getFluidState(pos));
    }

    /** The column's height plus one, as WorldGenRegion answers. */
    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        return isInChunk(x, z) ? this.chunk.getHeight(type, x & 15, z & 15) + 1 : this.surroundings.height(type, x, z);
    }

    @Override
    public boolean ensureCanWrite(BlockPos pos) {
        return isInChunk(pos) ? !this.chunk.isOutsideBuildHeight(pos.getY()) : this.surroundings.canWrite(pos);
    }

    /** WorldGenRegion.setBlock without post-processing and POI updates; a replaced block takes its block entity along. */
    @Override
    public boolean setBlock(BlockPos pos, BlockState blockState, @Block.UpdateFlags int updateFlags, int updateLimit) {
        if (!isInChunk(pos)) {
            return this.surroundings.write(pos, blockState);
        }
        if (this.chunk.isOutsideBuildHeight(pos.getY())) {
            return false;
        }
        BlockState old = this.chunk.setBlockState(pos, blockState, updateFlags);
        if (old != null && old.getBlock() != blockState.getBlock()) {
            this.chunk.removeBlockEntity(pos);
        }
        return true;
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean movedByPiston) {
        return setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropResources, @Nullable Entity breaker, int updateLimit) {
        return !getBlockState(pos).isAir() && setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL, updateLimit);
    }

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus targetStatus, boolean loadOrGenerate) {
        return hasChunk(chunkX, chunkZ) ? this.chunk : neighbours();
    }

    /** The empty chunk that stands for every neighbour, made on first use. */
    private ProtoChunk neighbours() {
        if (this.neighbours == null) {
            LevelHeightAccessor height = LevelHeightAccessor.create(this.chunk.getMinY(), this.chunk.getHeight());
            this.neighbours = new ProtoChunk(this.chunk.getPos(), UpgradeData.EMPTY, height, this.world.containerFactory(), null);
        }
        return this.neighbours;
    }

    @Override
    public @Nullable ChunkAccess getChunkIfLoadedImmediately(int x, int z) {
        return hasChunk(x, z) ? this.chunk : null;
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return chunkX == this.chunkX && chunkZ == this.chunkZ;
    }

    @Override
    public ChunkAccess moonrise$syncLoadNonFull(int chunkX, int chunkZ, ChunkStatus status) {
        throw new UnsupportedOperationException("a preview region loads no chunks");
    }

    /**
     * The block entity of a block a feature placed, made on first request as WorldGenRegion makes it from its
     * placeholder: a beehive decorator fills its nest with bees, drawing from the feature's random. In the chunk it is
     * the chunk's own; in a neighbour it is the throwaway one of {@link Surroundings}, so that the draws happen there too.
     */
    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        if (!isInChunk(pos)) {
            return this.surroundings.blockEntity(pos);
        }
        BlockEntity existing = this.chunk.getBlockEntity(pos);
        if (existing != null) {
            return existing;
        }
        BlockState state = this.chunk.getBlockState(pos);
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
            return null;
        }
        BlockEntity created = entityBlock.newBlockEntity(pos, state);
        if (created != null) {
            this.chunk.setBlockEntity(created);
        }
        return created;
    }

    @Override
    public BiomeManager getBiomeManager() {
        return this.biomeManager;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        return this.biomes.getNoiseBiome(quartX, quartY, quartZ);
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int quartX, int quartY, int quartZ) {
        return this.biomes.getNoiseBiome(quartX, quartY, quartZ);
    }

    /** As WorldGenRegion builds it: the dimension's and the biomes' attributes, no timelines and no weather. Built on first use. */
    @Override
    public EnvironmentAttributeReader environmentAttributes() {
        if (this.environmentAttributes == null) {
            this.environmentAttributes = EnvironmentAttributeSystem.builder().addStaticLayers(this).build();
        }
        return this.environmentAttributes;
    }

    @Override
    public long getSeed() {
        return this.world.seed();
    }

    @Override
    public RandomSource getRandom() {
        return this.random;
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.world.registries();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.world.enabledFeatures();
    }

    @Override
    public DimensionType dimensionType() {
        return this.world.dimension();
    }

    @Override
    public int getSeaLevel() {
        return this.world.seaLevel();
    }

    @Override
    public int getMinY() {
        return this.chunk.getMinY();
    }

    @Override
    public int getHeight() {
        return this.chunk.getHeight();
    }

    @Override
    public int getSkyDarken() {
        return 0;
    }

    @Override
    public int getBrightness(LightLayer lightLayer, BlockPos pos) {
        return 0;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return 0;
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public long getGameTime() {
        return 0;
    }

    @Override
    public long nextSubTickCount() {
        return this.subTickCount++;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public List<Entity> getEntities(@Nullable Entity except, AABB bb, @Nullable Predicate<? super Entity> selector) {
        return List.of();
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB bb, Predicate<? super T> selector) {
        return List.of();
    }

    @Override
    public List<Entity> moonrise$getHardCollidingEntities(Entity entity, AABB box, Predicate<? super Entity> predicate) {
        return List.of();
    }

    @Override
    public List<Player> players() {
        return List.of();
    }

    @Override
    public void playSound(@Nullable Entity except, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
    }

    @Override
    public void addParticle(ParticleOptions particle, double x, double y, double z, double xd, double yd, double zd) {
    }

    @Override
    public void levelEvent(@Nullable Entity source, @LevelEvent.Value int type, BlockPos pos, int data) {
    }

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context) {
    }

    @Override
    public @Nullable MinecraftServer getServer() {
        return null;
    }

    /** Null on purpose, see the class comment: a preview must never reach the real world. */
    @Override
    public ServerLevel getLevel() {
        return null;
    }

    @Override
    public ServerLevel getMinecraftWorld() {
        throw noRealWorld();
    }

    @Override
    public ChunkSource getChunkSource() {
        throw noRealWorld();
    }

    @Override
    public LevelLightEngine getLightEngine() {
        throw noRealWorld();
    }

    @Override
    public LevelData getLevelData() {
        throw noRealWorld();
    }

    @Override
    public WorldBorder getWorldBorder() {
        return NO_BORDER;
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        throw noRealWorld();
    }

    private UnsupportedOperationException noRealWorld() {
        return new UnsupportedOperationException("a preview of chunk " + this.chunk.getPos() + " has no real world");
    }
}
