package com.seedscout.config;

import net.minecraft.network.chat.Component;

/**
 * The three modes offered next to the seed box on the Create World screen.
 * <p>
 * {@link #RANDOM} is the vanilla behaviour: the mod does not search, compute or change anything.
 */
public enum SearchMode {
	/** Vanilla: no search, nothing is touched. */
	RANDOM("random"),
	/** Good loot structures (village, shipwreck, ruined portal, …) close to spawn. */
	SPEEDRUN("speedrun"),
	/** Nice building terrain: mountains, water, forests, flat areas, biome borders. */
	SURVIVAL("survival");

	private final String id;

	SearchMode(String id) {
		this.id = id;
	}

	/** Stable id used in the JSON config. */
	public String id() {
		return this.id;
	}

	/** Translated short name, e.g. "Speedrun/OP". */
	public Component displayName() {
		return Component.translatable("seedscout.mode." + this.id);
	}

	/** Translated one-line explanation shown as a tooltip. */
	public Component description() {
		return Component.translatable("seedscout.mode." + this.id + ".info");
	}

	/** True when the mode actually searches (everything except {@link #RANDOM}). */
	public boolean searches() {
		return this != RANDOM;
	}

	public static SearchMode byId(String id) {
		for (SearchMode mode : values()) {
			if (mode.id.equals(id)) {
				return mode;
			}
		}
		return RANDOM;
	}
}
