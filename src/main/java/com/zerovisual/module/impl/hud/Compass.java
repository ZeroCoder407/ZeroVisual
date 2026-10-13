package com.zerovisual.module.impl.hud;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** Compass strip: directions and degrees around where you are looking. */
public class Compass extends HudModule {
	private static final float W = 190;
	private static final float H = 20;
	/** Degrees visible on each side of the center. */
	private static final float RANGE = 90;
	private static final String[] NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	public Compass() {
		super("Compass", "Compass strip with directions");
	}

	@Override
	public boolean isDraggable() {
		return true;
	}

	@Override
	protected float[] defaultPosition(float screenW, float screenH, float w, float h) {
		// top center, below the boss bar area
		return new float[]{screenW / 2f - w / 2, 22};
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		if (mc.player == null) return;
		float[] pos = place(ctx, W, H);
		float x = pos[0], y = pos[1];
		float cx = x + W / 2;
		// Minecraft yaw 0 = south; compass 0 = north
		float heading = MathHelper.floorMod(mc.player.getYaw(tickDelta) + 180f, 360f);
		int accent = Theme.accent();

		Theme.card(ctx, x, y, W, H);
		ctx.enableScissor((int) x, (int) y, (int) (x + W), (int) (y + H));
		for (int deg = 0; deg < 360; deg += 15) {
			float delta = MathHelper.wrapDegrees(deg - heading);
			if (Math.abs(delta) > RANGE + 10) continue;
			float px = cx + delta / RANGE * (W / 2 - 6);
			float fade = 1f - Math.min(1f, Math.abs(delta) / RANGE);
			if (deg % 45 == 0) {
				String name = NAMES[deg / 45];
				int color = name.length() == 1 ? ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, fade) : ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT_MUTED, fade);
				if (deg == 0) color = ColorUtil.lerp(Theme.TEXT_DIM, accent, fade);
				Render2D.centeredText(ctx, name, px, y + 6, ColorUtil.fade(color, 0.3f + 0.7f * fade));
			} else {
				Render2D.rect(ctx, px - 0.5f, y + 8, 1, 4, 0.5f, ColorUtil.withAlpha(0xFFFFFF, 0.15f + 0.3f * fade));
			}
		}
		ctx.disableScissor();

		// center marker and the exact heading
		Render2D.rect(ctx, cx - 0.75f, y + 1, 1.5f, 3, 0.75f, accent);
		String degrees = Math.round(heading) % 360 + "°";
		float dw = Render2D.textWidth(degrees) * 0.7f + 6;
		Render2D.rect(ctx, cx - dw / 2, y + H + 2, dw, 9, 3, 0xD00D0D12);
		Render2D.scaledText(ctx, degrees, cx - Render2D.textWidth(degrees) * 0.7f / 2, y + H + 3.5f, 0.7f, Theme.TEXT);
	}
}
