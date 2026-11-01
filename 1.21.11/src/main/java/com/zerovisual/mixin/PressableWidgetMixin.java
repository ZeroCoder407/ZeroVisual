package com.zerovisual.mixin;

import com.zerovisual.gui.ButtonStyle;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rounded dark buttons: replaces the button sprite and, for text buttons, the label. */
@Mixin(AbstractButton.class)
public abstract class PressableWidgetMixin extends AbstractWidget {
	/** The label is drawn through a text collector, so the graphics of the current frame are kept here. */
	@Unique
	private GuiGraphics zerovisual$graphics;

	public PressableWidgetMixin(int x, int y, int width, int height, Component message) {
		super(x, y, width, height, message);
	}

	@Inject(method = "renderWidget", at = @At("HEAD"))
	private void zerovisual$keepGraphics(GuiGraphics graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		zerovisual$graphics = graphics;
	}

	@Inject(method = "renderDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void zerovisual$background(GuiGraphics context, CallbackInfo ci) {
		if (!ButtonStyle.isEnabled()) return;
		ButtonStyle.drawButton(context, this, getX(), getY(), getWidth(), getHeight(), active, isHoveredOrFocused(), alpha);
		ci.cancel();
	}

	@Inject(method = "renderDefaultLabel", at = @At("HEAD"), cancellable = true)
	private void zerovisual$label(ActiveTextCollector output, CallbackInfo ci) {
		// icon buttons (language, accessibility...) keep their own drawing
		if (!ButtonStyle.isEnabled() || zerovisual$graphics == null || (Object) this instanceof SpriteIconButton) return;
		ButtonStyle.drawLabel(zerovisual$graphics, this, getMessage(), getX(), getY(), getWidth(), getHeight(), active, isHoveredOrFocused(), alpha);
		ci.cancel();
	}
}
