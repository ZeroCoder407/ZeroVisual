package com.zerovisual.gui;

import com.zerovisual.render.DrawContext;

/** A button of the menus, in the rounded style of ButtonStyle. */
public class Button extends ZvScreen.Widget {
	public interface Action {
		void press(Button button);
	}

	private String message;
	private final Action action;

	public Button(String message, float x, float y, float width, float height, Action action) {
		super(x, y, width, height);
		this.message = message;
		this.action = action;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY) {
		if (!visible) return;
		boolean hovered = active && isHovered(mouseX, mouseY);
		ButtonStyle.drawButton(ctx, this, x, y, width, height, active, hovered, 1f);
		ButtonStyle.drawLabel(ctx, this, message, x, y, width, height, active, hovered, 1f);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !active || !isHovered(mouseX, mouseY)) return false;
		net.minecraft.client.Minecraft.getMinecraft().getSoundHandler().playSound(
			net.minecraft.client.audio.PositionedSoundRecord.create(new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
		action.press(this);
		return true;
	}
}
