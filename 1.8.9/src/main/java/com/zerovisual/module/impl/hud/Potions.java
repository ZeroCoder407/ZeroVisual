package com.zerovisual.module.impl.hud;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.util.Lang;
import com.zerovisual.render.DrawContext;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Card with active effects and their remaining time. */
public class Potions extends HudModule {
	private static final int ICON = 11;
	private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

	public final BooleanSetting icons = add(new BooleanSetting("Effect icons", true));

	public Potions() {
		super("Potions", "Active effects and their time");
		setEnabled(true);
	}

	@Override
	public boolean isDraggable() {
		return true;
	}

	@Override
	protected float[] defaultPosition(float screenW, float screenH, float w, float h) {
		// below InfoHUD when it is shown
		InfoHud info = ZeroVisualClient.get().modules().get(InfoHud.class);
		return new float[]{6, info.isEnabled() && info.isOnScreen() && !info.hasCustomPosition() ? info.getLastY() + info.getLastH() + 6 : 30};
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		if (mc.thePlayer == null) return;
		List<PotionEffect> effects = new ArrayList<PotionEffect>(mc.thePlayer.getActivePotionEffects());

		List<String[]> rows = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		List<Potion> kinds = new ArrayList<Potion>();
		float nameW = 0, timeW = 0;
		for (PotionEffect effect : effects) {
			Potion type = Potion.potionTypes[effect.getPotionID()];
			if (type == null) continue;
			int amp = effect.getAmplifier();
			String name = I18n.format(type.getName()) + (amp > 0 ? (amp < ROMAN.length ? ROMAN[amp] : " " + (amp + 1)) : "");
			String time = formatDuration(effect);
			rows.add(new String[]{name, time});
			colors.add(type.getLiquidColor() | 0xFF000000);
			kinds.add(type);
			nameW = Math.max(nameW, Render2D.textWidth(name));
			timeW = Math.max(timeW, Render2D.textWidth(time));
		}

		if (rows.isEmpty()) {
			// placeholder so the card can be positioned in the chat
			if (!isEditing()) return;
			rows.add(new String[]{Lang.get("zerovisual.hud.potions.empty"), ""});
			colors.add(Theme.TEXT_DIM);
			nameW = Render2D.textWidth(Lang.get("zerovisual.hud.potions.empty"));
		}

		boolean showIcons = icons.get() && !kinds.isEmpty();
		float rowH = showIcons ? ICON + 2 : 12;
		float textX = showIcons ? 8 + ICON : 16;
		float w = Math.max(90, textX + nameW + 14 + timeW + 8);
		float h = 20 + rows.size() * rowH + 4;
		float[] pos = place(ctx, w, h);
		float x = pos[0];
		float y = pos[1];

		Theme.card(ctx, x, y, w, h);
		int accent = Theme.accent();
		Render2D.circle(ctx, x + 10, y + 9, 2.5f, accent);
		Render2D.text(ctx, "Potions", x + 17, y + 5, Theme.TEXT);
		Render2D.rect(ctx, x + 6, y + 17, w - 12, 1, 0.5f, 0x20FFFFFF);

		float ry = y + 21;
		for (int i = 0; i < rows.size(); i++) {
			if (showIcons) icon(ctx, kinds.get(i), x + 5, ry - 2);
			else Render2D.circle(ctx, x + 10, ry + 3.5f, 2f, colors.get(i));
			Render2D.text(ctx, rows.get(i)[0], x + textX, ry, Theme.TEXT_MUTED);
			String time = rows.get(i)[1];
			Render2D.text(ctx, time, x + w - 8 - Render2D.textWidth(time), ry, Theme.TEXT_DIM);
			ry += rowH;
		}
	}

	private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");

	/** The 18 px effect icons of 1.8.9 are in the inventory texture, eight to a row. */
	private void icon(DrawContext ctx, Potion effect, float x, float y) {
		if (!effect.hasStatusIcon()) return;
		int index = effect.getStatusIconIndex();
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x, y, 0);
		ctx.getMatrices().scale(ICON / 18f, ICON / 18f, 1);
		ctx.drawTexture(INVENTORY, 0, 0, index % 8 * 18, 198 + index / 8 * 18, 18, 18, 256, 256);
		ctx.getMatrices().pop();
	}

	private static String formatDuration(PotionEffect effect) {
		if (effect.getIsPotionDurationMax()) return "∞";
		int seconds = effect.getDuration() / 20;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
