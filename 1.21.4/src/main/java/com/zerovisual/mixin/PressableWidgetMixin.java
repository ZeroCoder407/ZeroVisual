package com.zerovisual.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zerovisual.gui.ButtonStyle;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.TextIconButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

/** Rounded dark buttons: replaces the button texture and, for text buttons, the label. */
@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin extends ClickableWidget {
	public PressableWidgetMixin(int x, int y, int width, int height, Text message) {
		super(x, y, width, height, message);
	}

	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIIII)V"))
	private void zerovisual$background(DrawContext context, Function<Identifier, RenderLayer> layers, Identifier sprite,
									   int x, int y, int width, int height, int color, Operation<Void> original) {
		if (!ButtonStyle.isEnabled()) {
			original.call(context, layers, sprite, x, y, width, height, color);
			return;
		}
		ButtonStyle.drawButton(context, this, x, y, width, height, active, isSelected(), alpha);
	}

	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/widget/PressableWidget;drawMessage(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;I)V"))
	private void zerovisual$label(PressableWidget widget, DrawContext context, TextRenderer textRenderer, int color, Operation<Void> original) {
		// icon buttons (language, accessibility...) keep their own drawing
		if (!ButtonStyle.isEnabled() || widget instanceof TextIconButtonWidget) {
			original.call(widget, context, textRenderer, color);
			return;
		}
		ButtonStyle.drawLabel(context, this, getMessage(), getX(), getY(), getWidth(), getHeight(), active, isSelected(), alpha);
	}
}
