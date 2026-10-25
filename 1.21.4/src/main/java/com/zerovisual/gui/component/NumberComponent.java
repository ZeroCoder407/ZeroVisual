package com.zerovisual.gui.component;

import com.zerovisual.gui.Theme;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

public class NumberComponent extends Component {
	private final NumberSetting setting;
	private final Anim progress;
	private boolean dragging;

	public NumberComponent(NumberSetting setting) {
		this.setting = setting;
		this.progress = new Anim(18f, (float) setting.getProgress());
	}

	@Override
	public float getHeight() {
		return 22;
	}

	@Override
	public boolean isVisible() {
		return setting.isVisible();
	}

	@Override
	protected void draw(DrawContext ctx, double mouseX, double mouseY) {
		if (dragging) setting.setProgress((mouseX - x) / width);
		float p = progress.update((float) setting.getProgress());
		boolean active = dragging || isHovered(mouseX, mouseY);

		Render2D.text(ctx, setting.getName(), x, y + 2, active ? Theme.TEXT_MUTED : Theme.TEXT_DIM);
		String value = setting.format();
		Render2D.text(ctx, value, x + width - Render2D.textWidth(value), y + 2, Theme.TEXT);

		float ty = y + 15;
		float th = 3;
		Render2D.rect(ctx, x, ty, width, th, th / 2, Theme.TRACK);
		float fill = Math.max(th, width * p);
		Render2D.gradientH(ctx, x, ty, fill, th, th / 2, Theme.accent(), Theme.accentLight());

		float kx = x + width * p;
		if (active) Render2D.shadow(ctx, kx - 4, ty + th / 2 - 4, 8, 8, 4, 4, ColorUtil.withAlpha(Theme.accent(), 0.6f));
		Render2D.circle(ctx, kx, ty + th / 2, 3.5f, 0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isHovered(mouseX, mouseY)) {
			dragging = true;
			setting.setProgress((mouseX - x) / width);
			return true;
		}
		return false;
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
	}
}
