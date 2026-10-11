package com.zerovisual.gui.component;

import com.zerovisual.gui.Theme;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.ModeSetting;
import net.minecraft.client.gui.DrawContext;

/** Left click: next mode, right click: previous mode. */
public class ModeComponent extends Component {
	private final ModeSetting setting;

	public ModeComponent(ModeSetting setting) {
		this.setting = setting;
	}

	@Override
	public float getHeight() {
		return 14;
	}

	@Override
	public boolean isVisible() {
		return setting.isVisible();
	}

	@Override
	protected void draw(DrawContext ctx, double mouseX, double mouseY) {
		boolean hovered = isHovered(mouseX, mouseY);
		Render2D.text(ctx, setting.getName(), x, y + 3, hovered ? Theme.TEXT_MUTED : Theme.TEXT_DIM);
		String value = setting.get();
		float vw = Render2D.textWidth(value);
		float bx = x + width - vw - 8;
		Render2D.rect(ctx, bx, y + 1, vw + 8, 12, 4, Theme.TRACK);
		Render2D.text(ctx, value, bx + 4, y + 3, Theme.accentLight());
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!isHovered(mouseX, mouseY) || (button != 0 && button != 1)) return false;
		setting.cycle(button == 0);
		return true;
	}
}
