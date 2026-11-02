package com.seedscout.search;

/**
 * What "nice terrain" means, as weights (0 = don't care). Used by the Survival / Nice Terrain mode.
 * Terrain is measured from the noise heights around spawn, without generating chunks.
 *
 * @param radius    measured area around spawn (blocks)
 * @param water     wants some water (lakes, rivers, coast) but not mostly water
 * @param relief    wants hills / height variety
 * @param mountains wants a mountain or high cliff in the area
 * @param flat      wants flat building space
 */
public record TerrainGoals(int radius, double water, double relief, double mountains, double flat) {
	public static final TerrainGoals DEFAULT = new TerrainGoals(160, 2.0, 2.5, 1.5, 2.0);
}
