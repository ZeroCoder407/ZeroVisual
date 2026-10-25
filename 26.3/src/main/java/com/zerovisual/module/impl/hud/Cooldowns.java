package com.zerovisual.module.impl.hud;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.render.Render2D;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;

/** Items on cooldown (ender pearl, chorus fruit, wind charge...) with the remaining time. */
public class Cooldowns extends HudModule {
	private static final float W = 110;
	private static final float ROW = 18;

	/** First observed progress and time per cooldown group, to estimate the remaining seconds. */
	private final Map<Identifier, double[]> tracking = new HashMap<>();

	public Cooldowns() {
		super("Cooldowns", "Item cooldowns: ender pearl, chorus fruit and more");
		setEnabled(true);
	}

	@Override
	public boolean isDraggable() {
		return true;
	}

	@Override
	protected float[] defaultPosition(float screenW, float screenH, float w, float h) {
		return new float[]{6, screenH / 2f - h / 2};
	}

	@Override
	public void render(GuiGraphicsExtractor ctx, float tickDelta) {
		if (mc.player == null) return;
		ItemCooldowns cooldowns = mc.player.getCooldowns();
		Inventory inventory = mc.player.getInventory();

		Map<Identifier, ItemStack> active = new LinkedHashMap<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) continue;
			Identifier group = cooldowns.getCooldownGroup(stack);
			if (!active.containsKey(group) && cooldowns.getCooldownPercent(stack, tickDelta) > 0) active.put(group, stack);
		}
		tracking.keySet().retainAll(active.keySet());
		if (active.isEmpty() && !isEditing()) return;

		float h = 20 + Math.max(1, active.size()) * ROW + 2;
		float[] pos = place(ctx, W, h);
		float x = pos[0], y = pos[1];

		Theme.card(ctx, x, y, W, h);
		Render2D.circle(ctx, x + 10, y + 9, 2.5f, Theme.accent());
		Render2D.text(ctx, "Cooldowns", x + 17, y + 5, Theme.TEXT);
		Render2D.rect(ctx, x + 6, y + 17, W - 12, 1, 0.5f, 0x20FFFFFF);

		if (active.isEmpty()) {
			Render2D.text(ctx, I18n.get("zerovisual.hud.cooldowns.empty"), x + 8, y + 24, Theme.TEXT_DIM);
			return;
		}

		float ry = y + 20;
		long now = System.currentTimeMillis();
		for (Map.Entry<Identifier, ItemStack> entry : active.entrySet()) {
			ItemStack stack = entry.getValue();
			float progress = cooldowns.getCooldownPercent(stack, tickDelta);
			ctx.pose().pushMatrix();
			ctx.pose().translate(x + 6, ry + 1);
			ctx.pose().scale(0.875f, 0.875f);
			ctx.item(stack, 0, 0);
			ctx.pose().popMatrix();

			String time = remaining(entry.getKey(), progress, now);
			Render2D.text(ctx, time, x + W - 8 - Render2D.textWidth(time), ry + 4, Theme.TEXT_MUTED);

			float barX = x + 24, barW = W - 32 - Render2D.textWidth(time) - 6;
			Render2D.rect(ctx, barX, ry + 7, barW, 3, 1.5f, Theme.TRACK);
			Render2D.gradientH(ctx, barX, ry + 7, Math.max(3, barW * progress), 3, 1.5f, Theme.accent(), Theme.accentLight());
			ry += ROW;
		}
	}

	private String remaining(Identifier group, float progress, long now) {
		double[] first = tracking.get(group);
		// new cooldown, or the same item used again (progress went back up)
		if (first == null || progress > first[0] + 0.01) {
			first = new double[]{progress, now};
			tracking.put(group, first);
		}
		double spent = first[0] - progress;
		if (spent < 0.02) return "...";
		double totalMs = (now - first[1]) / spent;
		return String.format(Locale.ROOT, "%.1fs", progress * totalMs / 1000.0);
	}
}
