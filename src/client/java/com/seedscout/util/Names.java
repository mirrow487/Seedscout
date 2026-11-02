package com.seedscout.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Locale;

/** Display names for structures, biomes and compass directions. */
public final class Names {
	private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

	private Names() {
	}

	/** Registry id, or "?" for unregistered (direct) holders. */
	public static String id(Holder<?> holder) {
		return holder.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
	}

	/**
	 * Structures have no translation keys in vanilla: use ours ({@code seedscout.structure.<ns>.<path>})
	 * when present, otherwise a readable version of the id ("village_plains" -> "Village Plains").
	 */
	public static Component structure(Holder<Structure> holder) {
		return holder.unwrapKey().map(k -> named("seedscout.structure", k.identifier())).orElse(Component.literal("?"));
	}

	/** Biomes have vanilla translation keys ({@code biome.minecraft.plains}); data pack biomes may not. */
	public static Component biome(Holder<Biome> holder) {
		return holder.unwrapKey().map(k -> named("biome", k.identifier())).orElse(Component.literal("?"));
	}

	private static Component named(String prefix, Identifier id) {
		String key = prefix + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
		if (Language.getInstance().has(key)) {
			return Component.translatable(key);
		}
		return Component.literal(prettify(id.getPath()));
	}

	private static String prettify(String path) {
		String last = path.substring(path.lastIndexOf('/') + 1);
		StringBuilder out = new StringBuilder();
		for (String word : last.split("_")) {
			if (!word.isEmpty()) {
				if (!out.isEmpty()) {
					out.append(' ');
				}
				out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
			}
		}
		return out.toString();
	}

	/** Compass direction from {@code from} to {@code to}: north = -Z, east = +X. */
	public static Component direction(BlockPos from, BlockPos to) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		if (dx == 0 && dz == 0) {
			return Component.translatable("seedscout.dir.here");
		}
		double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
		int index = (int) Math.floorMod(Math.round(angle / 45.0), 8);
		return Component.translatable("seedscout.dir." + DIRECTIONS[index]);
	}
}
