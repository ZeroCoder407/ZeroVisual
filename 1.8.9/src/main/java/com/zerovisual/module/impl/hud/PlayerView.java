package com.zerovisual.module.impl.hud;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.render.DrawContext;
import net.minecraft.client.gui.inventory.GuiInventory;

/** Your player model on the HUD, like in the inventory. */
public class PlayerView extends HudModule {
	private static final float W = 56;
	private static final float H = 86;

	public final BooleanSetting background = add(new BooleanSetting("Background", true));

	public PlayerView() {
		super("PlayerView", "Your character model on screen");
	}

	@Override
	public boolean isDraggable() {
		return true;
	}

	@Override
	protected float[] defaultPosition(float screenW, float screenH, float w, float h) {
		return new float[]{screenW - w - 6, screenH / 2f - h / 2 - 40};
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		if (mc.thePlayer == null) return;
		float[] pos = place(ctx, W, H);
		int x = Math.round(pos[0]), y = Math.round(pos[1]);
		if (background.get()) Theme.card(ctx, x, y, W, H);
		// the model looks slightly to the left, towards the screen center
		GuiInventory.drawEntityOnScreen(x + (int) W / 2, y + (int) H - 8, 36, 40, -10, mc.thePlayer);
		net.minecraft.client.renderer.GlStateManager.enableBlend();
	}
}
