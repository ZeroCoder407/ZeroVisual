package com.zerovisual.module;

import com.zerovisual.gui.HudEditor;
import net.minecraft.client.gui.DrawContext;

/**
 * A module that draws an element on the in-game HUD.
 * Draggable elements remember their position as a fraction of the free screen space,
 * so they stay in place when the window or GUI scale changes.
 */
public abstract class HudModule extends Module {
	/** Normalized position 0..1, or -1 for the default position. */
	private float hudX = -1, hudY = -1;
	private float lastX, lastY, lastW, lastH;
	private long lastRenderTime;

	protected HudModule(String name, String description) {
		super(name, description, Category.HUD);
	}

	public abstract void render(DrawContext ctx, float tickDelta);

	/** Whether the element can be moved in the chat screen. */
	public boolean isDraggable() {
		return false;
	}

	/** Top-left corner used until the player moves the element. */
	protected float[] defaultPosition(float screenW, float screenH, float w, float h) {
		return new float[]{6, 6};
	}

	/** Resolves the element position for this frame and records its bounds for dragging. */
	protected float[] place(DrawContext ctx, float w, float h) {
		float sw = ctx.getScaledWindowWidth();
		float sh = ctx.getScaledWindowHeight();
		float x, y;
		if (hudX < 0) {
			float[] d = defaultPosition(sw, sh, w, h);
			x = d[0];
			y = d[1];
		} else {
			x = hudX * Math.max(0, sw - w);
			y = hudY * Math.max(0, sh - h);
		}
		x = Math.max(0, Math.min(x, sw - w));
		y = Math.max(0, Math.min(y, sh - h));
		lastX = x;
		lastY = y;
		lastW = w;
		lastH = h;
		lastRenderTime = System.currentTimeMillis();
		return new float[]{x, y};
	}

	/** Moves the element so its top-left corner is at (x, y) in scaled pixels. */
	public void moveTo(float x, float y, float screenW, float screenH) {
		float freeW = Math.max(1, screenW - lastW);
		float freeH = Math.max(1, screenH - lastH);
		hudX = Math.max(0, Math.min(1, x / freeW));
		hudY = Math.max(0, Math.min(1, y / freeH));
	}

	public void resetPosition() {
		hudX = -1;
		hudY = -1;
	}

	public boolean hasCustomPosition() {
		return hudX >= 0;
	}

	public float getHudX() {
		return hudX;
	}

	public float getHudY() {
		return hudY;
	}

	public void setHudPosition(float x, float y) {
		hudX = x;
		hudY = y;
	}

	/** True if the element was drawn in the last few frames (it can be hidden, e.g. TargetHUD without a target). */
	public boolean isOnScreen() {
		return System.currentTimeMillis() - lastRenderTime < 150;
	}

	public boolean contains(double mx, double my) {
		return mx >= lastX && mx <= lastX + lastW && my >= lastY && my <= lastY + lastH;
	}

	public float getLastX() {
		return lastX;
	}

	public float getLastY() {
		return lastY;
	}

	public float getLastW() {
		return lastW;
	}

	public float getLastH() {
		return lastH;
	}

	/** True while the chat is open and HUD elements can be dragged. */
	protected boolean isEditing() {
		return HudEditor.isEditing();
	}
}
