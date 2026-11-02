package com.seedscout.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * A structure that will generate in this world.
 *
 * @param structure the structure (e.g. minecraft:village_plains)
 * @param chunk     chunk of the structure start
 * @param pos       position reported by {@code /locate} (placement locate offset applied; y = 0)
 */
public record StructureHit(Holder<Structure> structure, ChunkPos chunk, BlockPos pos) {
	/** Horizontal distance in blocks to a point. */
	public double distanceTo(BlockPos from) {
		double dx = this.pos.getX() - from.getX();
		double dz = this.pos.getZ() - from.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
