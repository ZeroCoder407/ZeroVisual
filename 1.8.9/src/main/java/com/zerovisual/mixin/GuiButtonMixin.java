package com.zerovisual.mixin;

import com.zerovisual.gui.ButtonStyle;
import com.zerovisual.render.DrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.gui.GuiSlider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dark rounded look for vanilla buttons (MainMenu: Rounded buttons). Sliders keep the vanilla look of their knob. */
@Mixin(GuiButton.class)
public abstract class GuiButtonMixin {
	@Shadow
	public int xPosition;
	@Shadow
	public int yPosition;
	@Shadow
	protected int width;
	@Shadow
	protected int height;
	@Shadow
	public String displayString;
	@Shadow
	public boolean enabled;
	@Shadow
	public boolean visible;
	@Shadow
	protected boolean hovered;

	@Shadow
	protected abstract void mouseDragged(Minecraft mc, int mouseX, int mouseY);

	@Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
	private void zerovisual$rounded(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
		Object self = this;
		if (!visible || !ButtonStyle.isEnabled() || self instanceof GuiOptionSlider || self instanceof GuiSlider) return;
		hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
		DrawContext ctx = new DrawContext();
		ButtonStyle.drawButton(ctx, this, xPosition, yPosition, width, height, enabled, hovered, 1f);
		mouseDragged(mc, mouseX, mouseY);
		ButtonStyle.drawLabel(ctx, this, displayString, xPosition, yPosition, width, height, enabled, hovered, 1f);
		ci.cancel();
	}
}
