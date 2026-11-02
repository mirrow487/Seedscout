package com.seedscout.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns computed tiles into GPU textures (render thread only) and keeps memory bounded: textures of
 * tiles not drawn recently are released, and tile data far from the view is dropped (it is simply
 * computed again if the player comes back).
 */
public final class MapTileTextures {
	/** Tile data kept at most (each tile is 64 KiB of colours, + 64 KiB of heights when shaded). */
	private static final int MAX_TILES = 1200;

	private final String seedPath;

	public MapTileTextures(long seed) {
		this.seedPath = Long.toHexString(seed);
	}

	public @Nullable Identifier id(MapTile tile) {
		return tile.textureId;
	}

	public boolean hasHeights(MapTile tile) {
		return tile.heights != null;
	}

	/** True when the texture is missing or shows something else than wanted. */
	public boolean needsUpload(MapTile tile, boolean shading) {
		return tile.uploaded != wanted(tile, shading);
	}

	private static int wanted(MapTile tile, boolean shading) {
		if (tile.biomeColors == null) {
			return 0;
		}
		return shading && tile.heights != null ? 2 : 1;
	}

	public void upload(MapTile tile, boolean shading) {
		int[] colors = tile.biomeColors;
		if (colors == null) {
			return;
		}
		int[] heights = shading ? tile.heights : null;
		NativeImage image = new NativeImage(MapTile.SIZE, MapTile.SIZE, false);
		int step = 1 << tile.level;
		for (int j = 0; j < MapTile.SIZE; j++) {
			for (int i = 0; i < MapTile.SIZE; i++) {
				int color = colors[j * MapTile.SIZE + i];
				if (heights != null) {
					color = shade(color, heights, i, j, step);
				}
				image.setPixel(i, j, color);
			}
		}
		if (tile.texture == null) {
			Identifier id = Identifier.fromNamespaceAndPath("seedscout",
				"map/" + this.seedPath + "/" + tile.level + "/" + tile.tileX + "_" + tile.tileZ);
			tile.texture = new DynamicTexture(() -> "Seedscout map tile", image);
			tile.textureId = id;
			Minecraft.getInstance().getTextureManager().register(id, tile.texture);
		} else {
			tile.texture.setPixels(image);
			tile.texture.upload();
		}
		tile.uploaded = heights != null ? 2 : 1;
	}

	/** Hill shading: light from the north-west, stronger for steeper slopes; high ground a little brighter. */
	private static int shade(int argb, int[] heights, int i, int j, int step) {
		int h = heights[j * MapTile.SIZE + i];
		int west = heights[j * MapTile.SIZE + Math.max(0, i - 1)];
		int north = heights[Math.max(0, j - 1) * MapTile.SIZE + i];
		double slope = ((h - west) + (h - north)) / (double) step;
		double factor = 1.0 + Mth.clamp(slope * 0.6, -0.4, 0.4) + Mth.clamp((h - 64) / 500.0, -0.1, 0.25);
		int r = Mth.clamp((int) (((argb >> 16) & 0xFF) * factor), 0, 255);
		int g = Mth.clamp((int) (((argb >> 8) & 0xFF) * factor), 0, 255);
		int b = Mth.clamp((int) ((argb & 0xFF) * factor), 0, 255);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	public void markDrawn(MapTile tile, long frame) {
		tile.lastDrawn = frame;
	}

	/** Keeps at most {@code maxTextures} textures and {@link #MAX_TILES} tiles of data. */
	public void evict(MapSession session, int maxTextures, long frame) {
		List<MapTile> withTexture = new ArrayList<>();
		int count = 0;
		for (MapTile tile : session.allTiles()) {
			count++;
			if (tile.texture != null) {
				withTexture.add(tile);
			}
		}
		if (withTexture.size() > maxTextures) {
			withTexture.sort(Comparator.comparingLong(t -> t.lastDrawn));
			for (int i = 0; i < withTexture.size() - maxTextures * 4 / 5; i++) {
				MapTile tile = withTexture.get(i);
				if (tile.lastDrawn < frame) {
					release(tile);
				}
			}
		}
		if (count > MAX_TILES) {
			session.removeTiles(tile -> tile.texture == null && tile.lastDrawn < frame - 120
				// still being computed: keep, the worker writes into it
				&& !(tile.biomeRequested && tile.biomeColors == null) && !(tile.heightRequested && tile.heights == null), MAX_TILES * 4 / 5);
		}
	}

	private static void release(MapTile tile) {
		if (tile.textureId != null) {
			Minecraft.getInstance().getTextureManager().release(tile.textureId);
		}
		tile.texture = null;
		tile.textureId = null;
		tile.uploaded = 0;
	}

	public void releaseAll(MapSession session) {
		for (MapTile tile : session.allTiles()) {
			release(tile);
		}
	}
}
