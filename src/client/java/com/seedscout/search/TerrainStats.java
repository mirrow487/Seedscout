package com.seedscout.search;

/**
 * Terrain around spawn, measured from noise heights (no chunks).
 *
 * @param water     share of sampled columns that are water (0..1)
 * @param relief    height difference between the low and the high land (10th to 90th percentile), blocks
 * @param mountain  highest point minus spawn height, blocks
 * @param flat      share of land columns whose neighbours are within 2 blocks of their height (0..1)
 */
public record TerrainStats(double water, int relief, int mountain, double flat) {
}
