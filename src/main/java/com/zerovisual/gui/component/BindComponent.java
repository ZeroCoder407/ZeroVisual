package com.zerovisual.gui.component;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.Module;
import com.zerovisual.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Click, then press a key. Esc / Delete / Backspace removes the bind. */
public class BindComponent extends Component {
	private final Module module;
	private boolean listening;

	public BindComponent(Module module) {
		this.module = module;
	}

	@Override
	public float getHeight() {
		return 14;
	}

	@Override
	protected void draw(DrawContext ctx, double mouseX, double mouseY) {
		boolean hovered = isHovered(mouseX, mouseY);
		Render2D.text(ctx, "Bind", x, y + 3, hovered ? Theme.TEXT_MUTED : Theme.TEXT_DIM);

		String value = listening ? "..." : keyName(module.getKey());
		float vw = Render2D.textWidth(value);
		float bx = x + width - vw - 8;
		Render2D.rect(ctx, bx, y + 1, vw + 8, 12, 4, Theme.TRACK);
		if (listening) Render2D.outline(ctx, bx, y + 1, vw + 8, 12, 4, 1, Theme.accent());
		Render2D.text(ctx, value, bx + 4, y + 3, listening ? Theme.accentLight() : Theme.TEXT);
	}

	public static String keyName(int key) {
		if (key == GLFW.GLFW_KEY_UNKNOWN) return "None";
		return InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
	}

	@Override
	public boolean isListening() {
		return listening;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (isHovered(mouseX, mouseY) && button == 0) {
			listening = !listening;
			return true;
		}
		listening = false;
		return false;
	}

	@Override
	public boolean keyPressed(int key) {
		if (!listening) return false;
		if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
			module.setKey(GLFW.GLFW_KEY_UNKNOWN);
		} else {
			module.setKey(key);
		}
		listening = false;
		return true;
	}
}
