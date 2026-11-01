package com.zerovisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.hud.ChatHeads;
import com.zerovisual.module.impl.hud.ChatTweaks;
import com.zerovisual.util.ChatLineContext;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * ChatTweaks (timestamps, background, slide-in) and ChatHeads (sender heads). The slide-in and the heads are
 * drawn by ChatGraphicsAccessMixin for the line that is being drawn (ChatLineContext).
 */
@Mixin(ChatComponent.class)
public class ChatHudMixin {
	@Unique
	private static final String LINE_CONSUMER =
		"Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;accept(Lnet/minecraft/client/GuiMessage$Line;IF)V";

	@Shadow
	@Final
	private List<GuiMessage.Line> trimmedMessages;
	@Unique
	private int zerovisual$lineCount;

	@Unique
	private static ChatTweaks zerovisual$tweaks() {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return null;
		ChatTweaks tweaks = client.modules().get(ChatTweaks.class);
		return tweaks.isEnabled() ? tweaks : null;
	}

	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
	private Component zerovisual$timestamp(Component message) {
		ChatTweaks tweaks = zerovisual$tweaks();
		return tweaks != null && tweaks.timestamps.get() ? tweaks.addTimestamp(message) : message;
	}

	/** Ordinal 1 is the text background opacity (0 is chat opacity, 2 line spacing). */
	@ModifyExpressionValue(method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 1))
	private Object zerovisual$background(Object value) {
		ChatTweaks tweaks = zerovisual$tweaks();
		return tweaks != null ? (Object) tweaks.background.get() : value;
	}

	/** Remembers which line is drawn, for the slide-in and the heads. */
	@Inject(method = "forEachLine", at = @At(value = "INVOKE", target = LINE_CONSUMER))
	private void zerovisual$lineBegin(CallbackInfoReturnable<Integer> cir, @Local GuiMessage.Line line) {
		ChatLineContext.set(line);
	}

	@Inject(method = "forEachLine", at = @At(value = "INVOKE", target = LINE_CONSUMER, shift = At.Shift.AFTER))
	private void zerovisual$lineEnd(CallbackInfoReturnable<Integer> cir) {
		ChatLineContext.set(null);
	}

	// ChatHeads: find the sender while the message is split into lines and remember which lines belong to it

	@Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"))
	private void zerovisual$headsBegin(GuiMessage message, CallbackInfo ci) {
		ChatHeads heads = zerovisual$heads();
		if (heads != null) heads.beginMessage(message.content());
	}

	@ModifyArg(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"), index = 1)
	private int zerovisual$headsWidth(int width) {
		ChatHeads heads = zerovisual$heads();
		return heads != null ? heads.wrapWidth(width) : width;
	}

	@ModifyExpressionValue(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
	private List<FormattedCharSequence> zerovisual$headsLines(List<FormattedCharSequence> lines) {
		zerovisual$lineCount = lines.size();
		return lines;
	}

	@Inject(method = "addMessageToDisplayQueue", at = @At("RETURN"))
	private void zerovisual$headsEnd(GuiMessage message, CallbackInfo ci) {
		ChatHeads heads = zerovisual$heads();
		if (heads != null) heads.endMessage(trimmedMessages, zerovisual$lineCount);
	}

	@Unique
	private static ChatHeads zerovisual$heads() {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return null;
		ChatHeads heads = client.modules().get(ChatHeads.class);
		return heads.isEnabled() ? heads : null;
	}
}
