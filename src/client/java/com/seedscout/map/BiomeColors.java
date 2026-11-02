package com.seedscout.map;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.Map;

/**
 * Map colours for biomes (in the spirit of the classic Amidst / Chunkbase palettes, so the map reads
 * the way players expect). Biomes without an entry (data packs, mods) get a stable colour derived
 * from their id.
 */
public final class BiomeColors {
	private static final Map<String, Integer> COLORS = new HashMap<>();

	static {
		put(0x000070, "ocean");
		put(0x000030, "deep_ocean");
		put(0x0000AC, "warm_ocean");
		put(0x000090, "lukewarm_ocean");
		put(0x000040, "deep_lukewarm_ocean");
		put(0x202070, "cold_ocean");
		put(0x202038, "deep_cold_ocean");
		put(0x7070D6, "frozen_ocean");
		put(0x404090, "deep_frozen_ocean");
		put(0x0000FF, "river");
		put(0xA0A0FF, "frozen_river");
		put(0xFADE55, "beach");
		put(0xFAF0C0, "snowy_beach");
		put(0xA2A284, "stony_shore");
		put(0x8DB360, "plains");
		put(0xB5DB88, "sunflower_plains");
		put(0xF0F8FF, "snowy_plains");
		put(0xB4DCDC, "ice_spikes");
		put(0xFA9418, "desert");
		put(0x07B26E, "swamp");
		put(0x2CCC8E, "mangrove_swamp");
		put(0x056621, "forest");
		put(0x2D8E49, "flower_forest");
		put(0x307444, "birch_forest");
		put(0x40511A, "dark_forest");
		put(0x589C6C, "old_growth_birch_forest");
		put(0x596651, "old_growth_pine_taiga");
		put(0x818E79, "old_growth_spruce_taiga");
		put(0x0B6659, "taiga");
		put(0x31554A, "snowy_taiga");
		put(0xBDB25F, "savanna");
		put(0xA79D64, "savanna_plateau");
		put(0x606060, "windswept_hills");
		put(0x888888, "windswept_gravelly_hills");
		put(0x507050, "windswept_forest");
		put(0xE5DA87, "windswept_savanna");
		put(0x537B09, "jungle");
		put(0x628B17, "sparse_jungle");
		put(0x768E14, "bamboo_jungle");
		put(0xD94515, "badlands");
		put(0xFF6D3D, "eroded_badlands");
		put(0xB09765, "wooded_badlands");
		put(0x60A445, "meadow");
		put(0xE8A5C3, "cherry_grove");
		put(0x47726C, "grove");
		put(0xC4C4C4, "snowy_slopes");
		put(0xA0A0B4, "frozen_peaks");
		put(0xDCDCC8, "jagged_peaks");
		put(0x7B8F74, "stony_peaks");
		put(0xFF00FF, "mushroom_fields");
		put(0x7D6E5C, "dripstone_caves");
		put(0x3B7D2E, "lush_caves");
		put(0x0F2A2A, "deep_dark");
		put(0xBFC4BF, "pale_garden");
		put(0x3F8F3A, "dappled_forest");
	}

	private BiomeColors() {
	}

	private static void put(int rgb, String vanillaPath) {
		COLORS.put("minecraft:" + vanillaPath, 0xFF000000 | rgb);
	}

	/** Opaque ARGB colour of a biome. */
	public static int argb(Holder<Biome> biome) {
		String id = biome.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
		Integer color = COLORS.get(id);
		if (color != null) {
			return color;
		}
		// Unknown biome: stable, medium-bright colour from the id hash.
		int h = id.hashCode();
		int r = 60 + ((h >> 16) & 0x7F);
		int g = 60 + ((h >> 8) & 0x7F);
		int b = 60 + (h & 0x7F);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
