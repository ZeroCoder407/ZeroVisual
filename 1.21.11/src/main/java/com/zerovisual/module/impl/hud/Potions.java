package com.zerovisual.module.impl.hud;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/** Card with active effects and their remaining time. */
public class Potions extends HudModule {
	private static final int ICON = 11;
	private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

	public final BooleanSetting hideVanilla = add(new BooleanSetting("Hide vanilla icons", true));
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
	public void render(GuiGraphics ctx, float tickDelta) {
		if (mc.player == null) return;
		List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());

		List<String[]> rows = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		List<net.minecraft.core.Holder<MobEffect>> kinds = new ArrayList<>();
		float nameW = 0, timeW = 0;
		for (MobEffectInstance effect : effects) {
			MobEffect type = effect.getEffect().value();
			int amp = effect.getAmplifier();
			String name = type.getDisplayName().getString() + (amp > 0 ? (amp < ROMAN.length ? ROMAN[amp] : " " + (amp + 1)) : "");
			String time = formatDuration(effect);
			rows.add(new String[]{name, time});
			colors.add(type.getColor() | 0xFF000000);
			kinds.add(effect.getEffect());
			nameW = Math.max(nameW, Render2D.textWidth(name));
			timeW = Math.max(timeW, Render2D.textWidth(time));
		}

		if (rows.isEmpty()) {
			// placeholder so the card can be positioned in the chat
			if (!isEditing()) return;
			rows.add(new String[]{I18n.get("zerovisual.hud.potions.empty"), ""});
			colors.add(Theme.TEXT_DIM);
			nameW = Render2D.textWidth(I18n.get("zerovisual.hud.potions.empty"));
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

	private void icon(GuiGraphics ctx, net.minecraft.core.Holder<MobEffect> effect, float x, float y) {
		ctx.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, net.minecraft.client.gui.Gui.getMobEffectSprite(effect), (int) x, (int) y, ICON, ICON);
	}

	private static String formatDuration(MobEffectInstance effect) {
		if (effect.isInfiniteDuration()) return "∞";
		int seconds = effect.getDuration() / 20;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
