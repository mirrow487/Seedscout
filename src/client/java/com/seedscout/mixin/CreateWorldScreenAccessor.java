package com.seedscout.mixin;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;

/** Read access to the temporary data pack folder of the Create World screen (packs the player added). */
@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
	@Accessor("tempDataPackDir")
	@Nullable Path seedscout$getTempDataPackDir();
}
