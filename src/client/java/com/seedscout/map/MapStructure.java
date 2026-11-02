package com.seedscout.map;

import com.seedscout.worldgen.StructureHit;

/**
 * A structure on the map.
 *
 * @param hit           the structure and its position
 * @param spacingBlocks average distance between structures of its set (used to hide very common
 *                      structures such as mineshafts when zoomed out)
 */
public record MapStructure(StructureHit hit, int spacingBlocks) {
}
