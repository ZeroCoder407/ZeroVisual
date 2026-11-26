package com.zerovisual.gui;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.HudModule;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.util.Lang;
import net.minecraft.client.Minecraft;
import com.zerovisual.render.DrawContext;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

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
		return Minecraft.getMinecraft().currentScreen instanceof GuiChat;
	}

	/** Forge events of the chat screen: mouse input before the chat sees it, and drawing after it. */
	public static void register() {
		MinecraftForge.EVENT_BUS.register(new HudEditor.Events());
	}

	public static final class Events {
		@SubscribeEvent
		public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
			if (!(event.gui instanceof GuiChat)) return;
			int button = Mouse.getEventButton();
			if (button < 0) return;
			double mx = mouseX(event.gui), my = mouseY(event.gui);
			if (Mouse.getEventButtonState()) {
				HudModule hovered = hovered(mx, my);
				if (hovered == null) return;
				if (button == 0) {
					dragging = hovered;
					offsetX = (float) (mx - hovered.getLastX());
					offsetY = (float) (my - hovered.getLastY());
				} else if (button == 1) {
					hovered.resetPosition();
					ZeroVisualClient.get().config().save();
				}
				event.setCanceled(true);
			} else if (dragging != null) {
				dragging = null;
				ZeroVisualClient.get().config().save();
				event.setCanceled(true);
			}
		}

		@SubscribeEvent
		public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
			if (!(event.gui instanceof GuiChat)) return;
			GuiScreen s = event.gui;
			if (dragging != null) drag((int) mouseX(s), (int) mouseY(s), s.width, s.height);
			renderOverlay(new DrawContext(), event.mouseX, event.mouseY, s.width, s.height);
		}

		@SubscribeEvent
		public void onOpen(GuiOpenEvent event) {
			if (dragging != null) ZeroVisualClient.get().config().save();
			dragging = null;
		}
	}

	private static double mouseX(GuiScreen s) {
		return Mouse.getX() * s.width / (double) Minecraft.getMinecraft().displayWidth;
	}

	private static double mouseY(GuiScreen s) {
		return s.height - Mouse.getY() * s.height / (double) Minecraft.getMinecraft().displayHeight - 1;
	}

	private static HudModule hovered(double mx, double my) {
		HudModule found = null;
		for (Module module : ZeroVisualClient.get().modules().getModules()) {
			if (!(module instanceof HudModule)) continue;
			HudModule hud = (HudModule) module;
			if (hud.isEnabled() && hud.isDraggable() && hud.isOnScreen() && hud.contains(mx, my)) {
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
			if (!(module instanceof HudModule)) continue;
			HudModule hud = (HudModule) module;
			if (!hud.isEnabled() || !hud.isDraggable() || !hud.isOnScreen()) continue;
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
			String hint = Lang.get("zerovisual.hud.editor.hint");
			float hw = Render2D.textWidth(hint) + 14;
			Render2D.rect(ctx, (screenW - hw) / 2f, 4, hw, 14, 6, 0xC00D0D12);
			Render2D.centeredText(ctx, hint, screenW / 2f, 7, Theme.TEXT_MUTED);
		}
	}
}
