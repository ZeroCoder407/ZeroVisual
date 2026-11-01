package com.zerovisual.util;

import net.minecraft.client.GuiMessage;
import org.jetbrains.annotations.Nullable;

/** The chat line being drawn right now (ChatHudMixin sets it, ChatGraphicsAccessMixin reads it). Render thread only. */
public final class ChatLineContext {
	private static GuiMessage.@Nullable Line current;

	private ChatLineContext() {
	}

	public static GuiMessage.@Nullable Line current() {
		return current;
	}

	public static void set(GuiMessage.@Nullable Line line) {
		current = line;
	}
}
