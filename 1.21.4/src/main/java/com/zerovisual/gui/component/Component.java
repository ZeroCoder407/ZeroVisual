package com.zerovisual.gui.component;

import net.minecraft.client.gui.DrawContext;

/** An element inside an expanded module. Bounds are remembered from the last render for input handling. */
public abstract class Component {
	protected float x, y, width;

	public abstract float getHeight();

	public boolean isVisible() {
		return true;
	}

	public void render(DrawContext ctx, float x, float y, float width, double mouseX, double mouseY) {
		this.x = x;
		this.y = y;
		this.width = width;
		draw(ctx, mouseX, mouseY);
	}

	protected abstract void draw(DrawContext ctx, double mouseX, double mouseY);

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return false;
	}

	public void mouseReleased(double mouseX, double mouseY, int button) {
	}

	public void mouseDragged(double mouseX, double mouseY, int button) {
	}

	/** @return true if the key was consumed */
	public boolean keyPressed(int key) {
		return false;
	}

	/** Waiting for a key press (binds). While true the screen sends keys and mouse buttons here first. */
	public boolean isListening() {
		return false;
	}

	/** A mouse button pressed anywhere while listening. @return true if consumed */
	public boolean captureMouse(int button) {
		return false;
	}

	protected boolean isHovered(double mouseX, double mouseY) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + getHeight();
	}
}
