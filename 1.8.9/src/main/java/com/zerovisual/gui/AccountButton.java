package com.zerovisual.gui;

import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.DrawContext;
import com.zerovisual.render.Render2D;
import com.zerovisual.util.Lang;
import com.zerovisual.util.Skins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** The current nickname in the corner of the title screen; opens the account manager (ScreenHooks). */
public class AccountButton extends GuiButton {
	public static final int ID = 0x7B10;

	private final Anim hover = new Anim(18f, 0f);

	public AccountButton(int x, int y) {
		super(ID, x, y, 120, 22, "Accounts");
	}

	static UUID offlineUuid(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public void drawButton(Minecraft mc, int mouseX, int mouseY) {
		if (!visible) return;
		DrawContext ctx = new DrawContext();
		String name = mc.getSession().getUsername();
		String hint = Lang.get("zerovisual.account.switch");
		width = 36 + Render2D.textWidth(name) + 8 + Render2D.textWidth(hint) + 10;
		hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;

		float hv = hover.update(hovered);
		float x = xPosition, y = yPosition, w = width, h = height;
		Render2D.shadow(ctx, x, y + 2, w, h, 8, 8, 0x80000000);
		Render2D.rect(ctx, x, y, w, h, 8, ColorUtil.lerp(Theme.BACKGROUND, 0xF0181822, hv));
		Render2D.outline(ctx, x, y, w, h, 8, 1, ColorUtil.lerp(0x18FFFFFF, Theme.accent(), hv));

		GlStateManager.color(1, 1, 1, 1);
		Skins.drawHead(DefaultPlayerSkin.getDefaultSkin(offlineUuid(name)), xPosition + 5, yPosition + 4, 14);
		Render2D.text(ctx, name, x + 25, y + 7, Theme.TEXT);
		Render2D.text(ctx, hint, x + 25 + Render2D.textWidth(name) + 8, y + 7, ColorUtil.lerp(Theme.TEXT_DIM, Theme.accentLight(), hv));
	}
}
