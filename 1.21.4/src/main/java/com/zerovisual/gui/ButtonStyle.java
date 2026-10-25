package com.zerovisual.gui;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.utilities.MainMenu;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.WeakHashMap;

/** Dark rounded look for vanilla buttons and sliders (PressableWidgetMixin, SliderWidgetMixin). */
public final class ButtonStyle {
	private static final Map<ClickableWidget, Anim> HOVER = new WeakHashMap<>();

	private ButtonStyle() {
	}

	public static boolean isEnabled() {
		ZeroVisualClient client = ZeroVisualClient.get();
		return client != null && client.modules().get(MainMenu.class).roundedButtons.get();
	}

	private static float hover(ClickableWidget widget, boolean hovered) {
		return HOVER.computeIfAbsent(widget, w -> new Anim(16f, 0f)).update(hovered);
	}

	public static void drawButton(DrawContext ctx, ClickableWidget widget, float x, float y, float w, float h,
								  boolean active, boolean hovered, float alpha) {
		float hv = active ? hover(widget, hovered) : 0f;
		float prev = Render2D.getAlpha();
		Render2D.setAlpha(prev * alpha);
		int accent = Theme.accent();
		Render2D.shadow(ctx, x, y + 1, w, h, 6, 4, 0x50000000);
		int bg = active ? ColorUtil.lerp(0xE612121A, 0xF01C1A2A, hv) : 0x9A0E0E12;
		Render2D.rect(ctx, x, y, w, h, 6, bg);
		if (hv > 0.01f) {
			Render2D.gradientH(ctx, x, y, w, h, 6, ColorUtil.withAlpha(accent, 0.18f * hv), ColorUtil.withAlpha(accent, 0.04f * hv));
		}
		Render2D.outline(ctx, x, y, w, h, 6, 1, active ? ColorUtil.lerp(0x1CFFFFFF, accent, hv) : 0x10FFFFFF);
		Render2D.setAlpha(prev);
	}

	public static void drawLabel(DrawContext ctx, ClickableWidget widget, Text message, float x, float y, float w, float h,
								 boolean active, boolean hovered, float alpha) {
		float hv = active ? hover(widget, hovered) : 0f;
		String text = message.getString();
		float maxW = w - 10;
		if (Render2D.textWidth(text) > maxW) {
			while (text.length() > 1 && Render2D.textWidth(text + "...") > maxW) text = text.substring(0, text.length() - 1);
			text += "...";
		}
		int color = active ? ColorUtil.lerp(Theme.TEXT_MUTED, 0xFFFFFFFF, hv) : Theme.TEXT_DIM;
		float prev = Render2D.getAlpha();
		Render2D.setAlpha(prev * alpha);
		Render2D.centeredText(ctx, text, x + w / 2f, y + (h - 8) / 2f, color);
		Render2D.setAlpha(prev);
	}

	public static void drawSliderTrack(DrawContext ctx, float x, float y, float w, float h, boolean active, float alpha) {
		float prev = Render2D.getAlpha();
		Render2D.setAlpha(prev * alpha);
		Render2D.rect(ctx, x, y, w, h, 6, active ? 0xE612121A : 0x9A0E0E12);
		Render2D.outline(ctx, x, y, w, h, 6, 1, 0x1CFFFFFF);
		Render2D.setAlpha(prev);
	}

	public static void drawSliderHandle(DrawContext ctx, float x, float y, float w, float h, boolean hovered, float alpha) {
		float prev = Render2D.getAlpha();
		Render2D.setAlpha(prev * alpha);
		int accent = Theme.accent();
		if (hovered) Render2D.shadow(ctx, x, y, w, h, 3, 4, ColorUtil.withAlpha(accent, 0.6f));
		Render2D.gradientV(ctx, x + 1, y + 2, w - 2, h - 4, 3, Theme.accentLight(), accent);
		Render2D.setAlpha(prev);
	}
}
