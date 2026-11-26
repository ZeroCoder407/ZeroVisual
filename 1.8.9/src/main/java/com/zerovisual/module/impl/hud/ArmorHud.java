package com.zerovisual.module.impl.hud;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.render.DrawContext;
import net.minecraft.item.ItemStack;

/** Worn armor with durability. */
public class ArmorHud extends HudModule {
	/** Helmet first: 1.8.9 keeps the armor from the boots up. */
	private static final int[] SLOTS = {3, 2, 1, 0};
	private static final float CELL = 20;

	public final BooleanSetting percent = add(new BooleanSetting("Percent", true));

	public ArmorHud() {
		super("ArmorHUD", "Worn armor and its durability");
	}

	@Override
	public boolean isDraggable() {
		return true;
	}

	@Override
	protected float[] defaultPosition(float screenW, float screenH, float w, float h) {
		return new float[]{screenW / 2f + 96, screenH - h - 2};
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		if (mc.thePlayer == null) return;
		boolean any = false;
		for (int slot : SLOTS) any |= mc.thePlayer.inventory.armorInventory[slot] != null;
		if (!any && !isEditing()) return;

		float w = 6 + SLOTS.length * CELL + 2;
		float h = percent.get() ? 32 : 24;
		float[] pos = place(ctx, w, h);
		float x = pos[0], y = pos[1];

		Theme.card(ctx, x, y, w, h);
		float cx = x + 4;
		for (int slot : SLOTS) {
			ItemStack stack = mc.thePlayer.inventory.armorInventory[slot];
			if (stack == null) {
				Render2D.rect(ctx, cx + 2, y + 4, 16, 16, 4, 0x14FFFFFF);
			} else {
				ctx.draw();
				ctx.drawItem(stack, (int) cx + 2, (int) y + 4);
				if (stack.isItemStackDamageable()) {
					float left = 1f - stack.getItemDamage() / (float) stack.getMaxDamage();
					int color = ColorUtil.lerp(0xFFFF5C6C, 0xFF5CFF8A, left);
					Render2D.rect(ctx, cx + 3, y + 21, 14, 1.5f, 0.75f, 0x40FFFFFF);
					Render2D.rect(ctx, cx + 3, y + 21, 14 * left, 1.5f, 0.75f, color);
					if (percent.get()) {
						String text = Math.round(left * 100) + "%";
						Render2D.scaledText(ctx, text, cx + 10 - Render2D.textWidth(text) * 0.6f / 2, y + 24.5f, 0.6f, color);
					}
				}
			}
			cx += CELL;
		}
	}
}
