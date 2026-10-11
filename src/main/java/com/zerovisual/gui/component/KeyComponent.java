package com.zerovisual.gui.component;

import com.zerovisual.gui.Theme;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.KeySetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

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
	protected void draw(DrawContext ctx, double mouseX, double mouseY) {
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
		if (listening && button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			setting.setKey(InputUtil.Type.MOUSE.createFromCode(button));
			listening = false;
			return true;
		}
		if (isHovered(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
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
		if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
			setting.setKey(InputUtil.UNKNOWN_KEY);
		} else {
			setting.setKey(InputUtil.Type.KEYSYM.createFromCode(key));
		}
		listening = false;
		return true;
	}
}
