package com.zerovisual.module.impl.hud;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Chat look: background opacity, slide-in animation, timestamps. Applied in ChatHudMixin. */
public class ChatTweaks extends Module {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final long ANIMATION_MS = 220;

	public final NumberSetting background = add(new NumberSetting("Background", 0.5, 0, 1, 0.05));
	public final BooleanSetting animation = add(new BooleanSetting("Animation", true));
	public final BooleanSetting timestamps = add(new BooleanSetting("Timestamps", true));

	/** When each chat line was first drawn, for the slide-in animation. */
	private final Map<Object, Long> firstSeen = Collections.synchronizedMap(new WeakHashMap<>());

	public ChatTweaks() {
		super("ChatTweaks", "Прозрачность фона чата, анимация и время сообщений", Category.HUD);
	}

	public Text addTimestamp(Text message) {
		MutableText time = Text.literal("[" + LocalTime.now().format(TIME) + "] ").formatted(Formatting.DARK_GRAY);
		return time.append(message);
	}

	/** Horizontal offset of a chat line: slides in from the left when it appears. */
	public float lineOffset(Object line) {
		if (!animation.get()) return 0;
		long now = System.currentTimeMillis();
		long first = firstSeen.computeIfAbsent(line, l -> now);
		float t = Math.min(1f, (now - first) / (float) ANIMATION_MS);
		float eased = 1f - (1f - t) * (1f - t) * (1f - t);
		return (1f - eased) * -40f;
	}
}
