package com.seedscout.gametest;

import com.mojang.datafixers.util.Pair;
import com.seedscout.util.Names;
import com.seedscout.worldgen.BiomeLocator;
import com.seedscout.worldgen.SeedWorld;
import com.seedscout.worldgen.StructureHit;
import com.seedscout.worldgen.StructureLocator;
import com.seedscout.worldgen.WorldgenSettings;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.seedscout.gametest.TestUtil.check;

/**
 * Stage 2 verification: Seedscout's structure / biome / spawn results for a seed, computed from the
 * Create World screen settings WITHOUT generating chunks, must be identical to what the real world
 * (integrated server, {@code /locate} code path) reports for the same seed and world type.
 */
public class LocateParityClientGameTest implements FabricClientGameTest {
	/** /locate structure searches 100 spread regions. */
	private static final int LOCATE_RADIUS = 100;
	private static final List<String> BIOMES_TO_LOCATE = List.of(
		"minecraft:plains", "minecraft:desert", "minecraft:cherry_grove", "minecraft:mushroom_fields", "minecraft:lush_caves", "minecraft:jagged_peaks"
	);

	@Override
	public void runTest(ClientGameTestContext ctx) {
		String only = System.getenv("SEEDSCOUT_ONLY");
		if (only != null && !only.equals(getClass().getSimpleName())) {
			return;
		}
		compare(ctx, "8675309", WorldPresets.NORMAL);
		if (System.getenv("SEEDSCOUT_QUICK") == null) {
			compare(ctx, "-4172144997902289642", WorldPresets.LARGE_BIOMES);
		}
	}

	/** What Seedscout computes. */
	private record Expected(
		String seedText, ChunkPos spawnChunk, BlockPos spawnPos,
		Map<String, BlockPos> structures, Map<Long, String> biomeSamples, Map<String, BlockPos> biomeLocate
	) {
	}

	private void compare(ClientGameTestContext ctx, String seedText, ResourceKey<WorldPreset> preset) {
		Expected expected = computeWithSeedscout(ctx, seedText, preset);

		try (TestSingleplayerContext sp = ctx.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(ui -> {
				ui.setSeed(seedText);
				ui.setWorldType(worldType(ui, preset));
			})
			.create()) {
			List<String> mismatches = sp.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				List<String> problems = new ArrayList<>();

				// --- spawn
				BlockPos realSpawn = level.getRespawnData().pos();
				if (!ChunkPos.containing(realSpawn).equals(expected.spawnChunk())) {
					problems.add("spawn chunk " + expected.spawnChunk() + " vs real spawn " + realSpawn);
				}
				System.out.printf("[Seedscout test] %s %s: spawn real=%s seedscout=%s (%.1f blocks apart)%n", seedText, preset.identifier(),
					realSpawn.toShortString(), expected.spawnPos().toShortString(), Math.sqrt(realSpawn.distSqr(expected.spawnPos())));

				// --- structures, exactly the /locate code path (findNearestMapStructure)
				Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
				for (Map.Entry<String, BlockPos> e : expected.structures().entrySet()) {
					Holder<Structure> holder = structures.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.parse(e.getKey())));
					Pair<BlockPos, Holder<Structure>> real = level.getChunkSource().getGenerator()
						.findNearestMapStructure(level, HolderSet.direct(holder), expected.spawnPos(), LOCATE_RADIUS, false);
					BlockPos realPos = real == null ? null : real.getFirst();
					String line = String.format("%-32s seedscout=%-18s /locate=%s", e.getKey(), str(e.getValue()), str(realPos));
					System.out.println("[Seedscout test]   " + line);
					if (!same(e.getValue(), realPos)) {
						problems.add(line);
					}
				}

				// --- biomes at random points (all heights)
				int biomeDiffs = 0;
				for (Map.Entry<Long, String> e : expected.biomeSamples().entrySet()) {
					BlockPos p = BlockPos.of(e.getKey());
					String real = Names.id(level.getUncachedNoiseBiome(QuartPos.fromBlock(p.getX()), QuartPos.fromBlock(p.getY()), QuartPos.fromBlock(p.getZ())));
					if (!real.equals(e.getValue())) {
						biomeDiffs++;
						if (biomeDiffs <= 5) {
							problems.add("biome at " + p.toShortString() + ": seedscout=" + e.getValue() + " real=" + real);
						}
					}
				}
				System.out.printf("[Seedscout test]   biome samples: %d, different: %d%n", expected.biomeSamples().size(), biomeDiffs);

				// --- /locate biome
				for (Map.Entry<String, BlockPos> e : expected.biomeLocate().entrySet()) {
					Pair<BlockPos, Holder<Biome>> real = level.findClosestBiome3d(
						b -> Names.id(b).equals(e.getKey()), expected.spawnPos(), 6400, 32, 64);
					BlockPos realPos = real == null ? null : real.getFirst();
					String line = String.format("%-32s seedscout=%-18s /locate=%s", e.getKey(), str(e.getValue()), str(realPos));
					System.out.println("[Seedscout test]   " + line);
					if (!same(e.getValue(), realPos)) {
						problems.add(line);
					}
				}
				return problems;
			});
			check(mismatches.isEmpty(), seedText + " " + preset.identifier() + ": Seedscout == real world " + (mismatches.isEmpty() ? "" : mismatches));
		}
	}

	private Expected computeWithSeedscout(ClientGameTestContext ctx, String seedText, ResourceKey<WorldPreset> preset) {
		ctx.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen())));
		ctx.waitForScreen(CreateWorldScreen.class);
		WorldgenSettings settings = ctx.computeOnClient(mc -> {
			CreateWorldScreen screen = (CreateWorldScreen) mc.gui.screen();
			screen.getUiState().setSeed(seedText);
			screen.getUiState().setWorldType(worldType(screen.getUiState(), preset));
			return WorldgenSettings.fromScreen(screen);
		});
		ctx.runOnClient(mc -> mc.gui.setScreen(new TitleScreen()));

		try (settings) {
			long start = System.nanoTime();
			SeedWorld world = new SeedWorld(settings, Long.parseLong(seedText));
			StructureLocator locator = new StructureLocator(world);
			BlockPos spawn = world.spawnPos();

			Map<String, BlockPos> structures = new LinkedHashMap<>();
			for (Holder<StructureSet> set : locator.structureSets()) {
				for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
					long t0 = System.nanoTime();
					StructureHit hit = locator.locate(entry.structure(), spawn, LOCATE_RADIUS);
					System.out.printf("[Seedscout test]   timing %-40s %6d ms%n", Names.id(entry.structure()), (System.nanoTime() - t0) / 1_000_000);
					structures.put(Names.id(entry.structure()), hit == null ? null : hit.pos());
				}
			}
			long structuresDone = System.nanoTime();

			Map<Long, String> biomeSamples = new LinkedHashMap<>();
			Random random = new Random(42);
			int minY = settings.heightAccessor().getMinY();
			int height = settings.heightAccessor().getHeight();
			for (int i = 0; i < 2000; i++) {
				BlockPos p = new BlockPos(random.nextInt(20001) - 10000, minY + random.nextInt(height), random.nextInt(20001) - 10000);
				biomeSamples.put(p.asLong(), Names.id(world.biome(p.getX(), p.getY(), p.getZ())));
			}

			Map<String, BlockPos> biomeLocate = new LinkedHashMap<>();
			for (String id : BIOMES_TO_LOCATE) {
				Pair<BlockPos, Holder<Biome>> found = BiomeLocator.locate(world, spawn, 6400, 32, 64, b -> Names.id(b).equals(id));
				biomeLocate.put(id, found == null ? null : found.getFirst());
			}
			long end = System.nanoTime();
			System.out.printf("[Seedscout test] %s %s: seedscout took %d ms for %d structures, %d ms for biomes%n", seedText, preset.identifier(),
				(structuresDone - start) / 1_000_000, structures.size(), (end - structuresDone) / 1_000_000);
			return new Expected(seedText, world.spawnChunk(), spawn, structures, biomeSamples, biomeLocate);
		}
	}

	private static WorldCreationUiState.WorldTypeEntry worldType(WorldCreationUiState ui, ResourceKey<WorldPreset> preset) {
		return ui.getNormalPresetList().stream()
			.filter(t -> t.preset() != null && t.preset().is(preset))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No world type " + preset));
	}

	private static boolean same(BlockPos a, BlockPos b) {
		if (a == null || b == null) {
			return a == b;
		}
		return a.getX() == b.getX() && a.getZ() == b.getZ();
	}

	private static String str(BlockPos p) {
		return p == null ? "-" : p.getX() + " " + p.getY() + " " + p.getZ();
	}
}
