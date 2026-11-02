package com.seedscout.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seedscout.SeedscoutClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Global client settings, stored in {@code config/seedscout/settings.json}.
 * <p>
 * Presets (rule lists) will be stored as separate JSON files in {@code config/seedscout/presets/}
 * in a later stage.
 */
public final class SeedscoutConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static SeedscoutConfig instance;

	/** Last selected mode on the Create World screen. */
	public SearchMode mode = SearchMode.RANDOM;
	/** Number of search threads; 0 = automatic (half of the CPU threads, at most 8). */
	public int threads = 0;
	/** Map preview radius around spawn, in blocks. */
	public int mapRadius = 10000;

	private SeedscoutConfig() {
	}

	public static SeedscoutConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve(SeedscoutClient.MOD_ID);
	}

	private static Path file() {
		return directory().resolve("settings.json");
	}

	/**
	 * Threads actually used for searching. The automatic value leaves half of the CPU to the game:
	 * with (almost) all cores busy the game itself became slow enough to look frozen.
	 */
	public int effectiveThreads() {
		int cores = Runtime.getRuntime().availableProcessors();
		return this.threads > 0 ? Math.min(this.threads, cores) : Math.clamp(cores / 2, 1, 8);
	}

	private static SeedscoutConfig load() {
		SeedscoutConfig config = new SeedscoutConfig();
		Path file = file();
		if (!Files.isRegularFile(file)) {
			return config;
		}
		try {
			JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			if (json.has("mode")) {
				config.mode = SearchMode.byId(json.get("mode").getAsString());
			}
			if (json.has("mapRadius")) {
				config.mapRadius = Math.clamp(json.get("mapRadius").getAsInt(), 500, 50000);
			}
			if (json.has("threads")) {
				config.threads = Math.max(0, json.get("threads").getAsInt());
			}
		} catch (Exception e) {
			// A broken file must never crash the title screen: fall back to defaults.
			SeedscoutClient.LOGGER.warn("Could not read {}, using defaults", file, e);
		}
		return config;
	}

	public void save() {
		JsonObject json = new JsonObject();
		json.addProperty("mode", this.mode.id());
		json.addProperty("threads", this.threads);
		json.addProperty("mapRadius", this.mapRadius);
		try {
			Files.createDirectories(directory());
			Files.writeString(file(), GSON.toJson(json), StandardCharsets.UTF_8);
		} catch (IOException e) {
			SeedscoutClient.LOGGER.warn("Could not save {}", file(), e);
		}
	}
}
