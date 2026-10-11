package com.zerovisual.module.impl.hud;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;

/** Card with active effects and their remaining time. */
public class Potions extends HudModule {
	private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

	public final BooleanSetting hideVanilla = add(new BooleanSetting("Hide vanilla icons", true));

	public Potions() {
		super("Potions", "Активные эффекты и их время");
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
		if (mc.player == null) return;
		List<StatusEffectInstance> effects = new ArrayList<>(mc.player.getStatusEffects());

		List<String[]> rows = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		float nameW = 0, timeW = 0;
		for (StatusEffectInstance effect : effects) {
			StatusEffect type = effect.getEffectType().value();
			int amp = effect.getAmplifier();
			String name = type.getName().getString() + (amp > 0 ? (amp < ROMAN.length ? ROMAN[amp] : " " + (amp + 1)) : "");
			String time = formatDuration(effect);
			rows.add(new String[]{name, time});
			colors.add(type.getColor() | 0xFF000000);
			nameW = Math.max(nameW, Render2D.textWidth(name));
			timeW = Math.max(timeW, Render2D.textWidth(time));
		}

		if (rows.isEmpty()) {
			// placeholder so the card can be positioned in the chat
			if (!isEditing()) return;
			rows.add(new String[]{"Нет эффектов", ""});
			colors.add(Theme.TEXT_DIM);
			nameW = Render2D.textWidth("Нет эффектов");
		}

		float rowH = 12;
		float w = Math.max(90, 8 + 8 + nameW + 14 + timeW + 8);
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
			Render2D.circle(ctx, x + 10, ry + 3.5f, 2f, colors.get(i));
			Render2D.text(ctx, rows.get(i)[0], x + 16, ry, Theme.TEXT_MUTED);
			String time = rows.get(i)[1];
			Render2D.text(ctx, time, x + w - 8 - Render2D.textWidth(time), ry, Theme.TEXT_DIM);
			ry += rowH;
		}
	}

	private static String formatDuration(StatusEffectInstance effect) {
		if (effect.isInfinite()) return "∞";
		int seconds = effect.getDuration() / 20;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
