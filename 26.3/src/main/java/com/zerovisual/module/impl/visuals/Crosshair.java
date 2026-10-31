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
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Replaces the vanilla crosshair. Drawn from InGameHudMixin. */
public class Crosshair extends Module {
	public final ModeSetting style = add(new ModeSetting("Style", "Cross", "Cross", "Cross + Dot", "Dot", "Circle", "Circle + Dot", "T", "Square", "Brackets", "Chevron", "Diamond"));
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
	public final BooleanSetting targetColor = add(new BooleanSetting("Target color", false));
	public final ColorSetting onTarget = add(new ColorSetting("On target", 0xFFF43F5E)
		.visibleWhen(targetColor::get));
	public final BooleanSetting attackIndicator = add(new BooleanSetting("Attack indicator", true));

	private final Anim spread = new Anim(12f, 0f);

	public Crosshair() {
		super("Crosshair", "Custom crosshair: style, size, color, dynamic", Category.VISUALS);
	}

	public void draw(GuiGraphicsExtractor ctx) {
		float cx = ctx.guiWidth() / 2f;
		float cy = ctx.guiHeight() / 2f;
		int c = useAccent.get() ? Theme.accent() : (color.get() | 0xFF000000);
		if (targetColor.get() && aimingAtEntity()) c = onTarget.get() | 0xFF000000;
		float t = thickness.getFloat();

		float moving = 0;
		if (dynamic.get() && mc.player != null) {
			double speed = Math.hypot(mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().z);
			moving = (float) Math.min(4, speed * 18 + (mc.player.onGround() ? 0 : 1.5));
		}
		float g = gap.getFloat() + spread.update(moving);
		float s = size.getFloat();

		switch (style.get()) {
			case "Dot" -> dot(ctx, cx, cy, t + 1, c);
			case "T" -> {
				line(ctx, cx - t / 2, cy + g, t, s, c);
				line(ctx, cx - g - s, cy - t / 2, s, t, c);
				line(ctx, cx + g, cy - t / 2, s, t, c);
			}
			case "Square" -> {
				float r = g + s / 2;
				if (outline.get()) Render2D.outline(ctx, cx - r - 0.5f, cy - r - 0.5f, r * 2 + 1, r * 2 + 1, 0, t + 1, 0xA0000000);
				Render2D.outline(ctx, cx - r, cy - r, r * 2, r * 2, 0, t, c);
			}
			case "Brackets" -> {
				float r = g + s, l = s * 0.7f;
				for (int sx = -1; sx <= 1; sx += 2) {
					for (int sy = -1; sy <= 1; sy += 2) {
						line(ctx, sx < 0 ? cx - r : cx + r - l, sy < 0 ? cy - r : cy + r - t, l, t, c);
						line(ctx, sx < 0 ? cx - r : cx + r - t, sy < 0 ? cy - r : cy + r - l, t, l, c);
					}
				}
			}
			case "Chevron" -> {
				// two diagonal strokes meeting under the centre, drawn as steps so they stay pixel sharp
				int steps = Math.max(2, Math.round(s));
				for (int i = 0; i < steps; i++) {
					Render2D.rect(ctx, cx - i - t, cy + g + steps - i, t, t, 0, c);
					Render2D.rect(ctx, cx + i, cy + g + steps - i, t, t, 0, c);
				}
				dot(ctx, cx, cy, t * 0.6f, c);
			}
			case "Diamond" -> {
				int r = Math.max(2, Math.round(g + s / 2));
				for (int i = 0; i < r; i++) {
					Render2D.rect(ctx, cx + i, cy - r + i, t, t, 0, c);
					Render2D.rect(ctx, cx + r - i, cy + i, t, t, 0, c);
					Render2D.rect(ctx, cx - i, cy + r - i, t, t, 0, c);
					Render2D.rect(ctx, cx - r + i, cy - i, t, t, 0, c);
				}
			}
			case "Circle + Dot", "Circle" -> {
				float r = g + s / 2;
				if (outline.get()) Render2D.outline(ctx, cx - r - 0.5f, cy - r - 0.5f, r * 2 + 1, r * 2 + 1, r + 0.5f, t + 1, 0xA0000000);
				Render2D.outline(ctx, cx - r, cy - r, r * 2, r * 2, r, t, c);
				if (style.is("Circle + Dot")) dot(ctx, cx, cy, t * 0.8f, c);
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
			float progress = mc.player.getAttackStrengthScale(0f);
			if (progress < 1f) {
				float w = 16, y = cy + g + s + 4;
				Render2D.rect(ctx, cx - w / 2, y, w, 2, 1, 0x80000000);
				Render2D.rect(ctx, cx - w / 2, y, w * progress, 2, 1, c);
			}
		}
	}

	private boolean aimingAtEntity() {
		return mc.crosshairPickEntity != null;
	}

	private void line(GuiGraphicsExtractor ctx, float x, float y, float w, float h, int c) {
		if (outline.get()) Render2D.rect(ctx, x - 0.5f, y - 0.5f, w + 1, h + 1, 0.5f, 0xA0000000);
		Render2D.rect(ctx, x, y, w, h, 0, c);
	}

	private void dot(GuiGraphicsExtractor ctx, float cx, float cy, float r, int c) {
		if (outline.get()) Render2D.circle(ctx, cx, cy, r + 0.6f, 0xA0000000);
		Render2D.circle(ctx, cx, cy, r, c);
	}
}
