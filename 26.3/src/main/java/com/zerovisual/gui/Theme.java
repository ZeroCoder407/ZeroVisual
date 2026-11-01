package com.zerovisual.gui;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.hud.HudStyle;
import com.zerovisual.module.impl.utilities.ClickGui;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared palette and card style for the menu and HUD. */
public final class Theme {
	public static final int BACKGROUND = 0xF00D0D12;
	public static final int HEADER = 0xFF13131A;
	public static final int SURFACE = 0xFF09090D;
	public static final int SHADOW = 0x70000000;
	public static final int TEXT = 0xFFEDEDF5;
	public static final int TEXT_DIM = 0xFF6F6F80;
	public static final int TEXT_MUTED = 0xFF9A9AAD;
	public static final int TRACK = 0xFF24242E;

	public static final float RADIUS = 6f;

	private Theme() {
	}

	public static int accent() {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return 0xFF8B5CF6;
		return client.modules().get(ClickGui.class).accent.get() | 0xFF000000;
	}

	/** Second gradient stop, a lighter shade of the accent. */
	public static int accentLight() {
		return ColorUtil.brighten(accent(), 1.35f);
	}

	private static final float CARD_OPACITY = 0.88f;

	private static HudStyle style() {
		return ZeroVisualClient.get().modules().get(HudStyle.class);
	}

	private static int cardBackground(HudStyle style) {
		return ColorUtil.lightness(style.background.get(), style.lightness.getFloat());
	}

	/** Background color of HUD cards at the opacity set in HudStyle. */
	public static int cardColor() {
		HudStyle style = style();
		return ColorUtil.withAlpha(cardBackground(style), style.opacity.getFloat());
	}

	/** The HudStyle outline around a card. */
	public static void cardEdge(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float radius) {
		HudStyle style = style();
		if (style.outline.get()) {
			Render2D.outline(ctx, x, y, w, h, radius, style.outlineWidth.getFloat(), style.outlineAccent.get() ? accent() : style.outlineColor.get() | 0xFF000000);
		}
	}

	/** Card in the style set in HudStyle, used by HUD elements. */
	public static void card(GuiGraphicsExtractor ctx, float x, float y, float w, float h) {
		card(ctx, x, y, w, h, CARD_OPACITY);
	}

	/** Card with its own background opacity (0..1), scaled by HudStyle around the default; the shadow fades with it. */
	public static void card(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float opacity) {
		HudStyle style = style();
		float alpha = Math.min(1, opacity * style.opacity.getFloat() / CARD_OPACITY);
		float radius = style.radius.getFloat();
		if (alpha > 0.01f) {
			if (style.shadow.get()) Render2D.shadow(ctx, x, y + 1, w, h, radius, 6, ColorUtil.fade(SHADOW, alpha));
			Render2D.rect(ctx, x, y, w, h, radius, ColorUtil.withAlpha(cardBackground(style), alpha));
		}
		cardEdge(ctx, x, y, w, h, radius);
	}
}
