package com.seedscout.map;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * One square piece of the map: {@link #SIZE}×{@link #SIZE} pixels, each pixel {@code 2^level} blocks.
 * Data is filled by worker threads; the texture is created on the render thread.
 */
public final class MapTile {
	public static final int SIZE = 128;

	public final int level;
	public final int tileX;
	public final int tileZ;
	/** ARGB biome colours, row-major (z rows, x columns). */
	volatile int @Nullable [] biomeColors;
	/** Terrain heights for shading (only computed while shading is on). */
	volatile int @Nullable [] heights;
	volatile boolean biomeRequested;
	volatile boolean heightRequested;
	/** Texture (render thread only). */
	@Nullable DynamicTexture texture;
	@Nullable Identifier textureId;
	/** What the uploaded texture shows: 0 = nothing, 1 = biomes, 2 = biomes + shading. */
	int uploaded;
	/** Last frame this tile was drawn (render thread only; for evicting textures). */
	long lastDrawn;

	MapTile(int level, int tileX, int tileZ) {
		this.level = level;
		this.tileX = tileX;
		this.tileZ = tileZ;
	}

	/** Blocks covered by one side of a tile at this level. */
	public static long blocks(int level) {
		return (long) SIZE << level;
	}

	public boolean hasBiomes() {
		return this.biomeColors != null;
	}

	static long key(int level, int tileX, int tileZ) {
		return ((long) level << 58) ^ ((long) (tileX & 0x1FFFFFFF) << 29) ^ (tileZ & 0x1FFFFFFF);
	}
}
