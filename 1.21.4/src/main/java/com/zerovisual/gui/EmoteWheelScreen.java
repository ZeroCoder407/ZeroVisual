package com.zerovisual.gui;

import com.zerovisual.emote.Emote;
import com.zerovisual.emote.EmoteManager;
import com.zerovisual.emote.Emotes;
import com.zerovisual.module.impl.cosmetics.EmoteMenu;
import com.zerovisual.render.Anim;
import com.zerovisual.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** The emote wheel. Stays open while the key is held; letting go (or a click) plays what the mouse points at. */
public class EmoteWheelScreen extends Screen {
	private static final int RADIUS_X = 136, RADIUS_Y = 88, TILE_W = 84, TILE_H = 24, CENTER = 30;

	/** DevScreenshots: the wheel stays open without the key. */
	public static boolean keepOpen;

	private final EmoteMenu menu;
	private final Anim open = new Anim(14f, 0f);
	private int hovered = -1;
	private boolean centerHovered;

	public EmoteWheelScreen(EmoteMenu menu) {
		super(Text.translatableWithFallback("zerovisual.emote.title", "Emotes"));
		this.menu = menu;
	}

	private static String label(Emote emote) {
		return Text.translatableWithFallback("zerovisual.emote." + emote.id(), emote.name()).getString();
	}

	private void updateHover(double mouseX, double mouseY) {
		double dx = mouseX - width / 2.0, dy = mouseY - height / 2.0;
		centerHovered = dx * dx + dy * dy < CENTER * CENTER;
		if (centerHovered) {
			hovered = -1;
			return;
		}
		int count = Emotes.ALL.size();
		double angle = Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / count;
		hovered = (int) Math.floor(angle / (2 * Math.PI) * count + count) % count;
	}

	private void select() {
		if (hovered >= 0) menu.play(Emotes.ALL.get(hovered));
		else if (centerHovered) menu.stop();
		close();
	}

	@Override
	public void tick() {
		if (!keepOpen && !EmoteMenu.isKeyHeld()) select();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		updateHover(mouseX, mouseY);
		select();
		return true;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, (int) (open.get() * 0x60) << 24);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		float k = open.update(1f);
		updateHover(mouseX, mouseY);
		float cx = width / 2f, cy = height / 2f;
		int count = Emotes.ALL.size();
		Render2D.setAlpha(k);
		for (int i = 0; i < count; i++) {
			double angle = 2 * Math.PI * i / count - Math.PI / 2;
			float x = cx + (float) Math.cos(angle) * RADIUS_X * k, y = cy + (float) Math.sin(angle) * RADIUS_Y * k;
			boolean on = i == hovered;
			float w = on ? TILE_W + 6 : TILE_W, h = on ? TILE_H + 4 : TILE_H;
			Render2D.shadow(ctx, x - w / 2, y - h / 2 + 2, w, h, 8, 10, 0x80000000);
			Render2D.rect(ctx, x - w / 2, y - h / 2, w, h, 8, on ? Theme.accent() : Theme.BACKGROUND);
			Render2D.outline(ctx, x - w / 2, y - h / 2, w, h, 8, 1, on ? 0x40FFFFFF : 0x14FFFFFF);
			Render2D.centeredText(ctx, label(Emotes.ALL.get(i)), x, y - 4, on ? 0xFFFFFFFF : Theme.TEXT);
		}
		Render2D.circle(ctx, cx, cy, CENTER, centerHovered ? Theme.accent() : Theme.BACKGROUND);
		boolean playing = client.player != null && EmoteManager.current(client.player.getUuid()) != null;
		String center = hovered >= 0 ? label(Emotes.ALL.get(hovered)) : playing
			? Text.translatableWithFallback("zerovisual.emote.stop", "Stop").getString() : title.getString();
		Render2D.setAlpha(1);
		Render2D.centeredText(ctx, Render2D.textWidth(center) > CENTER * 2 - 6 ? title.getString() : center, cx, cy - 4, Theme.TEXT);
	}
}
