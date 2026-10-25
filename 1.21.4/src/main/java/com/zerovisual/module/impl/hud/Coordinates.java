package com.zerovisual.module.impl.hud;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

public class Coordinates extends HudModule {
	public final BooleanSetting netherCoords = add(new BooleanSetting("Nether coords", true));

	public Coordinates() {
		super("Coordinates", "Player coordinates and facing");
	}

	@Override
	public boolean isDraggable() {
		return true;
	}

	@Override
	protected float[] defaultPosition(float screenW, float screenH, float w, float h) {
		return new float[]{6, screenH - h - 6};
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		if (mc.player == null || mc.world == null) return;

		int x = (int) Math.floor(mc.player.getX());
		int y = (int) Math.floor(mc.player.getY());
		int z = (int) Math.floor(mc.player.getZ());
		String main = x + "  " + y + "  " + z;
		String facing = mc.player.getHorizontalFacing().asString().toUpperCase();

		String extra = null;
		if (netherCoords.get()) {
			if (mc.world.getRegistryKey() == World.NETHER) extra = "OW  " + (x * 8) + "  " + (z * 8);
			else if (mc.world.getRegistryKey() == World.OVERWORLD) extra = "NE  " + (x / 8) + "  " + (z / 8);
		}

		float h = extra != null ? 30 : 18;
		float w = 8 + Render2D.textWidth("XYZ  ") + Math.max(Render2D.textWidth(main + "   " + facing),
			extra != null ? Render2D.textWidth(extra) - Render2D.textWidth("XYZ  ") + 4 : 0) + 8;
		float[] pos = place(ctx, w, h);
		float left = pos[0];
		float top = pos[1];

		Theme.card(ctx, left, top, w, h);
		int accent = Theme.accent();
		Render2D.text(ctx, "XYZ", left + 8, top + 5, accent);
		float vx = left + 8 + Render2D.textWidth("XYZ  ");
		Render2D.text(ctx, main, vx, top + 5, Theme.TEXT);
		Render2D.text(ctx, facing, vx + Render2D.textWidth(main + "   "), top + 5, Theme.TEXT_DIM);
		if (extra != null) Render2D.text(ctx, extra, left + 8, top + 17, Theme.TEXT_MUTED);
	}
}
