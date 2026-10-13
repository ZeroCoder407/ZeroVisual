package com.zerovisual.gui;

import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Uuids;

/** Pill in the top left corner of the title screen: current nickname, opens the account manager. */
public class AccountButton extends ClickableWidget {
	private final Screen parent;
	private final Anim hover = new Anim(18f, 0f);

	public AccountButton(Screen parent, int x, int y) {
		super(x, y, 120, 22, Text.literal("Accounts"));
		this.parent = parent;
	}

	@Override
	protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
		String name = MinecraftClient.getInstance().getSession().getUsername();
		String hint = I18n.translate("zerovisual.account.switch");
		setWidth(36 + Render2D.textWidth(name) + 8 + Render2D.textWidth(hint) + 10);

		float hv = hover.update(isHovered());
		float x = getX(), y = getY(), w = getWidth(), h = getHeight();
		Render2D.shadow(ctx, x, y + 2, w, h, 8, 8, 0x80000000);
		Render2D.rect(ctx, x, y, w, h, 8, ColorUtil.lerp(Theme.BACKGROUND, 0xF0181822, hv));
		Render2D.outline(ctx, x, y, w, h, 8, 1, ColorUtil.lerp(0x18FFFFFF, Theme.accent(), hv));

		ctx.draw();
		PlayerSkinDrawer.draw(ctx, DefaultSkinHelper.getSkinTextures(Uuids.getOfflinePlayerUuid(name)), getX() + 5, getY() + 4, 14);
		Render2D.text(ctx, name, x + 25, y + 7, Theme.TEXT);
		Render2D.text(ctx, hint, x + 25 + Render2D.textWidth(name) + 8, y + 7, ColorUtil.lerp(Theme.TEXT_DIM, Theme.accentLight(), hv));
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		MinecraftClient.getInstance().setScreen(new AccountScreen(parent));
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		appendDefaultNarrations(builder);
	}
}
