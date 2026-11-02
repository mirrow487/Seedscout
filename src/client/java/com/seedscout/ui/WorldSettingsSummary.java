package com.seedscout.ui;

import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Human readable summary of the world settings that the seed search will use:
 * world type, "generate structures", enabled data packs and how many structures / biomes
 * the registries contain (vanilla + data packs + mods).
 */
public final class WorldSettingsSummary {
	private WorldSettingsSummary() {
	}

	public static List<Component> lines(WorldCreationUiState uiState) {
		WorldCreationContext settings = uiState.getSettings();
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("seedscout.summary.world_type", uiState.getWorldType().describePreset()));
		lines.add(Component.translatable("seedscout.summary.structures",
			Component.translatable(uiState.isGenerateStructures() ? "options.on" : "options.off")));
		String seed = uiState.getSeed();
		lines.add(Component.translatable("seedscout.summary.seed",
			seed.isBlank() ? Component.translatable("seedscout.summary.seed.empty") : Component.literal(seed)));
		lines.add(Component.translatable("seedscout.summary.datapacks",
			String.join(", ", settings.dataConfiguration().dataPacks().getEnabled())));
		int structures = settings.worldgenLoadContext().lookupOrThrow(Registries.STRUCTURE).size();
		int biomes = settings.worldgenLoadContext().lookupOrThrow(Registries.BIOME).size();
		lines.add(Component.translatable("seedscout.summary.registry", structures, biomes));
		return lines;
	}
}
