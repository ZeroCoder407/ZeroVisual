package com.zerovisual.module.impl.hud;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Locale;

/** Golden outlines on the hunger bar showing saturation. Drawn from InGameHudMixin after the food icons. */
public class Saturation extends Module {
	private static final int GOLD = 0xFFFFC83C;

	public final BooleanSetting showValue = add(new BooleanSetting("Show value", true));

	public Saturation() {
		super("Saturation", "Насыщение на полоске голода", Category.HUD);
	}

	/** Same layout as InGameHud#renderFood: 10 icons from right to left, 8 px apart. */
	public void draw(DrawContext ctx, PlayerEntity player, int top, int right) {
		float saturation = player.getHungerManager().getSaturationLevel();
		for (int i = 0; i < 10; i++) {
			float level = Math.max(0, Math.min(2, saturation - i * 2)) / 2f;
			if (level <= 0) break;
			float x = right - i * 8 - 9;
			Render2D.outline(ctx, x, top, 9, 9, 2.5f, 1.1f, ColorUtil.withAlpha(GOLD, 0.35f + 0.55f * level));
		}
		if (showValue.get()) {
			String text = String.format(Locale.ROOT, "%.1f", saturation);
			Render2D.scaledText(ctx, text, right + 3, top + 2, 0.75f, ColorUtil.withAlpha(GOLD, 0.9f));
		}
	}
}
