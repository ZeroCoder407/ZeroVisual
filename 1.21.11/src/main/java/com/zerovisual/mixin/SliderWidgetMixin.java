package com.zerovisual.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zerovisual.gui.ButtonStyle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Rounded dark slider track with an accent handle. */
@Mixin(AbstractSliderButton.class)
public abstract class SliderWidgetMixin extends AbstractWidget {
	public SliderWidgetMixin(int x, int y, int width, int height, Component message) {
		super(x, y, width, height, message);
	}

	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V", ordinal = 0))
	private void zerovisual$track(GuiGraphics context, RenderPipeline layers, Identifier sprite,
								  int x, int y, int width, int height, int color, Operation<Void> original) {
		if (!ButtonStyle.isEnabled()) {
			original.call(context, layers, sprite, x, y, width, height, color);
			return;
		}
		ButtonStyle.drawSliderTrack(context, x, y, width, height, active, alpha);
	}

	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V", ordinal = 1))
	private void zerovisual$handle(GuiGraphics context, RenderPipeline layers, Identifier sprite,
								   int x, int y, int width, int height, int color, Operation<Void> original) {
		if (!ButtonStyle.isEnabled()) {
			original.call(context, layers, sprite, x, y, width, height, color);
			return;
		}
		ButtonStyle.drawSliderHandle(context, x, y, width, height, isHoveredOrFocused(), alpha);
	}
}
