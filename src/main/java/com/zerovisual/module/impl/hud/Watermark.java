package com.zerovisual.module.impl.hud;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Watermark extends HudModule {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	public final BooleanSetting showFps = add(new BooleanSetting("FPS", true));
	public final BooleanSetting showTime = add(new BooleanSetting("Time", true));

	public Watermark() {
		super("Watermark", "Логотип клиента, FPS и время");
		setEnabled(true);
	}

	@Override
	public boolean isDraggable() {
		return true;
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		String first = "Zero";
		String second = "Visual";
		StringBuilder info = new StringBuilder();
		if (showFps.get()) info.append("  ").append(mc.getCurrentFps()).append(" fps");
		if (showTime.get()) info.append("  ").append(LocalTime.now().format(TIME));

		float h = 18;
		float badge = 12;
		float textOffset = 6 + badge + 5;
		float w = textOffset + Render2D.textWidth(first + second) + Render2D.textWidth(info.toString()) + 7;
		float[] pos = place(ctx, w, h);
		float x = pos[0], y = pos[1];

		Theme.card(ctx, x, y, w, h);

		int accent = Theme.accent();
		Render2D.gradientV(ctx, x + 6, y + 3, badge, badge, 4, Theme.accentLight(), accent);
		Render2D.centeredText(ctx, "✦", x + 6 + badge / 2f + 0.5f, y + 5, 0xFFFFFFFF);

		float tx = x + textOffset;
		Render2D.text(ctx, first, tx, y + 5, accent);
		tx += Render2D.textWidth(first);
		Render2D.text(ctx, second, tx, y + 5, Theme.TEXT);
		tx += Render2D.textWidth(second);
		Render2D.text(ctx, info.toString(), tx, y + 5, Theme.TEXT_MUTED);
	}
}
