package com.seedscout.map;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 8×8 map icons. Where vanilla has an explorer-map decoration for a structure, the game's own texture is
 * used (read from the game, so resource packs apply); the others are Seedscout's own icons.
 * Data pack / mod structures are matched by name (e.g. any "*village*" gets a village icon).
 */
public final class StructureIcons {
	private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();
	public static final Identifier SPAWN = own("spawn");

	private StructureIcons() {
	}

	public static Identifier icon(Holder<Structure> structure) {
		String path = structure.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		return CACHE.computeIfAbsent(path, StructureIcons::resolve);
	}

	private static Identifier resolve(String path) {
		if (path.contains("village")) {
			for (String type : new String[]{"desert", "savanna", "snowy", "taiga"}) {
				if (path.contains(type)) {
					return vanilla(type + "_village");
				}
			}
			return vanilla("plains_village");
		}
		if (path.contains("mansion")) return vanilla("woodland_mansion");
		if (path.contains("monument")) return vanilla("ocean_monument");
		if (path.contains("jungle_pyramid") || path.contains("jungle_temple")) return vanilla("jungle_temple");
		if (path.contains("desert_pyramid")) return vanilla("desert_pyramid");
		if (path.contains("swamp_hut")) return vanilla("swamp_hut");
		if (path.contains("buried_treasure")) return vanilla("red_x");
		if (path.contains("mineshaft")) return vanilla("mineshaft");
		if (path.contains("trial_chambers")) return vanilla("trial_chambers");
		if (path.contains("ancient_city")) return vanilla("ancient_city");
		if (path.contains("abandoned_camp")) return vanilla("abandoned_camp");
		if (path.contains("ocean_ruin")) return vanilla("warm_ocean_ruins");
		if (path.contains("shipwreck")) return own("shipwreck");
		if (path.contains("ruined_portal")) return own("ruined_portal");
		if (path.contains("outpost")) return own("pillager_outpost");
		if (path.contains("stronghold")) return own("stronghold");
		if (path.contains("igloo")) return own("igloo");
		if (path.contains("trail_ruins")) return own("trail_ruins");
		return own("generic");
	}

	private static Identifier vanilla(String name) {
		return Identifier.withDefaultNamespace("textures/map/decorations/" + name + ".png");
	}

	private static Identifier own(String name) {
		return Identifier.fromNamespaceAndPath("seedscout", "textures/map/" + name + ".png");
	}
}
