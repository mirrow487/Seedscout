package com.seedscout.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.seedscout.ui.CreateWorldControls;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the Seedscout row (finder button, mode selector, customize button) to the "World" tab
 * of the Create World screen, directly below the seed box and above the switches.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class WorldTabMixin {
	/** The enclosing CreateWorldScreen of this inner class. */
	@Shadow
	@Final
	CreateWorldScreen this$0;

	@Inject(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/worldselection/SwitchGrid;builder(I)Lnet/minecraft/client/gui/screens/worldselection/SwitchGrid$Builder;"
		)
	)
	private void seedscout$addControls(CallbackInfo ci, @Local GridLayout.RowHelper helper) {
		helper.addChild(CreateWorldControls.createRow(this.this$0), 2);
	}
}
