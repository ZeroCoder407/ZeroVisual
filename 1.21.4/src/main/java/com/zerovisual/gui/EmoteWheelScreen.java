package com.zerovisual.gui;

import com.zerovisual.emote.Emote;
import com.zerovisual.emote.EmoteManager;
import com.zerovisual.emote.Emotes;
import com.zerovisual.module.impl.cosmetics.EmoteMenu;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** The emote wheel, in the manner of the weapon wheel of GTA: a translucent ring of sectors over the blurred game, the name in the middle. Stays open while the key is held; letting go (or a click) plays what the mouse points at. */
public class EmoteWheelScreen extends Screen {
	private static final float MAX_RADIUS = 120, RING = 0.62f, LINE = 0.004f, LABEL = 0.75f;
	private static final int SELECTED = 0xC05FA9BC;

	/** DevScreenshots: the wheel stays open without the key. */
	public static boolean keepOpen;
	/** DevScreenshots: the sector shown as pointed at. */
	public static int forcedHover = -1;

	private final EmoteMenu menu;
	private final Anim open = new Anim(14f, 0f);
	private final Anim[] lit = new Anim[Emotes.ALL.size()];
	private int hovered = -1;
	private boolean centerHovered;

	public EmoteWheelScreen(EmoteMenu menu) {
		super(Text.translatableWithFallback("zerovisual.emote.title", "Emotes"));
		this.menu = menu;
		for (int i = 0; i < lit.length; i++) lit[i] = new Anim(16f, 0f);
	}

	private static String label(Emote emote) {
		return Text.translatableWithFallback("zerovisual.emote." + emote.id(), emote.name()).getString();
	}

	private float outerRadius() {
		return Math.min(height / 2f - 10, MAX_RADIUS);
	}

	private float hubRadius() {
		return outerRadius() * 0.38f;
	}

	private void updateHover(double mouseX, double mouseY) {
		if (forcedHover >= 0) {
			hovered = forcedHover;
			centerHovered = false;
			return;
		}
		double dx = mouseX - width / 2.0, dy = mouseY - height / 2.0;
		centerHovered = dx * dx + dy * dy < hubRadius() * hubRadius();
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
		applyBlur();
		ctx.fill(0, 0, width, height, (int) (open.get() * 0x30) << 24);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		float k = open.update(1f);
		updateHover(mouseX, mouseY);
		float cx = width / 2f, cy = height / 2f;
		float grow = 0.86f + 0.14f * k;
		float outer = outerRadius() * grow, inner = outer * RING;
		int count = Emotes.ALL.size();
		Emote current = client.player != null ? EmoteManager.current(client.player.getUuid()) : null;
		Render2D.setAlpha(k);
		float half = (float) Math.PI / count;
		for (int i = 0; i < count; i++) {
			float on = lit[i].update(i == hovered ? 1f : 0f);
			float middle = 2 * half * i - (float) Math.PI / 2;
			Render2D.sector(ctx, cx, cy, inner, outer, middle - half, middle + half,
				ColorUtil.lerp(0x68585860, SELECTED, on), ColorUtil.lerp(0x90707078, SELECTED, on));
			Render2D.sector(ctx, cx, cy, inner, outer, middle + half - LINE, middle + half + LINE, 0x70FFFFFF, 0x70FFFFFF);
		}
		for (int i = 0; i < count; i++) {
			Emote emote = Emotes.ALL.get(i);
			float middle = 2 * half * i - (float) Math.PI / 2;
			// a thin line marks what is playing, the way the wheel of GTA marks the weapon in hand
			if (emote == current) Render2D.sector(ctx, cx, cy, outer - 2.5f, outer, middle - half + LINE, middle + half - LINE, Theme.accent(), Theme.accent());
			float x = cx + (float) Math.cos(middle) * (inner + outer) / 2, y = cy + (float) Math.sin(middle) * (inner + outer) / 2;
			drawLabel(ctx, label(emote), x, y, 0xFFFFFFFF);
		}
		if (hovered >= 0) {
			String name = label(Emotes.ALL.get(hovered));
			Render2D.text(ctx, name, cx - Render2D.textWidth(name) / 2f, cy - 13, 0xFFFFFFFF, true);
			String number = (hovered + 1) + " / " + count;
			float side = Render2D.textWidth(number) / 2f + 7;
			Render2D.text(ctx, number, cx - Render2D.textWidth(number) / 2f, cy + 3, 0xFFFFFFFF, true);
			Render2D.text(ctx, "<", cx - side - Render2D.textWidth("<"), cy + 3, SELECTED | 0xFF000000, true);
			Render2D.text(ctx, ">", cx + side, cy + 3, SELECTED | 0xFF000000, true);
		} else {
			String center = current != null ? Text.translatableWithFallback("zerovisual.emote.stop", "Stop").getString() : title.getString();
			Render2D.text(ctx, center, cx - Render2D.textWidth(center) / 2f, cy - 4, 0xFFFFFFFF, true);
		}
		Render2D.setAlpha(1);
	}

	/** Names are small and split at the last space: a sector is narrower than a whole name. */
	private static void drawLabel(DrawContext ctx, String text, float x, float y, int color) {
		int space = text.lastIndexOf(' ');
		if (space < 0) {
			Render2D.scaledText(ctx, text, x - Render2D.textWidth(text) * LABEL / 2, y - 4 * LABEL, LABEL, color);
			return;
		}
		String top = text.substring(0, space), bottom = text.substring(space + 1);
		Render2D.scaledText(ctx, top, x - Render2D.textWidth(top) * LABEL / 2, y - 9 * LABEL, LABEL, color);
		Render2D.scaledText(ctx, bottom, x - Render2D.textWidth(bottom) * LABEL / 2, y + LABEL, LABEL, color);
	}
}
