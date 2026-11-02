package com.seedscout.mixin;

import com.seedscout.ui.CreateWorldControls;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-opens the "World" tab when the player comes back from one of the Seedscout screens. */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
	@Shadow
	private @Nullable MenuTabBar tabNavigationBar;

	@Inject(method = "init", at = @At("TAIL"))
	private void seedscout$reopenWorldTab(CallbackInfo ci) {
		if (CreateWorldControls.consumeReturnToWorldTab() && this.tabNavigationBar != null) {
			this.tabNavigationBar.selectTab(1, false);
		}
	}
}
