package com.seedscout.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/** Small helpers shared by the Seedscout client game tests. */
public final class TestUtil {
	private TestUtil() {
	}

	/** Finds a widget of the current screen whose text contains the translation of {@code key}. */
	public static Optional<AbstractWidget> widget(ClientGameTestContext ctx, String key) {
		return ctx.computeOnClient(mc -> {
			// The LAST match is used: Seedscout's widgets are added after vanilla's
			// (e.g. vanilla "Customize" for the world type vs. our "Customize").
			String text = Component.translatable(key).getString();
			AbstractWidget found = null;
			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof AbstractWidget w && w.getMessage().getString().contains(text)) {
					found = w;
				}
			}
			return Optional.ofNullable(found);
		});
	}

	public static AbstractWidget require(ClientGameTestContext ctx, String key) {
		return widget(ctx, key).orElseThrow(() -> {
			ctx.takeScreenshot("failure_missing_widget");
			String all = ctx.computeOnClient(mc -> mc.gui.screen().children().stream()
				.map(c -> c instanceof AbstractWidget w ? w.getMessage().getString() : c.getClass().getSimpleName())
				.toList().toString());
			return new AssertionError("No widget with text '" + key + "' on " + ctx.computeOnClient(mc -> mc.gui.screen()) + ", widgets: " + all);
		});
	}

	/** Selects the "World" tab (index 1) of the Create World screen. */
	public static void selectWorldTab(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			// Copy first: selecting a tab replaces the screen's widget list.
			for (GuiEventListener child : List.copyOf(mc.gui.screen().children())) {
				if (child instanceof TabNavigationBar bar) {
					bar.selectTab(1, false);
				}
			}
		});
		ctx.waitTicks(2);
	}

	/**
	 * Clicks the centre of a widget through the screen's own mouse handling (press + release).
	 * Note: since 26.x the left mouse button is {@code InputConstants.MOUSE_BUTTON_LEFT} = 1, not 0.
	 */
	public static void click(ClientGameTestContext ctx, AbstractWidget w) {
		ctx.runOnClient(mc -> {
			MouseButtonEvent event = new MouseButtonEvent(w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
			Screen screen = mc.gui.screen();
			screen.mouseClicked(event, false);
			screen.mouseReleased(event);
		});
		ctx.waitTicks(2);
	}

	/** Left click at a GUI position of the current screen (press + release). */
	public static void clickAt(ClientGameTestContext ctx, double x, double y) {
		ctx.runOnClient(mc -> {
			MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
			mc.gui.screen().mouseClicked(event, false);
			mc.gui.screen().mouseReleased(event);
		});
		ctx.waitTicks(2);
	}

	public static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
		System.out.println("[Seedscout test] OK " + message);
	}
}
