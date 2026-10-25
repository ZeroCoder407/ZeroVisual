package com.zerovisual.gui;

import com.zerovisual.gui.component.BindComponent;
import com.zerovisual.gui.component.BooleanComponent;
import com.zerovisual.gui.component.ColorComponent;
import com.zerovisual.gui.component.KeyComponent;
import com.zerovisual.setting.KeySetting;
import com.zerovisual.gui.component.Component;
import com.zerovisual.gui.component.ModeComponent;
import com.zerovisual.gui.component.NumberComponent;
import com.zerovisual.module.Module;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.setting.Setting;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** One module row; right click (or left click on action modules) expands its settings. */
public class ModuleButton {
	static final float ROW = 18;

	private final Module module;
	private final List<Component> components = new ArrayList<>();
	private final Anim hover = new Anim(18f, 0f);
	private final Anim enabled;
	private final Anim expand = new Anim(14f, 0f);
	private boolean expanded;
	private float x, y, width;

	public ModuleButton(Module module) {
		this.module = module;
		this.enabled = new Anim(14f, module.isEnabled() ? 1f : 0f);
		for (Setting<?> setting : module.getSettings()) {
			if (setting instanceof BooleanSetting s) components.add(new BooleanComponent(s));
			else if (setting instanceof NumberSetting s) components.add(new NumberComponent(s));
			else if (setting instanceof ModeSetting s) components.add(new ModeComponent(s));
			else if (setting instanceof ColorSetting s) components.add(new ColorComponent(s));
			else if (setting instanceof KeySetting s) components.add(new KeyComponent(s));
		}
		components.add(new BindComponent(module));
	}

	public Module getModule() {
		return module;
	}

	private float settingsHeight() {
		float h = 6;
		for (Component c : components) {
			if (c.isVisible()) h += c.getHeight() + 2;
		}
		return h + 2;
	}

	public float getHeight() {
		return ROW + settingsHeight() * expand.get();
	}

	public boolean isRowHovered(double mx, double my) {
		return mx >= x + 4 && mx <= x + width - 4 && my >= y && my <= y + ROW;
	}

	public void render(DrawContext ctx, float x, float y, float width, double mx, double my) {
		this.x = x;
		this.y = y;
		this.width = width;

		int accent = Theme.accent();
		float hv = hover.update(isRowHovered(mx, my));
		float on = enabled.update(module.isEnabled());
		float ex = expand.update(expanded);

		float rx = x + 5, rw = width - 10;
		if (on > 0.01f) {
			Render2D.gradientH(ctx, rx, y + 1, rw, ROW - 2, 5,
				ColorUtil.withAlpha(accent, 0.30f * on), ColorUtil.withAlpha(accent, 0.04f * on));
			Render2D.rect(ctx, rx + 3, y + 5, 2, ROW - 10, 1, ColorUtil.withAlpha(Theme.accentLight(), on));
		}
		if (hv > 0.01f) Render2D.rect(ctx, rx, y + 1, rw, ROW - 2, 5, ColorUtil.withAlpha(0xFFFFFF, 0.05f * hv));

		int base = ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT_MUTED, hv);
		Render2D.text(ctx, module.getName(), rx + 7 + 3 * on, y + 5, ColorUtil.lerp(base, Theme.TEXT, on));

		// "more" dots, lit while expanded
		int dots = ColorUtil.lerp(ColorUtil.lerp(0xFF4A4A57, Theme.TEXT_DIM, hv), Theme.accentLight(), ex);
		for (int i = 0; i < 3; i++) Render2D.circle(ctx, rx + rw - 6, y + 5.5f + i * 3.5f, 0.9f, dots);

		if (ex > 0.01f) {
			float full = settingsHeight();
			float visible = full * ex;
			ctx.enableScissor((int) x, (int) (y + ROW), (int) (x + width), (int) Math.ceil(y + ROW + visible));
			Render2D.rect(ctx, rx, y + ROW, rw, full - 3, 5, 0xB008080C);
			float cy = y + ROW + 5;
			for (Component c : components) {
				if (!c.isVisible()) continue;
				c.render(ctx, rx + 6, cy, rw - 12, mx, my);
				cy += c.getHeight() + 2;
			}
			ctx.disableScissor();
		}
	}

	public boolean mouseClicked(double mx, double my, int button) {
		if (isRowHovered(mx, my)) {
			if (button == 0 && module.isToggleable()) module.toggle();
			else if (button == 1 || button == 0) expanded = !expanded;
			return true;
		}
		if (expanded && expand.get() > 0.9f) {
			boolean handled = false;
			for (Component c : components) {
				if (c.isVisible() && c.mouseClicked(mx, my, button)) handled = true;
			}
			return handled;
		}
		return false;
	}

	public void mouseReleased(double mx, double my, int button) {
		for (Component c : components) c.mouseReleased(mx, my, button);
	}

	public boolean keyPressed(int key) {
		for (Component c : components) {
			if (c.keyPressed(key)) return true;
		}
		return false;
	}

	public boolean isListening() {
		for (Component c : components) {
			if (c.isListening()) return true;
		}
		return false;
	}

	public boolean captureMouse(int button) {
		for (Component c : components) {
			if (c.captureMouse(button)) return true;
		}
		return false;
	}
}
