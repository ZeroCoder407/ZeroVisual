package com.zerovisual.gui;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.utilities.ClickGui;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import net.minecraft.client.gui.DrawContext;

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

	/** Dark translucent card with a soft shadow, used by HUD elements. */
	public static void card(DrawContext ctx, float x, float y, float w, float h) {
		card(ctx, x, y, w, h, 0xE0 / 255f);
	}

	/** Card with a custom background opacity (0..1); the shadow fades with it. */
	public static void card(DrawContext ctx, float x, float y, float w, float h, float opacity) {
		if (opacity <= 0.01f) return;
		Render2D.shadow(ctx, x, y + 1, w, h, RADIUS, 6, ColorUtil.fade(SHADOW, Math.min(1f, opacity / (0xE0 / 255f))));
		Render2D.rect(ctx, x, y, w, h, RADIUS, ColorUtil.withAlpha(0xFF0D0D12, opacity));
	}
}
