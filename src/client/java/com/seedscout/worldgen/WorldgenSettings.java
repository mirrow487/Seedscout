package com.seedscout.worldgen;

import com.seedscout.SeedscoutClient;
import com.seedscout.mixin.CreateWorldScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.validation.DirectoryValidator;
import com.mojang.datafixers.DataFixer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import java.util.stream.Stream;

/**
 * Immutable snapshot of everything the Create World screen would use to generate the overworld:
 * <ul>
 *   <li>the registries loaded with the selected data packs (structures, structure sets, biomes, noise…),</li>
 *   <li>the overworld {@link ChunkGenerator} of the selected world type (normal, large biomes, amplified…),</li>
 *   <li>the "generate structures" switch,</li>
 *   <li>a {@link StructureTemplateManager} reading the templates of the selected data packs
 *       (jigsaw structures such as villages need the size of their start piece).</li>
 * </ul>
 * One instance is shared by all search threads; per-seed state lives in {@link SeedWorld}.
 * Must be {@link #close() closed} to release the data pack files.
 */
public final class WorldgenSettings implements AutoCloseable {
	private final RegistryAccess.Frozen registries;
	private final ChunkGenerator generator;
	private final LevelHeightAccessor heightAccessor;
	private final boolean generateStructures;
	private final FeatureFlagSet enabledFeatures;
	private final List<String> enabledPacks;
	private final @Nullable Path dataPackDir;
	private final DirectoryValidator directoryValidator;
	private final DataFixer dataFixer;
	private @Nullable CloseableResourceManager resourceManager;
	private @Nullable StructureTemplateManager templateManager;
	private @Nullable Set<Biome> caveBiomes;

	public WorldgenSettings(
		RegistryAccess.Frozen registries,
		ChunkGenerator generator,
		DimensionType dimensionType,
		boolean generateStructures,
		FeatureFlagSet enabledFeatures,
		List<String> enabledPacks,
		@Nullable Path dataPackDir,
		DirectoryValidator directoryValidator,
		DataFixer dataFixer
	) {
		this.registries = registries;
		this.generator = generator;
		this.heightAccessor = LevelHeightAccessor.create(dimensionType.minY(), dimensionType.height());
		this.generateStructures = generateStructures;
		this.enabledFeatures = enabledFeatures;
		this.enabledPacks = List.copyOf(enabledPacks);
		this.dataPackDir = dataPackDir;
		this.directoryValidator = directoryValidator;
		this.dataFixer = dataFixer;
	}

	/** Reads the current settings of a Create World screen. Must be called on the render thread. */
	public static WorldgenSettings fromScreen(CreateWorldScreen screen) {
		WorldCreationUiState uiState = screen.getUiState();
		WorldCreationContext context = uiState.getSettings();
		LevelStem overworld = context.selectedDimensions().get(LevelStem.OVERWORLD)
			.orElseThrow(() -> new IllegalStateException("The selected world type has no overworld"));
		return new WorldgenSettings(
			context.worldgenLoadContext(),
			overworld.generator(),
			overworld.type().value(),
			uiState.isGenerateStructures(),
			context.dataConfiguration().enabledFeatures(),
			context.dataConfiguration().dataPacks().getEnabled(),
			((CreateWorldScreenAccessor) screen).seedscout$getTempDataPackDir(),
			Minecraft.getInstance().directoryValidator(),
			Minecraft.getInstance().getFixerUpper()
		);
	}

	/** Underground-only biomes (lush caves, dripstone caves, deep dark), resolved once. */
	public synchronized Set<Biome> caveBiomes() {
		if (this.caveBiomes == null) {
			Set<Biome> set = Collections.newSetFromMap(new IdentityHashMap<>());
			var biomes = this.registries.lookupOrThrow(Registries.BIOME);
			for (String id : List.of("minecraft:lush_caves", "minecraft:dripstone_caves", "minecraft:deep_dark")) {
				biomes.get(ResourceKey.create(Registries.BIOME, Identifier.parse(id))).ifPresent(h -> set.add(h.value()));
			}
			this.caveBiomes = set;
		}
		return this.caveBiomes;
	}

	public RegistryAccess.Frozen registries() {
		return this.registries;
	}

	public ChunkGenerator generator() {
		return this.generator;
	}

	public LevelHeightAccessor heightAccessor() {
		return this.heightAccessor;
	}

	public boolean generateStructures() {
		return this.generateStructures;
	}

	/**
	 * Structure template manager for the selected data packs, created on first use.
	 * Mirrors {@code MinecraftServer}: same data packs, same data fixer, same feature-filtered block lookup.
	 */
	public synchronized StructureTemplateManager templateManager() {
		if (this.templateManager == null) {
			this.templateManager = createTemplateManager();
		}
		return this.templateManager;
	}

	private StructureTemplateManager createTemplateManager() {
		// Same pack sources the Create World screen uses: built-in packs (+ the packs the player added).
		PackRepository packs = this.dataPackDir != null
			? ServerPacksSource.createPackRepository(this.dataPackDir, this.directoryValidator)
			: new PackRepository(new RepositorySource[]{new ServerPacksSource(this.directoryValidator)});
		packs.reload();
		packs.setSelected(this.enabledPacks);
		this.resourceManager = new MultiPackResourceManager(PackType.SERVER_DATA, packs.openAllSelected());

		HolderGetter<Block> blocks = this.registries.lookupOrThrow(Registries.BLOCK).filterFeatures(this.enabledFeatures);
		// The constructor wants a world folder for "generated" templates (structure blocks). A new world has
		// none, so an empty temporary folder is used and deleted right away; only its path is remembered.
		Path temp = null;
		try {
			temp = Files.createTempDirectory("seedscout");
			try (LevelStorageSource.LevelStorageAccess access = LevelStorageSource.createDefault(temp).createAccess("templates")) {
				return new StructureTemplateManager(this.resourceManager, access, this.dataFixer, blocks);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not create the structure template manager", e);
		} finally {
			deleteQuietly(temp);
		}
	}

	private static void deleteQuietly(@Nullable Path dir) {
		if (dir == null) {
			return;
		}
		try (Stream<Path> files = Files.walk(dir)) {
			files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		} catch (IOException e) {
			SeedscoutClient.LOGGER.debug("Could not delete {}", dir, e);
		}
	}

	@Override
	public synchronized void close() {
		if (this.resourceManager != null) {
			this.resourceManager.close();
			this.resourceManager = null;
		}
		this.templateManager = null;
	}
}
