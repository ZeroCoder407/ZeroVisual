package com.zerovisual.gui;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.HudModule;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

/**
 * Drag HUD elements while the chat is open. Left mouse: move, right mouse: reset to default.
 * Snaps to the screen center lines.
 */
public final class HudEditor {
	private static final float SNAP = 4;

	private static HudModule dragging;
	private static float offsetX, offsetY;
	private static boolean snappedX, snappedY;

	private HudEditor() {
	}

	public static boolean isEditing() {
		return MinecraftClient.getInstance().currentScreen instanceof ChatScreen;
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof ChatScreen)) return;

			ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, button) -> {
				HudModule hovered = hovered(mx, my);
				if (hovered == null) return true;
				if (button == 0) {
					dragging = hovered;
					offsetX = (float) (mx - hovered.getLastX());
					offsetY = (float) (my - hovered.getLastY());
				} else if (button == 1) {
					hovered.resetPosition();
					ZeroVisualClient.get().config().save();
				}
				return false;
			});

			ScreenMouseEvents.afterMouseRelease(screen).register((s, mx, my, button) -> {
				if (dragging != null) {
					dragging = null;
					ZeroVisualClient.get().config().save();
				}
			});

			ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
				if (dragging != null) drag(mx, my, s.width, s.height);
				renderOverlay(ctx, mx, my, s.width, s.height);
			});

			ScreenEvents.remove(screen).register(s -> {
				if (dragging != null) ZeroVisualClient.get().config().save();
				dragging = null;
			});
		});
	}

	private static HudModule hovered(double mx, double my) {
		HudModule found = null;
		for (Module module : ZeroVisualClient.get().modules().getModules()) {
			if (module instanceof HudModule hud && hud.isEnabled() && hud.isDraggable() && hud.isOnScreen() && hud.contains(mx, my)) {
				found = hud;
			}
		}
		return found;
	}

	private static void drag(int mx, int my, int screenW, int screenH) {
		float w = dragging.getLastW(), h = dragging.getLastH();
		float x = mx - offsetX;
		float y = my - offsetY;

		float centerX = (screenW - w) / 2f;
		float centerY = (screenH - h) / 2f;
		snappedX = Math.abs(x - centerX) < SNAP;
		snappedY = Math.abs(y - centerY) < SNAP;
		if (snappedX) x = centerX;
		if (snappedY) y = centerY;

		dragging.moveTo(Math.max(0, Math.min(x, screenW - w)), Math.max(0, Math.min(y, screenH - h)), screenW, screenH);
	}

	private static void renderOverlay(DrawContext ctx, int mx, int my, int screenW, int screenH) {
		boolean any = false;
		HudModule hovered = dragging != null ? dragging : hovered(mx, my);
		int accent = Theme.accent();

		for (Module module : ZeroVisualClient.get().modules().getModules()) {
			if (!(module instanceof HudModule hud) || !hud.isEnabled() || !hud.isDraggable() || !hud.isOnScreen()) continue;
			any = true;
			boolean active = hud == hovered;
			float x = hud.getLastX() - 2, y = hud.getLastY() - 2, w = hud.getLastW() + 4, h = hud.getLastH() + 4;
			Render2D.outline(ctx, x, y, w, h, 8, 1, active ? accent : 0x40FFFFFF);
			if (active) {
				Render2D.rect(ctx, x, y, w, h, 8, ColorUtil.withAlpha(accent, 0.08f));
				String label = hud.getName();
				float lw = Render2D.textWidth(label) + 8;
				float ly = y - 13 < 0 ? y + h + 2 : y - 13;
				Render2D.rect(ctx, x, ly, lw, 11, 4, ColorUtil.withAlpha(accent, 0.9f));
				Render2D.text(ctx, label, x + 4, ly + 2, 0xFFFFFFFF);
			}
		}

		if (dragging != null) {
			if (snappedX) Render2D.rect(ctx, screenW / 2f - 0.5f, 0, 1, screenH, 0, ColorUtil.withAlpha(accent, 0.6f));
			if (snappedY) Render2D.rect(ctx, 0, screenH / 2f - 0.5f, screenW, 1, 0, ColorUtil.withAlpha(accent, 0.6f));
		}

		if (any) {
			String hint = "Перетаскивайте элементы HUD  •  ПКМ — вернуть на место";
			float hw = Render2D.textWidth(hint) + 14;
			Render2D.rect(ctx, (screenW - hw) / 2f, 4, hw, 14, 6, 0xC00D0D12);
			Render2D.centeredText(ctx, hint, screenW / 2f, 7, Theme.TEXT_MUTED);
		}
	}
}
