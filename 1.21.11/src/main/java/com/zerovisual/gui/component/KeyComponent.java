package com.zerovisual.gui.component;

import com.mojang.blaze3d.platform.InputConstants;
import com.zerovisual.gui.Theme;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.KeySetting;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Click, then press a key or a mouse button (right, middle, side buttons).
 * Esc / Delete / Backspace removes the key.
 */
public class KeyComponent extends Component {
	private final KeySetting setting;
	private boolean listening;

	public KeyComponent(KeySetting setting) {
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
	protected void draw(GuiGraphics ctx, double mouseX, double mouseY) {
		boolean hovered = isHovered(mouseX, mouseY);
		Render2D.text(ctx, setting.getName(), x, y + 3, hovered ? Theme.TEXT_MUTED : Theme.TEXT_DIM);

		String value = listening ? "..." : setting.displayName();
		float vw = Render2D.textWidth(value);
		float bx = x + width - vw - 8;
		Render2D.rect(ctx, bx, y + 1, vw + 8, 12, 4, Theme.TRACK);
		if (listening) Render2D.outline(ctx, bx, y + 1, vw + 8, 12, 4, 1, Theme.accent());
		Render2D.text(ctx, value, bx + 4, y + 3, listening ? Theme.accentLight() : Theme.TEXT);
	}

	@Override
	public boolean isListening() {
		return listening;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (listening && button != InputConstants.MOUSE_BUTTON_LEFT) {
			setting.setKey(InputConstants.Type.MOUSE.getOrCreate(button));
			listening = false;
			return true;
		}
		if (isHovered(mouseX, mouseY) && button == InputConstants.MOUSE_BUTTON_LEFT) {
			listening = !listening;
			return true;
		}
		listening = false;
		return false;
	}

	@Override
	public boolean captureMouse(int button) {
		return listening && mouseClicked(x, y, button);
	}

	@Override
	public boolean keyPressed(int key) {
		if (!listening) return false;
		if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) {
			setting.setKey(InputConstants.UNKNOWN);
		} else {
			setting.setKey(InputConstants.Type.KEYSYM.getOrCreate(key));
		}
		listening = false;
		return true;
	}
}
