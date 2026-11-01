package com.zerovisual.module.impl.hud;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.network.chat.Component;

/**
 * Shows the head of the player who wrote a chat message next to it. The sender is the online player
 * whose name appears first near the start of the message (works with ranks and prefixes). Applied in ChatHudMixin.
 */
public class ChatHeads extends Module {
	/** Space taken by the head in front of the text. */
	public static final int OFFSET = 10;
	/** The name must start within this many characters, so names mentioned later in a message are ignored. */
	private static final int MAX_NAME_POSITION = 40;

	private final Map<GuiMessage.Line, Head> heads = Collections.synchronizedMap(new WeakHashMap<>());
	@Nullable
	private PlayerSkin pending;

	public ChatHeads() {
		super("ChatHeads", "Player heads next to their chat messages", Category.HUD);
		setEnabled(true);
	}

	/** Start of ChatHud#addVisibleMessage: find the sender of the message. */
	public void beginMessage(Component content) {
		pending = isEnabled() ? findSender(content.getString()) : null;
	}

	/** Width available for the text of the message being added. */
	public int wrapWidth(int width) {
		return pending != null ? width - OFFSET : width;
	}

	/** End of ChatHud#addVisibleMessage: the first {@code count} visible lines belong to the message. */
	public void endMessage(List<GuiMessage.Line> visible, int count) {
		if (pending == null) return;
		count = Math.min(count, visible.size());
		for (int i = 0; i < count; i++) {
			// lines are inserted at the front, so the first line of the message has the highest index
			heads.put(visible.get(i), new Head(pending, i == count - 1));
		}
		pending = null;
	}

	/** Draws the head for a chat line and returns the text offset (0 when the line has no head). */
	public int drawHead(GuiGraphics ctx, GuiMessage.Line line, int y, int color) {
		if (!isEnabled() || line == null) return 0;
		Head head = heads.get(line);
		if (head == null) return 0;
		if (head.first) {
			int alpha = (color >>> 24) & 0xFF;
			PlayerFaceRenderer.draw(ctx, head.skin, 0, y, 8, (Math.max(alpha, 4) << 24) | 0xFFFFFF);
		}
		return OFFSET;
	}

	@Nullable
	private PlayerSkin findSender(String message) {
		ClientPacketListener network = mc.getConnection();
		if (network == null) return null;
		PlayerInfo best = null;
		int bestIndex = Integer.MAX_VALUE;
		for (PlayerInfo entry : network.getOnlinePlayers()) {
			String name = entry.getProfile().name();
			int index = indexOfWord(message, name);
			if (index >= 0 && index < bestIndex && index <= MAX_NAME_POSITION) {
				best = entry;
				bestIndex = index;
			}
		}
		return best != null ? best.getSkin() : null;
	}

	/** Position of {@code name} in {@code text} as a whole word (Steve must not match Steve123), or -1. */
	private static int indexOfWord(String text, String name) {
		int from = 0;
		while (true) {
			int i = text.indexOf(name, from);
			if (i < 0) return -1;
			int end = i + name.length();
			boolean startOk = i == 0 || !isNameChar(text.charAt(i - 1));
			boolean endOk = end == text.length() || !isNameChar(text.charAt(end));
			if (startOk && endOk) return i;
			from = i + 1;
		}
	}

	private static boolean isNameChar(char c) {
		return c == '_' || Character.isLetterOrDigit(c);
	}

	private record Head(PlayerSkin skin, boolean first) {
	}
}
