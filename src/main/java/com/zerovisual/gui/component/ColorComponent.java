package com.zerovisual.gui.component;

import com.zerovisual.gui.Theme;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

/** Name, color preview and a draggable hue bar. */
public class ColorComponent extends Component {
	private static final int SEGMENTS = 6;

	private final ColorSetting setting;
	private boolean dragging;

	public ColorComponent(ColorSetting setting) {
		this.setting = setting;
	}

	@Override
	public float getHeight() {
		return 26;
	}

	@Override
	public boolean isVisible() {
		return setting.isVisible();
	}

	@Override
	protected void draw(DrawContext ctx, double mouseX, double mouseY) {
		if (dragging) applyHue(mouseX);

		Render2D.text(ctx, setting.getName(), x, y + 2, isHovered(mouseX, mouseY) ? Theme.TEXT_MUTED : Theme.TEXT_DIM);
		Render2D.circle(ctx, x + width - 5, y + 5.5f, 4.5f, 0xFFFFFFFF);
		Render2D.circle(ctx, x + width - 5, y + 5.5f, 3.5f, setting.get() | 0xFF000000);

		float by = y + 16;
		float bh = 5;
		float segW = width / SEGMENTS;
		for (int i = 0; i < SEGMENTS; i++) {
			int from = Color.HSBtoRGB(i / (float) SEGMENTS, 0.85f, 1f);
			int to = Color.HSBtoRGB((i + 1) / (float) SEGMENTS, 0.85f, 1f);
			// +0.5 overlap hides seams between segments
			Render2D.gradientH(ctx, x + segW * i, by, segW + 0.5f, bh, 0, from | 0xFF000000, to | 0xFF000000);
		}

		float hx = x + setting.getHsb()[0] * width;
		Render2D.outline(ctx, hx - 2.5f, by - 1.5f, 5, bh + 3, 2, 1.2f, 0xFFFFFFFF);
	}

	private void applyHue(double mouseX) {
		float hue = (float) Math.max(0, Math.min(0.999, (mouseX - x) / width));
		float[] hsb = setting.getHsb();
		setting.setHsb(hue, Math.max(hsb[1], 0.55f), Math.max(hsb[2], 0.8f));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isHovered(mouseX, mouseY)) {
			dragging = true;
			applyHue(mouseX);
			return true;
		}
		return false;
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
	}
}
