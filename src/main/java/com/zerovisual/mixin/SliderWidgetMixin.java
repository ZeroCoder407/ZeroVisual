package com.zerovisual.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zerovisual.gui.ButtonStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

/** Rounded dark slider track with an accent handle. */
@Mixin(SliderWidget.class)
public abstract class SliderWidgetMixin extends ClickableWidget {
	public SliderWidgetMixin(int x, int y, int width, int height, Text message) {
		super(x, y, width, height, message);
	}

	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIIII)V", ordinal = 0))
	private void zerovisual$track(DrawContext context, Function<Identifier, RenderLayer> layers, Identifier sprite,
								  int x, int y, int width, int height, int color, Operation<Void> original) {
		if (!ButtonStyle.isEnabled()) {
			original.call(context, layers, sprite, x, y, width, height, color);
			return;
		}
		ButtonStyle.drawSliderTrack(context, x, y, width, height, active, alpha);
	}

	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIIII)V", ordinal = 1))
	private void zerovisual$handle(DrawContext context, Function<Identifier, RenderLayer> layers, Identifier sprite,
								   int x, int y, int width, int height, int color, Operation<Void> original) {
		if (!ButtonStyle.isEnabled()) {
			original.call(context, layers, sprite, x, y, width, height, color);
			return;
		}
		ButtonStyle.drawSliderHandle(context, x, y, width, height, isSelected(), alpha);
	}
}
