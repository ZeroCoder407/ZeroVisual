package com.zerovisual.gui;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A draggable column with the modules of one category. Right click on the header collapses it. */
public class Panel {
	static final float WIDTH = 120;
	static final float HEADER = 26;

	final Category category;
	final PanelState state;
	private final List<ModuleButton> buttons = new ArrayList<>();
	private final Anim open;
	private float renderedHeight;
	private float scrollTarget;
	private final Anim scrollAnim = new Anim(16f, 0f);

	public Panel(Category category, List<Module> modules, PanelState state) {
		this.category = category;
		this.state = state;
		this.open = new Anim(12f, state.open ? 1f : 0f);
		for (Module module : modules) buttons.add(new ModuleButton(module));
	}

	private List<ModuleButton> visibleButtons(String search) {
		if (search.isEmpty()) return buttons;
		String query = search.toLowerCase(Locale.ROOT);
		return buttons.stream().filter(b -> b.getModule().getName().toLowerCase(Locale.ROOT).contains(query)).toList();
	}

	/** Scrolls the module list when it is taller than the space left on screen. */
	public void scroll(double amount) {
		scrollTarget -= (float) amount * 22;
	}

	public void render(DrawContext ctx, double mx, double my, String search, float screenHeight) {
		float x = state.x, y = state.y;
		List<ModuleButton> visible = visibleButtons(search);

		float content = 4;
		for (ModuleButton b : visible) content += b.getHeight();
		content += 4;
		// leave room for the search bar at the bottom of the screen
		float body = Math.min(content, Math.max(60, screenHeight - y - HEADER - 48));
		float maxScroll = Math.max(0, content - body);
		scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
		float s = scrollAnim.update(scrollTarget);

		float o = open.update(state.open);
		float total = HEADER + body * o;
		renderedHeight = total;

		int accent = Theme.accent();
		Render2D.shadow(ctx, x, y + 3, WIDTH, total, 9, 12, 0x90000000);
		Render2D.rect(ctx, x, y, WIDTH, total, 9, Theme.BACKGROUND);
		Render2D.outline(ctx, x, y, WIDTH, total, 9, 1, 0x14FFFFFF);

		// header
		Render2D.gradientV(ctx, x + 8, y + 7, 12, 12, 4, Theme.accentLight(), accent);
		Render2D.centeredText(ctx, category.getIcon(), x + 14.5f, y + 9, 0xFFFFFFFF);
		Render2D.text(ctx, category.getDisplayName(), x + 26, y + 9, Theme.TEXT);

		long enabled = buttons.stream().filter(b -> b.getModule().isToggleable() && b.getModule().isEnabled()).count();
		long toggleable = buttons.stream().filter(b -> b.getModule().isToggleable()).count();
		if (toggleable > 0) {
			String count = enabled + "/" + toggleable;
			Render2D.text(ctx, count, x + WIDTH - 10 - Render2D.textWidth(count), y + 9, Theme.TEXT_DIM);
		}

		if (o > 0.01f) {
			float lineY = y + HEADER - 1;
			Render2D.gradientH(ctx, x + 8, lineY, WIDTH / 2 - 8, 1, 0.5f, ColorUtil.withAlpha(accent, 0f), ColorUtil.withAlpha(accent, 0.7f * o));
			Render2D.gradientH(ctx, x + WIDTH / 2, lineY, WIDTH / 2 - 8, 1, 0.5f, ColorUtil.withAlpha(accent, 0.7f * o), ColorUtil.withAlpha(accent, 0f));

			ctx.enableScissor((int) x, (int) (y + HEADER), (int) (x + WIDTH), (int) Math.ceil(y + total));
			float cy = y + HEADER + 4 - s;
			// rows hidden by scrolling must not react to the mouse
			boolean mouseInBody = my > y + HEADER && my < y + total;
			for (ModuleButton b : visible) {
				b.render(ctx, x, cy, WIDTH, mx, mouseInBody ? my : -1000);
				cy += b.getHeight();
			}
			ctx.disableScissor();

			if (maxScroll > 0) {
				float trackY = y + HEADER + 4, trackH = body * o - 8;
				float thumbH = Math.max(14, trackH * body / content);
				float thumbY = trackY + (trackH - thumbH) * (s / maxScroll);
				Render2D.rect(ctx, x + WIDTH - 3.5f, thumbY, 2, thumbH, 1, ColorUtil.withAlpha(accent, 0.6f));
			}
		}
	}

	public boolean isHeaderHovered(double mx, double my) {
		return mx >= state.x && mx <= state.x + WIDTH && my >= state.y && my <= state.y + HEADER;
	}

	public boolean isHovered(double mx, double my) {
		return mx >= state.x && mx <= state.x + WIDTH && my >= state.y && my <= state.y + renderedHeight;
	}

	public boolean mouseClicked(double mx, double my, int button, String search) {
		if (isHeaderHovered(mx, my)) {
			if (button == 1) state.open = !state.open;
			return true;
		}
		if (!state.open || !isHovered(mx, my)) return false;
		if (my <= state.y + HEADER) return true;
		for (ModuleButton b : visibleButtons(search)) {
			if (b.mouseClicked(mx, my, button)) return true;
		}
		return true;
	}

	public void mouseReleased(double mx, double my, int button) {
		for (ModuleButton b : buttons) b.mouseReleased(mx, my, button);
	}

	public boolean keyPressed(int key) {
		for (ModuleButton b : buttons) {
			if (b.keyPressed(key)) return true;
		}
		return false;
	}

	public boolean isListening() {
		return buttons.stream().anyMatch(ModuleButton::isListening);
	}

	public boolean captureMouse(int button) {
		for (ModuleButton b : buttons) {
			if (b.captureMouse(button)) return true;
		}
		return false;
	}

	/** Module under the cursor, for the description line. */
	public Module getHoveredModule(double mx, double my, String search) {
		if (!state.open || !isHovered(mx, my) || my <= state.y + HEADER) return null;
		for (ModuleButton b : visibleButtons(search)) {
			if (b.isRowHovered(mx, my)) return b.getModule();
		}
		return null;
	}

	/** Position and collapsed state, persisted in the config. */
	public static class PanelState {
		public float x, y;
		public boolean open = true;

		public PanelState(float x, float y) {
			this.x = x;
			this.y = y;
		}
	}
}
