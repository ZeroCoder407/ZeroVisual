package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.hud.ChatHeads;
import com.zerovisual.module.impl.hud.ChatTweaks;
import com.zerovisual.render.DrawContext;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * ChatTweaks (timestamps, background opacity, slide-in) and ChatHeads (the head of the sender).
 * While drawChat walks the lines, the line being drawn is remembered from its getUpdatedCounter call.
 */
@Mixin(GuiNewChat.class)
public class GuiNewChatMixin {
	@Shadow
	@Final
	private List<ChatLine> drawnChatLines;

	@Unique
	private ChatLine zerovisual$line;
	@Unique
	private int zerovisual$added;

	@Unique
	private static ChatTweaks zerovisual$tweaks() {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return null;
		ChatTweaks tweaks = client.modules().get(ChatTweaks.class);
		return tweaks.isEnabled() ? tweaks : null;
	}

	@Unique
	private static ChatHeads zerovisual$heads() {
		ZeroVisualClient client = ZeroVisualClient.get();
		return client == null ? null : client.modules().get(ChatHeads.class);
	}

	/** A new message: the time in front, once (the stored message keeps it when the chat is rebuilt). */
	@ModifyArg(method = "printChatMessageWithOptionalDeletion", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiNewChat;setChatLine(Lnet/minecraft/util/IChatComponent;IIZ)V"), index = 0)
	private IChatComponent zerovisual$timestamp(IChatComponent message) {
		ChatTweaks tweaks = zerovisual$tweaks();
		return tweaks != null && tweaks.timestamps.get() ? tweaks.addTimestamp(message) : message;
	}

	@Inject(method = "setChatLine", at = @At("HEAD"))
	private void zerovisual$beginMessage(IChatComponent message, int id, int updateCounter, boolean displayOnly, CallbackInfo ci) {
		ChatHeads heads = zerovisual$heads();
		if (heads != null) heads.beginMessage(message);
	}

	/** The text is wrapped narrower when a head goes in front of it; the number of lines is remembered. */
	@Redirect(method = "setChatLine", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiUtilRenderComponents;splitText(Lnet/minecraft/util/IChatComponent;ILnet/minecraft/client/gui/FontRenderer;ZZ)Ljava/util/List;"))
	private List<IChatComponent> zerovisual$wrap(IChatComponent message, int width, FontRenderer font, boolean trim, boolean forceColor) {
		ChatHeads heads = zerovisual$heads();
		List<IChatComponent> lines = GuiUtilRenderComponents.splitText(message, heads != null ? heads.wrapWidth(width) : width, font, trim, forceColor);
		zerovisual$added = lines.size();
		return lines;
	}

	@Inject(method = "setChatLine", at = @At("TAIL"))
	private void zerovisual$endMessage(IChatComponent message, int id, int updateCounter, boolean displayOnly, CallbackInfo ci) {
		ChatHeads heads = zerovisual$heads();
		if (heads != null) heads.endMessage(drawnChatLines, zerovisual$added);
	}

	@Redirect(method = "drawChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ChatLine;getUpdatedCounter()I"))
	private int zerovisual$currentLine(ChatLine line) {
		zerovisual$line = line;
		return line.getUpdatedCounter();
	}

	/** The background of a line: the opacity of ChatTweaks, moved with the slide-in. */
	@Redirect(method = "drawChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiNewChat;drawRect(IIIII)V", ordinal = 0))
	private void zerovisual$lineBackground(int left, int top, int right, int bottom, int color) {
		ChatTweaks tweaks = zerovisual$tweaks();
		if (tweaks == null) {
			Gui.drawRect(left, top, right, bottom, color);
			return;
		}
		// vanilla uses half the text alpha: the setting goes from none to twice that
		int alpha = Math.min(255, Math.round((color >>> 24) * 2 * tweaks.background.getFloat()));
		int offset = Math.round(tweaks.lineOffset(zerovisual$line));
		if (alpha > 0) Gui.drawRect(left + offset, top, right + offset, bottom, alpha << 24);
	}

	@Redirect(method = "drawChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"))
	private int zerovisual$lineText(FontRenderer font, String text, float x, float y, int color) {
		ChatTweaks tweaks = zerovisual$tweaks();
		float offset = tweaks == null ? 0 : tweaks.lineOffset(zerovisual$line);
		ChatHeads heads = zerovisual$heads();
		if (heads != null) {
			net.minecraft.client.renderer.GlStateManager.pushMatrix();
			net.minecraft.client.renderer.GlStateManager.translate(x + offset, 0, 0);
			offset += heads.drawHead(new DrawContext(), zerovisual$line, (int) y, color);
			net.minecraft.client.renderer.GlStateManager.popMatrix();
			net.minecraft.client.renderer.GlStateManager.enableBlend();
		}
		return font.drawStringWithShadow(text, x + offset, y, color);
	}
}
