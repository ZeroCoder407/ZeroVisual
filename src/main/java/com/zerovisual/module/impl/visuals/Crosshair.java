package com.zerovisual.module.impl.visuals;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.Anim;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

/** Replaces the vanilla crosshair. Drawn from InGameHudMixin. */
public class Crosshair extends Module {
	public final ModeSetting style = add(new ModeSetting("Style", "Cross", "Cross", "Cross + Dot", "Dot", "Circle"));
	public final NumberSetting size = add(new NumberSetting("Size", 5, 2, 12, 0.5)
		.visibleWhen(() -> !style.is("Dot")));
	public final NumberSetting gap = add(new NumberSetting("Gap", 2, 0, 8, 0.5)
		.visibleWhen(() -> !style.is("Dot")));
	public final NumberSetting thickness = add(new NumberSetting("Thickness", 1, 0.5, 3, 0.25));
	public final BooleanSetting dynamic = add(new BooleanSetting("Dynamic", true));
	public final BooleanSetting outline = add(new BooleanSetting("Outline", true));
	public final BooleanSetting useAccent = add(new BooleanSetting("Accent color", false));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFFFF)
		.visibleWhen(() -> !useAccent.get()));
	public final BooleanSetting attackIndicator = add(new BooleanSetting("Attack indicator", true));

	private final Anim spread = new Anim(12f, 0f);

	public Crosshair() {
		super("Crosshair", "Свой прицел: стиль, размер, цвет, динамика", Category.VISUALS);
	}

	public void draw(DrawContext ctx) {
		float cx = ctx.getScaledWindowWidth() / 2f;
		float cy = ctx.getScaledWindowHeight() / 2f;
		int c = useAccent.get() ? Theme.accent() : (color.get() | 0xFF000000);
		float t = thickness.getFloat();

		float moving = 0;
		if (dynamic.get() && mc.player != null) {
			double speed = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
			moving = (float) Math.min(4, speed * 18 + (mc.player.isOnGround() ? 0 : 1.5));
		}
		float g = gap.getFloat() + spread.update(moving);
		float s = size.getFloat();

		switch (style.get()) {
			case "Dot" -> dot(ctx, cx, cy, t + 1, c);
			case "Circle" -> {
				float r = g + s / 2;
				if (outline.get()) Render2D.outline(ctx, cx - r - 0.5f, cy - r - 0.5f, r * 2 + 1, r * 2 + 1, r + 0.5f, t + 1, 0xA0000000);
				Render2D.outline(ctx, cx - r, cy - r, r * 2, r * 2, r, t, c);
			}
			default -> {
				line(ctx, cx - t / 2, cy - g - s, t, s, c);
				line(ctx, cx - t / 2, cy + g, t, s, c);
				line(ctx, cx - g - s, cy - t / 2, s, t, c);
				line(ctx, cx + g, cy - t / 2, s, t, c);
				if (style.is("Cross + Dot")) dot(ctx, cx, cy, t, c);
			}
		}

		if (attackIndicator.get() && mc.player != null) {
			float progress = mc.player.getAttackCooldownProgress(0f);
			if (progress < 1f) {
				float w = 16, y = cy + g + s + 4;
				Render2D.rect(ctx, cx - w / 2, y, w, 2, 1, 0x80000000);
				Render2D.rect(ctx, cx - w / 2, y, w * progress, 2, 1, c);
			}
		}
	}

	private void line(DrawContext ctx, float x, float y, float w, float h, int c) {
		if (outline.get()) Render2D.rect(ctx, x - 0.5f, y - 0.5f, w + 1, h + 1, 0.5f, 0xA0000000);
		Render2D.rect(ctx, x, y, w, h, 0, c);
	}

	private void dot(DrawContext ctx, float cx, float cy, float r, int c) {
		if (outline.get()) Render2D.circle(ctx, cx, cy, r + 0.6f, 0xA0000000);
		Render2D.circle(ctx, cx, cy, r, c);
	}
}
