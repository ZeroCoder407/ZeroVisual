package com.zerovisual.gui;

import com.zerovisual.render.DrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/**
 * A 1.8.9 screen with the input and render methods of the newer versions' Screen, so the menus are shared code:
 * fractional mouse coordinates, separate key and char events, drag and scroll events, GLFW-style modifier bits.
 */
public abstract class ZvScreen extends GuiScreen {
	public static final int MOD_SHIFT = 1, MOD_CONTROL = 2, MOD_ALT = 4;

	protected final Minecraft client = Minecraft.getMinecraft();
	private final List<Widget> widgets = new ArrayList<Widget>();
	private int heldButton = -1;
	private double lastX, lastY;

	@Override
	public final void initGui() {
		widgets.clear();
		Keyboard.enableRepeatEvents(true);
		init();
	}

	protected void init() {
	}

	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
		removed();
	}

	/** Every client tick while the screen is open. */
	public void tick() {
	}

	@Override
	public final void updateScreen() {
		tick();
	}

	/** Called when the screen is left. */
	protected void removed() {
	}

	@Override
	public final void drawScreen(int mouseX, int mouseY, float partialTicks) {
		DrawContext ctx = new DrawContext();
		int mx = (int) mouseX(), my = (int) mouseY();
		renderBackground(ctx, mx, my, partialTicks);
		render(ctx, mx, my, partialTicks);
	}

	/** Draws the vanilla background: the dirt in menus, the dark gradient over a world. */
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		drawDefaultBackground();
	}

	/** The screen content; the default draws the widgets. */
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		for (Widget widget : widgets) widget.render(ctx, mouseX, mouseY);
	}

	protected <W extends Widget> W addWidget(W widget) {
		widgets.add(widget);
		return widget;
	}

	protected List<Widget> widgets() {
		return widgets;
	}

	public double mouseX() {
		return Mouse.getX() * width / (double) client.displayWidth;
	}

	public double mouseY() {
		return height - Mouse.getY() * height / (double) client.displayHeight - 1;
	}

	@Override
	public void handleMouseInput() {
		double mx = Mouse.getEventX() * width / (double) client.displayWidth;
		double my = height - Mouse.getEventY() * height / (double) client.displayHeight - 1;
		int button = Mouse.getEventButton();
		if (button >= 0) {
			if (Mouse.getEventButtonState()) {
				heldButton = button;
				lastX = mx;
				lastY = my;
				mouseClicked(mx, my, button);
			} else {
				if (heldButton == button) heldButton = -1;
				mouseReleased(mx, my, button);
			}
		} else if (heldButton >= 0 && (mx != lastX || my != lastY)) {
			mouseDragged(mx, my, heldButton, mx - lastX, my - lastY);
			lastX = mx;
			lastY = my;
		}
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) mouseScrolled(mx, my, 0, Math.signum(wheel));
	}

	@Override
	public void handleKeyboardInput() {
		int key = Keyboard.getEventKey();
		char c = Keyboard.getEventCharacter();
		if (Keyboard.getEventKeyState()) {
			int mods = (isShiftKeyDown() ? MOD_SHIFT : 0) | (isCtrlKeyDown() ? MOD_CONTROL : 0)
				| (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU) ? MOD_ALT : 0);
			boolean used = key != Keyboard.KEY_NONE && keyPressed(key, 0, mods);
			if (!used && c >= ' ') charTyped(c, mods);
		}
		client.dispatchKeypresses();
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (int i = widgets.size() - 1; i >= 0; i--) {
			if (widgets.get(i).mouseClicked(mouseX, mouseY, button)) return true;
		}
		return false;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		return false;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		return false;
	}

	/** An LWJGL key code; Esc closes the screen by default. */
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		for (Widget widget : widgets) {
			if (widget.keyPressed(keyCode, modifiers)) return true;
		}
		if (keyCode == Keyboard.KEY_ESCAPE && shouldCloseOnEsc()) {
			close();
			return true;
		}
		return false;
	}

	public boolean charTyped(char chr, int modifiers) {
		for (Widget widget : widgets) {
			if (widget.charTyped(chr)) return true;
		}
		return false;
	}

	public boolean shouldCloseOnEsc() {
		return true;
	}

	public boolean shouldPause() {
		return true;
	}

	@Override
	public final boolean doesGuiPauseGame() {
		return shouldPause();
	}

	public void close() {
		client.displayGuiScreen(null);
	}

	/** A clickable element of a ZvScreen (buttons and text fields of the menus). */
	public abstract static class Widget {
		public float x, y, width, height;
		public boolean visible = true, active = true;

		protected Widget(float x, float y, float width, float height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}

		public abstract void render(DrawContext ctx, int mouseX, int mouseY);

		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			return false;
		}

		public boolean keyPressed(int key, int modifiers) {
			return false;
		}

		public boolean charTyped(char chr) {
			return false;
		}

		public boolean isHovered(double mouseX, double mouseY) {
			return visible && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}

		public void setPosition(float x, float y) {
			this.x = x;
			this.y = y;
		}
	}
}
