package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.hud.ChatHeads;
import com.zerovisual.module.impl.hud.ChatTweaks;
import com.zerovisual.util.ChatLineContext;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.GuiMessage;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Slide-in of new chat lines (ChatTweaks) and sender heads (ChatHeads): the line background and text of the
 * line that is drawn right now are shifted, and the head is drawn in front of the text.
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
public class ChatGraphicsAccessMixin {
	@Shadow
	@Final
	private GuiGraphics graphics;
	@Shadow
	private ActiveTextCollector.Parameters parameters;
	@Unique
	private ActiveTextCollector.Parameters zerovisual$savedParameters;

	@Inject(method = "fill", at = @At("HEAD"), cancellable = true)
	private void zerovisual$slideFill(int x0, int y0, int x1, int y1, int color, CallbackInfo ci) {
		float offset = zerovisual$offset();
		if (offset == 0) return;
		graphics.pose().pushMatrix();
		graphics.pose().translate(offset, 0);
		graphics.fill(x0, y0, x1, y1, zerovisual$fade(color, offset));
		graphics.pose().popMatrix();
		ci.cancel();
	}

	@ModifyVariable(method = "handleMessage", at = @At("HEAD"), argsOnly = true)
	private float zerovisual$slideOpacity(float opacity) {
		float offset = zerovisual$offset();
		return offset == 0 ? opacity : opacity * Math.max(0.02f, 1f + offset / 40f);
	}

	@Inject(method = "handleMessage", at = @At("HEAD"))
	private void zerovisual$slideText(int textTop, float opacity, FormattedCharSequence message, CallbackInfoReturnable<Boolean> cir) {
		GuiMessage.Line line = ChatLineContext.current();
		if (line == null || line.content() != message) return;
		float offset = zerovisual$offset();
		ChatHeads heads = zerovisual$heads();
		if (offset == 0 && heads == null) return;
		int shift = 0;
		if (heads != null) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(offset, 0);
			shift = heads.drawHead(graphics, line, textTop, ARGB.white(opacity));
			graphics.pose().popMatrix();
		}
		zerovisual$savedParameters = parameters;
		parameters = parameters.withPose(new Matrix3x2f(parameters.pose()).translate(offset + shift, 0));
	}

	@Inject(method = "handleMessage", at = @At("RETURN"))
	private void zerovisual$restoreText(int textTop, float opacity, FormattedCharSequence message, CallbackInfoReturnable<Boolean> cir) {
		if (zerovisual$savedParameters == null) return;
		parameters = zerovisual$savedParameters;
		zerovisual$savedParameters = null;
	}

	@Unique
	private static float zerovisual$offset() {
		GuiMessage.Line line = ChatLineContext.current();
		ZeroVisualClient client = ZeroVisualClient.get();
		if (line == null || client == null) return 0;
		ChatTweaks tweaks = client.modules().get(ChatTweaks.class);
		return tweaks.isEnabled() ? tweaks.lineOffset(line) : 0;
	}

	@Unique
	private static ChatHeads zerovisual$heads() {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return null;
		ChatHeads heads = client.modules().get(ChatHeads.class);
		return heads.isEnabled() ? heads : null;
	}

	/** Fades the color in while the line slides (offset goes from -40 to 0). */
	@Unique
	private static int zerovisual$fade(int color, float offset) {
		float t = 1f + offset / 40f;
		int alpha = Math.max(4, Math.round(((color >>> 24) & 0xFF) * t));
		return (alpha << 24) | (color & 0xFFFFFF);
	}
}
