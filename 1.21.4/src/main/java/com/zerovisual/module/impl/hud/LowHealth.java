package com.zerovisual.module.impl.hud;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

/** Red pulsing vignette at the screen edges when your health is low. */
public class LowHealth extends Module {
	public final NumberSetting threshold = add(new NumberSetting("Hearts", 4, 1, 10, 0.5));
	public final NumberSetting strength = add(new NumberSetting("Strength", 0.6, 0.1, 1, 0.05));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFE11D48));

	private float shown;

	public LowHealth() {
		super("LowHealth", "Red pulsing vignette when your health is low", Category.HUD);
	}

	public void render(DrawContext ctx) {
		if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) return;
		// real health only: absorption hearts (e.g. after a totem) do not make you safe
		float hp = mc.player.getHealth();
		float limit = threshold.getFloat() * 2;
		// stronger the lower the health, eased so it does not pop in
		float target = hp <= 0 || hp >= limit ? 0 : 1f - hp / limit * 0.6f;
		shown += (target - shown) * 0.12f;
		if (shown < 0.01f) return;

		float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 1000.0 * (4 + 4 * shown));
		float a = shown * pulse * strength.getFloat();
		int edge = ColorUtil.withAlpha(color.get(), a), none = ColorUtil.withAlpha(color.get(), 0);
		float w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
		float depthX = w * 0.18f, depthY = h * 0.22f;
		Render2D.gradientH(ctx, 0, 0, depthX, h, 0, edge, none);
		Render2D.gradientH(ctx, w - depthX, 0, depthX, h, 0, none, edge);
		Render2D.gradientV(ctx, 0, 0, w, depthY, 0, edge, none);
		Render2D.gradientV(ctx, 0, h - depthY, w, depthY, 0, none, edge);
	}
}
