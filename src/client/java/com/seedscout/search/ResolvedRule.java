package com.seedscout.search;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * A {@link Rule} resolved against the registries of the world being created (vanilla + data packs + mods).
 * Ids that do not exist (e.g. a removed data pack) simply match nothing.
 */
public record ResolvedRule(Rule rule, Set<Structure> structures, Set<Biome> biomes) {
	public static ResolvedRule resolve(Rule rule, RegistryAccess registries) {
		if (rule.target() == Rule.Target.STRUCTURE) {
			return new ResolvedRule(rule, collect(registries.lookupOrThrow(Registries.STRUCTURE), rule), Set.of());
		}
		return new ResolvedRule(rule, Set.of(), collect(registries.lookupOrThrow(Registries.BIOME), rule));
	}

	private static <T> Set<T> collect(Registry<T> registry, Rule rule) {
		Set<T> out = Collections.newSetFromMap(new IdentityHashMap<>());
		for (String raw : rule.ids()) {
			String id = raw.trim();
			if (id.startsWith("#")) {
				Identifier tag = Identifier.tryParse(id.substring(1));
				if (tag != null) {
					registry.get(TagKey.create(registry.key(), tag)).ifPresent(set -> set.forEach(h -> out.add(h.value())));
				}
			} else if (id.endsWith("*")) {
				String prefix = id.substring(0, id.length() - 1);
				registry.listElements().filter(h -> h.key().identifier().toString().startsWith(prefix)).forEach(h -> out.add(h.value()));
			} else {
				Identifier key = Identifier.tryParse(id);
				if (key != null) {
					registry.get(ResourceKey.create(registry.key(), key)).map(Holder::value).ifPresent(out::add);
				}
			}
		}
		return out;
	}

	public boolean matchesBiome(Holder<Biome> biome) {
		return this.biomes.contains(biome.value());
	}
}
