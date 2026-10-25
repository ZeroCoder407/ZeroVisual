package com.zerovisual.gui;

import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;

/** Pill in the top left corner of the title screen: current nickname, opens the account manager. */
public class AccountButton extends AbstractWidget {
	private final Screen parent;
	private final Anim hover = new Anim(18f, 0f);

	public AccountButton(Screen parent, int x, int y) {
		super(x, y, 120, 22, Component.literal("Accounts"));
		this.parent = parent;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		String name = Minecraft.getInstance().getUser().getName();
		String hint = I18n.get("zerovisual.account.switch");
		setWidth(36 + Render2D.textWidth(name) + 8 + Render2D.textWidth(hint) + 10);

		float hv = hover.update(isHovered());
		float x = getX(), y = getY(), w = getWidth(), h = getHeight();
		Render2D.shadow(ctx, x, y + 2, w, h, 8, 8, 0x80000000);
		Render2D.rect(ctx, x, y, w, h, 8, ColorUtil.lerp(Theme.BACKGROUND, 0xF0181822, hv));
		Render2D.outline(ctx, x, y, w, h, 8, 1, ColorUtil.lerp(0x18FFFFFF, Theme.accent(), hv));
		PlayerFaceExtractor.extractRenderState(ctx, DefaultPlayerSkin.get(UUIDUtil.createOfflinePlayerUUID(name)), getX() + 5, getY() + 4, 14);
		Render2D.text(ctx, name, x + 25, y + 7, Theme.TEXT);
		Render2D.text(ctx, hint, x + 25 + Render2D.textWidth(name) + 8, y + 7, ColorUtil.lerp(Theme.TEXT_DIM, Theme.accentLight(), hv));
	}

	@Override
	public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		Minecraft.getInstance().gui.setScreen(new AccountScreen(parent));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput builder) {
		defaultButtonNarrationText(builder);
	}
}
