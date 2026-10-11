package com.zerovisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.hud.ChatTweaks;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public class ChatHudMixin {
	@Unique
	private static ChatTweaks zerovisual$tweaks() {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return null;
		ChatTweaks tweaks = client.modules().get(ChatTweaks.class);
		return tweaks.isEnabled() ? tweaks : null;
	}

	@ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
		at = @At("HEAD"), argsOnly = true)
	private Text zerovisual$timestamp(Text message) {
		ChatTweaks tweaks = zerovisual$tweaks();
		return tweaks != null && tweaks.timestamps.get() ? tweaks.addTimestamp(message) : message;
	}

	/** Ordinal 1 is the text background opacity (0 is chat opacity, 2 line spacing). */
	@ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;", ordinal = 1))
	private Object zerovisual$background(Object value) {
		ChatTweaks tweaks = zerovisual$tweaks();
		return tweaks != null ? (Object) tweaks.background.get() : value;
	}

	// slide-in: shift the background, indicator and text of a line by the same offset

	@WrapOperation(method = "render", at = {
		@At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 0),
		@At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 1)
	})
	private void zerovisual$slideFill(DrawContext context, int x1, int y1, int x2, int y2, int color, Operation<Void> original,
									  @Local ChatHudLine.Visible line) {
		float offset = zerovisual$offset(line);
		if (offset == 0) {
			original.call(context, x1, y1, x2, y2, color);
			return;
		}
		context.getMatrices().push();
		context.getMatrices().translate(offset, 0, 0);
		original.call(context, x1, y1, x2, y2, zerovisual$fade(color, offset));
		context.getMatrices().pop();
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"))
	private int zerovisual$slideText(DrawContext context, TextRenderer renderer, OrderedText text, int x, int y, int color,
									 Operation<Integer> original, @Local ChatHudLine.Visible line) {
		float offset = zerovisual$offset(line);
		if (offset == 0) return original.call(context, renderer, text, x, y, color);
		context.getMatrices().push();
		context.getMatrices().translate(offset, 0, 0);
		int result = original.call(context, renderer, text, x, y, zerovisual$fade(color, offset));
		context.getMatrices().pop();
		return result;
	}

	@Unique
	private static float zerovisual$offset(ChatHudLine.Visible line) {
		ChatTweaks tweaks = zerovisual$tweaks();
		return tweaks != null && line != null ? tweaks.lineOffset(line) : 0;
	}

	/** Fades the color in while the line slides (offset goes from -40 to 0). */
	@Unique
	private static int zerovisual$fade(int color, float offset) {
		float t = 1f + offset / 40f;
		int alpha = Math.max(4, Math.round(((color >>> 24) & 0xFF) * t));
		return (alpha << 24) | (color & 0xFFFFFF);
	}
}
