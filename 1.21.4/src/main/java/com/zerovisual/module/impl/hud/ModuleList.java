package com.zerovisual.module.impl.hud;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.HudModule;
import com.zerovisual.module.Module;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * List of enabled modules, sliding in and out.
 * Rows are aligned to the side of the screen the list is on.
 */
public class ModuleList extends HudModule {
	private static final float ROW_H = 13;
	private static final float GAP = 2;

	private final Map<Module, Anim> anims = new HashMap<>();

	public ModuleList() {
		super("ModuleList", "List of enabled modules");
		setEnabled(true);
	}

	@Override
	public boolean isDraggable() {
		return true;
	}

	@Override
	protected float[] defaultPosition(float screenW, float screenH, float w, float h) {
		return new float[]{screenW - w - 6, 6};
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		List<Module> sorted = ZeroVisualClient.get().modules().getModules().stream()
			.filter(m -> m.isToggleable() && m.getCategory() != Category.HUD)
			.sorted(Comparator.comparingInt((Module m) -> Render2D.textWidth(m.getName())).reversed())
			.toList();

		List<Module> rows = new ArrayList<>();
		List<Float> progress = new ArrayList<>();
		float maxW = 0, totalH = 0;
		for (Module module : sorted) {
			float t = anims.computeIfAbsent(module, m -> new Anim(12f, 0f)).update(module.isEnabled());
			if (t <= 0.01f) continue;
			rows.add(module);
			progress.add(t);
			maxW = Math.max(maxW, Render2D.textWidth(module.getName()) + 10);
			totalH += (ROW_H + GAP) * t;
		}

		boolean placeholder = rows.isEmpty();
		if (placeholder) {
			if (!isEditing()) return;
			maxW = Render2D.textWidth(getName()) + 10;
			totalH = ROW_H;
		}

		float[] pos = place(ctx, maxW, Math.max(ROW_H, totalH - GAP));
		float x = pos[0], y = pos[1];
		boolean rightAligned = x + maxW / 2 > ctx.getScaledWindowWidth() / 2f;
		int accent = Theme.accent();
		int accentLight = Theme.accentLight();

		if (placeholder) {
			Render2D.rect(ctx, x, y, maxW, ROW_H, 3, 0xD00D0D12);
			Render2D.text(ctx, getName(), x + 5, y + 3, Theme.TEXT_DIM);
			return;
		}

		for (int i = 0; i < rows.size(); i++) {
			Module module = rows.get(i);
			float t = progress.get(i);
			float w = Render2D.textWidth(module.getName()) + 10;
			// slide in from the screen edge the list is attached to
			float rx = rightAligned ? x + maxW - w * t : x - w * (1 - t);

			float prev = Render2D.getAlpha();
			Render2D.setAlpha(t);
			Render2D.rect(ctx, rx, y, w, ROW_H, 3, 0xD00D0D12);
			float barX = rightAligned ? rx + w - 2 : rx;
			Render2D.gradientV(ctx, barX, y + 2, 2, ROW_H - 4, 1, accentLight, accent);
			Render2D.text(ctx, module.getName(), rx + (rightAligned ? 4 : 6), y + 3, ColorUtil.lerp(Theme.TEXT, accentLight, 0.15f));
			Render2D.setAlpha(prev);

			y += (ROW_H + GAP) * t;
		}
	}
}
