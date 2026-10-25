package com.zerovisual.gui.component;

import com.zerovisual.gui.Theme;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;

public class BooleanComponent extends Component {
	private final BooleanSetting setting;
	private final Anim anim;

	public BooleanComponent(BooleanSetting setting) {
		this.setting = setting;
		this.anim = new Anim(16f, setting.get() ? 1f : 0f);
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
		float t = anim.update(setting.get());
		Render2D.text(ctx, setting.getName(), x, y + 3, ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT_MUTED, isHovered(mouseX, mouseY) ? 1 : 0));

		float sw = 16, sh = 8;
		float sx = x + width - sw;
		float sy = y + 3;
		Render2D.rect(ctx, sx, sy, sw, sh, sh / 2, ColorUtil.lerp(Theme.TRACK, Theme.accent(), t));
		Render2D.circle(ctx, sx + 4 + 8 * t, sy + sh / 2, 3f, ColorUtil.lerp(0xFF8A8A99, 0xFFFFFFFF, t));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isHovered(mouseX, mouseY)) {
			setting.toggle();
			return true;
		}
		return false;
	}
}
