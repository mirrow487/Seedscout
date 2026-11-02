package com.seedscout.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * The world generation state of ONE seed, built exactly like the server does in {@code ChunkMap}:
 * a {@link RandomState} (all noises), the {@link ChunkGeneratorStructureState} (structure placement,
 * stronghold rings) and a biome resolver. Nothing here creates chunks.
 * <p>
 * Not thread-safe (the samplers use caches): use one instance per thread.
 */
public final class SeedWorld {
	private final WorldgenSettings settings;
	private final long seed;
	private final RandomState randomState;
	private final ChunkGeneratorStructureState structureState;
	private final Climate.Sampler climateSampler;
	private final BiomeResolver biomeResolver;
	private ChunkPos spawnChunk;
	private BlockPos spawnPos;

	public SeedWorld(WorldgenSettings settings, long seed) {
		this.settings = settings;
		this.seed = seed;
		ChunkGenerator generator = settings.generator();
		var registries = settings.registries();
		// Same as ChunkMap's constructor.
		if (generator instanceof NoiseBasedChunkGenerator noise) {
			this.randomState = RandomState.create(registries.lookupOrThrow(Registries.NOISE), seed, noise.generatorSettings().value());
		} else {
			this.randomState = RandomState.create(
				registries.lookupOrThrow(Registries.NOISE), seed, false, Blocks.STONE.defaultBlockState(), 63, NoiseRouterData.none()
			);
		}
		this.structureState = generator.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), this.randomState, seed);
		// Same as ChunkGenerator.createStructures / StructureCheck.
		this.climateSampler = this.randomState.createClimateSampler(SamplerContext.builder().enableCaches().build());
		this.biomeResolver = generator.getBiomeSource().createResolver(this.climateSampler);
	}

	public WorldgenSettings settings() {
		return this.settings;
	}

	public long seed() {
		return this.seed;
	}

	public RandomState randomState() {
		return this.randomState;
	}

	public ChunkGeneratorStructureState structureState() {
		return this.structureState;
	}

	public Climate.Sampler climateSampler() {
		return this.climateSampler;
	}

	/**
	 * The chunk the world spawn is searched in: {@code ChunkGenerator.getOrigin}, exactly like
	 * {@code MinecraftServer.setInitialSpawn} (climate based spawn finder for noise worlds).
	 */
	public ChunkPos spawnChunk() {
		if (this.spawnChunk == null) {
			this.spawnChunk = this.settings.generator().getOrigin(this.randomState);
		}
		return this.spawnChunk;
	}

	/**
	 * World spawn position. The server then picks the first column of this chunk (from its north-west
	 * corner) whose top block is solid and dry; that needs finished chunks (trees, water), so here the
	 * north-west corner is used with the terrain height. It is at most a few blocks off in almost all
	 * worlds, and players spawn randomly within the spawn radius around it anyway.
	 */
	public BlockPos spawnPos() {
		if (this.spawnPos == null) {
			ChunkPos chunk = spawnChunk();
			int x = chunk.getMinBlockX();
			int z = chunk.getMinBlockZ();
			this.spawnPos = new BlockPos(x, surfaceHeight(x, z), z);
		}
		return this.spawnPos;
	}

	/** Terrain height (first air block above ground or water) from the noise, without generating the chunk. */
	public int surfaceHeight(int blockX, int blockZ) {
		return this.settings.generator().getBaseHeight(blockX, blockZ, Heightmap.Types.WORLD_SURFACE_WG, this.settings.heightAccessor(), this.randomState);
	}

	/** Biome at a block position (biomes are stored per 4×4×4 "quart"). */
	public Holder<Biome> biome(int blockX, int blockY, int blockZ) {
		return this.biomeResolver.getNoiseBiome(QuartPos.fromBlock(blockX), QuartPos.fromBlock(blockY), QuartPos.fromBlock(blockZ));
	}

	/** Biome you would see standing on the surface at this column. */
	public Holder<Biome> surfaceBiome(int blockX, int blockZ) {
		return biome(blockX, surfaceHeight(blockX, blockZ) - 1, blockZ);
	}
}
