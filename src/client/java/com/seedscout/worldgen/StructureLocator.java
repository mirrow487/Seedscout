package com.seedscout.worldgen;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.DimensionOriginStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Finds the structures of one seed without generating chunks, in three steps:
 * <ol>
 *   <li><b>Placement</b> – the structure set's own placement code gives the candidate chunks
 *       ({@link RandomSpreadStructurePlacement#getPotentialStructureChunk}, stronghold rings, …).
 *       {@link StructurePlacement#isStructureChunk} then applies frequency and exclusion zones.</li>
 *   <li><b>Selection</b> – sets with several structures (e.g. the five village types) choose one with
 *       the same weighted random as {@code ChunkGenerator.createStructures}; if it cannot generate, the
 *       next one is tried.</li>
 *   <li><b>Validity</b> – {@link Structure#findValidGenerationPoint} (the check {@code /locate} uses):
 *       biome at the start position, terrain height from the noise, jigsaw start piece.</li>
 * </ol>
 * Results are cached per (structure set, chunk). Not thread-safe; one instance per {@link SeedWorld}.
 */
public final class StructureLocator {
	private final SeedWorld world;
	/** Per structure set: chunk -> structure that generates there (Optional.empty() = nothing). */
	private final Map<StructureSet, Long2ObjectOpenHashMap<Optional<Holder<Structure>>>> cache = new IdentityHashMap<>();
	/** Per structure: chunk -> result of the game's validity check. */
	private final Map<Structure, Long2BooleanOpenHashMap> validCache = new IdentityHashMap<>();
	/** Per structure: can any biome of this world's biome source host it at all? */
	private final Map<Structure, Boolean> possibleCache = new IdentityHashMap<>();

	public StructureLocator(SeedWorld world) {
		this.world = world;
	}

	public SeedWorld world() {
		return this.world;
	}

	/** The structure sets that can generate in this world (sets without a possible biome are already dropped by the game). */
	public List<Holder<StructureSet>> structureSets() {
		if (!this.world.settings().generateStructures()) {
			return List.of();
		}
		return this.world.structureState().possibleStructureSets();
	}

	/** Which structure of {@code set} generates with its start in {@code chunk}, if any. */
	public @Nullable Holder<Structure> structureAt(StructureSet set, ChunkPos chunk) {
		return this.cache.computeIfAbsent(set, s -> new Long2ObjectOpenHashMap<>())
			.computeIfAbsent(chunk.pack(), k -> Optional.ofNullable(computeStructureAt(set, chunk)))
			.orElse(null);
	}

	private @Nullable Holder<Structure> computeStructureAt(StructureSet set, ChunkPos chunk) {
		ChunkGeneratorStructureState state = this.world.structureState();
		if (!set.placement().isStructureChunk(state, chunk.x(), chunk.z())) {
			return null;
		}
		List<StructureSet.StructureSelectionEntry> structures = set.structures();
		if (structures.size() == 1) {
			Holder<Structure> only = structures.getFirst().structure();
			return canGenerate(only, chunk) ? only : null;
		}
		// Same weighted selection as ChunkGenerator.createStructures.
		List<StructureSet.StructureSelectionEntry> options = new ArrayList<>(structures);
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureSeed(state.getLevelSeed(), chunk.x(), chunk.z());
		int total = 0;
		for (StructureSet.StructureSelectionEntry option : options) {
			total += option.weight();
		}
		while (!options.isEmpty()) {
			int choice = random.nextInt(total);
			int index = 0;
			for (StructureSet.StructureSelectionEntry option : options) {
				choice -= option.weight();
				if (choice < 0) {
					break;
				}
				index++;
			}
			StructureSet.StructureSelectionEntry selected = options.get(index);
			if (canGenerate(selected.structure(), chunk)) {
				return selected.structure();
			}
			options.remove(index);
			total -= selected.weight();
		}
		return null;
	}

	/**
	 * False when none of the biomes this world can generate is allowed for the structure (e.g. the
	 * nether ruined portal variant in the overworld). The game's check would then fail in every chunk,
	 * so skipping it changes no result, it only saves time.
	 */
	public boolean isPossible(Holder<Structure> holder) {
		return this.possibleCache.computeIfAbsent(holder.value(), structure -> this.world.settings().generator().getBiomeSource()
			.possibleBiomes().stream().anyMatch(structure.biomes()::contains));
	}

	/** {@link #computeCanGenerate} with a per-chunk cache. */
	public boolean canGenerate(Holder<Structure> holder, ChunkPos chunk) {
		if (!isPossible(holder)) {
			return false;
		}
		return this.validCache.computeIfAbsent(holder.value(), s -> new Long2BooleanOpenHashMap())
			.computeIfAbsent(chunk.pack(), k -> computeCanGenerate(holder, chunk));
	}

	/** The game's own validity check for one structure in one chunk (biome, height, start piece). */
	private boolean computeCanGenerate(Holder<Structure> holder, ChunkPos chunk) {
		Structure structure = holder.value();
		WorldgenSettings settings = this.world.settings();
		Structure.GenerationContext context = new Structure.GenerationContext(
			settings.registries(),
			settings.generator(),
			settings.generator().getBiomeSource(),
			this.world.climateSampler(),
			this.world.randomState(),
			settings.templateManager(),
			this.world.seed(),
			chunk,
			settings.heightAccessor(),
			structure.biomes()::contains
		);
		return structure.findValidGenerationPoint(context).isPresent();
	}

	/**
	 * All structures whose locate position is within {@code radius} blocks (horizontal) of {@code center}.
	 * {@code cancelled} is polled regularly so a background search can be stopped.
	 */
	public List<StructureHit> findAll(BlockPos center, int radius, BooleanSupplier cancelled) {
		List<StructureHit> hits = new ArrayList<>();
		long radiusSqr = (long) radius * radius;
		for (Holder<StructureSet> setHolder : structureSets()) {
			StructureSet set = setHolder.value();
			for (ChunkPos chunk : candidateChunks(set.placement(), center, radius)) {
				if (cancelled.getAsBoolean()) {
					return hits;
				}
				BlockPos pos = set.placement().getLocatePos(chunk);
				if (horizontalDistSqr(pos, center) > radiusSqr) {
					continue;
				}
				Holder<Structure> structure = structureAt(set, chunk);
				if (structure != null) {
					hits.add(new StructureHit(structure, chunk, pos));
				}
			}
		}
		return hits;
	}

	/**
	 * Nearest structure out of {@code wanted} within {@code radius} blocks of {@code center}, or null.
	 * Stage 1 (placement only, pure random numbers) selects the candidate chunks in range, sorted by
	 * distance; only those get stage 2 (selection + the game's validity check). Structure sets that
	 * contain none of the wanted structures are not touched at all.
	 */
	public @Nullable StructureHit nearest(Set<Structure> wanted, BlockPos center, int radius) {
		StructureHit best = null;
		double bestDist = Double.MAX_VALUE;
		long radiusSqr = (long) radius * radius;
		for (Holder<StructureSet> setHolder : structureSets()) {
			StructureSet set = setHolder.value();
			if (set.structures().stream().noneMatch(e -> wanted.contains(e.structure().value()) && isPossible(e.structure()))) {
				continue;
			}
			List<ChunkPos> candidates = new ArrayList<>();
			for (ChunkPos chunk : candidateChunks(set.placement(), center, radius)) {
				if (horizontalDistSqr(set.placement().getLocatePos(chunk), center) <= radiusSqr) {
					candidates.add(chunk);
				}
			}
			candidates.sort(Comparator.comparingLong(c -> horizontalDistSqr(set.placement().getLocatePos(c), center)));
			for (ChunkPos chunk : candidates) {
				BlockPos pos = set.placement().getLocatePos(chunk);
				double dist = Math.sqrt(horizontalDistSqr(pos, center));
				if (dist >= bestDist) {
					break;
				}
				Holder<Structure> structure = structureAt(set, chunk);
				if (structure != null && wanted.contains(structure.value())) {
					best = new StructureHit(structure, chunk, pos);
					bestDist = dist;
					break;
				}
			}
		}
		return best;
	}

	/** Candidate start chunks of a placement around a point (one per spread region, ring positions, …). */
	private List<ChunkPos> candidateChunks(StructurePlacement placement, BlockPos center, int radius) {
		List<ChunkPos> chunks = new ArrayList<>();
		int minChunkX = SectionPos.blockToSectionCoord(center.getX() - radius);
		int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + radius);
		int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
		int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
		switch (placement) {
			case RandomSpreadStructurePlacement spread -> {
				int spacing = spread.spacing();
				for (int rx = Math.floorDiv(minChunkX, spacing); rx <= Math.floorDiv(maxChunkX, spacing); rx++) {
					for (int rz = Math.floorDiv(minChunkZ, spacing); rz <= Math.floorDiv(maxChunkZ, spacing); rz++) {
						chunks.add(spread.getPotentialStructureChunk(this.world.seed(), rx * spacing, rz * spacing));
					}
				}
			}
			case ConcentricRingsStructurePlacement rings -> {
				List<ChunkPos> positions = this.world.structureState().getRingPositionsFor(rings);
				if (positions != null) {
					chunks.addAll(positions);
				}
			}
			case DimensionOriginStructurePlacement origin -> chunks.add(this.world.structureState().getDimensionOrigin());
			default -> {
				// Unknown (modded) placement type: test every chunk of the area with the placement itself.
				// Only done for small areas, it is slow.
				if ((long) (maxChunkX - minChunkX) * (maxChunkZ - minChunkZ) <= 128 * 128) {
					for (int x = minChunkX; x <= maxChunkX; x++) {
						for (int z = minChunkZ; z <= maxChunkZ; z++) {
							chunks.add(new ChunkPos(x, z));
						}
					}
				}
			}
		}
		return chunks;
	}

	/**
	 * Nearest {@code target} structure exactly like {@code /locate structure} (same search order and
	 * stop rule as {@code ChunkGenerator.findNearestMapStructure}, which does not always return the
	 * mathematically nearest one). {@code maxSearchRadius} is in spread regions, {@code /locate} uses 100.
	 */
	public @Nullable StructureHit locate(Holder<Structure> target, BlockPos from, int maxSearchRadius) {
		if (!isPossible(target)) {
			return null;
		}
		List<StructureSet> randomSpreadSets = new ArrayList<>();
		StructureHit nearest = null;
		double nearestDistSqr = Double.MAX_VALUE;
		for (Holder<StructureSet> setHolder : structureSets()) {
			StructureSet set = setHolder.value();
			if (set.structures().stream().noneMatch(e -> e.structure().equals(target))) {
				continue;
			}
			if (set.placement() instanceof ConcentricRingsStructurePlacement rings) {
				StructureHit hit = locateInRings(set, rings, target, from);
				if (hit != null && from.distSqr(hit.pos()) < nearestDistSqr) {
					nearestDistSqr = from.distSqr(hit.pos());
					nearest = hit;
				}
			} else if (set.placement() instanceof RandomSpreadStructurePlacement) {
				randomSpreadSets.add(set);
			}
		}
		if (randomSpreadSets.isEmpty()) {
			return nearest;
		}
		int originX = SectionPos.blockToSectionCoord(from.getX());
		int originZ = SectionPos.blockToSectionCoord(from.getZ());
		for (int radius = 0; radius <= maxSearchRadius; radius++) {
			boolean foundSomething = false;
			for (StructureSet set : randomSpreadSets) {
				StructureHit hit = locateOnSquare(set, (RandomSpreadStructurePlacement) set.placement(), target, originX, originZ, radius);
				if (hit != null) {
					foundSomething = true;
					double distSqr = from.distSqr(hit.pos());
					if (distSqr < nearestDistSqr) {
						nearestDistSqr = distSqr;
						nearest = hit;
					}
				}
			}
			if (foundSomething) {
				return nearest;
			}
		}
		return nearest;
	}

	private @Nullable StructureHit locateInRings(StructureSet set, ConcentricRingsStructurePlacement rings, Holder<Structure> target, BlockPos from) {
		List<ChunkPos> positions = this.world.structureState().getRingPositionsFor(rings);
		if (positions == null) {
			return null;
		}
		StructureHit closest = null;
		double closestDistSqr = Double.MAX_VALUE;
		for (ChunkPos chunk : positions) {
			BlockPos center = new BlockPos(SectionPos.sectionToBlockCoord(chunk.x(), 8), 32, SectionPos.sectionToBlockCoord(chunk.z(), 8));
			double distSqr = center.distSqr(from);
			if (closest == null || distSqr < closestDistSqr) {
				if (generatesHere(set, target, chunk)) {
					closest = new StructureHit(target, chunk, rings.getLocatePos(chunk));
					closestDistSqr = distSqr;
				}
			}
		}
		return closest;
	}

	private @Nullable StructureHit locateOnSquare(
		StructureSet set, RandomSpreadStructurePlacement placement, Holder<Structure> target, int originX, int originZ, int radius
	) {
		int spacing = placement.spacing();
		for (int x = -radius; x <= radius; x++) {
			boolean xEdge = x == -radius || x == radius;
			for (int z = -radius; z <= radius; z++) {
				boolean zEdge = z == -radius || z == radius;
				if (xEdge || zEdge) {
					ChunkPos chunk = placement.getPotentialStructureChunk(this.world.seed(), originX + spacing * x, originZ + spacing * z);
					if (generatesHere(set, target, chunk)) {
						return new StructureHit(target, chunk, placement.getLocatePos(chunk));
					}
				}
			}
		}
		return null;
	}

	/**
	 * Does {@code target} start in this chunk? Same order as {@code /locate}: first the cheap checks of
	 * {@code StructureCheck} (frequency, can the target itself generate here), and only then the real
	 * answer including the selection between the structures of the set and exclusion zones.
	 */
	private boolean generatesHere(StructureSet set, Holder<Structure> target, ChunkPos chunk) {
		return set.placement().applyAdditionalChunkRestrictions(chunk.x(), chunk.z(), this.world.seed())
			&& canGenerate(target, chunk)
			&& target.equals(structureAt(set, chunk));
	}

	private static long horizontalDistSqr(BlockPos a, BlockPos b) {
		long dx = a.getX() - b.getX();
		long dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

}
