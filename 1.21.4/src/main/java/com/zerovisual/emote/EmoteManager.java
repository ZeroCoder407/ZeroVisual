package com.zerovisual.emote;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Who is playing what. Render threads read it, the network thread writes it. No Minecraft classes here. */
public final class EmoteManager {
	private static final float FADE_IN = 0.18f, FADE_OUT = 0.25f;

	private static final class Active {
		final Emote emote;
		final long start;
		volatile long stopAt;

		Active(Emote emote, long start) {
			this.emote = emote;
			this.start = start;
		}
	}

	/** DevScreenshots: holds every emote at this moment, seconds; negative in normal use. */
	public static float frozenAt = -1;

	private static final Map<UUID, Active> PLAYING = new ConcurrentHashMap<>();

	private EmoteManager() {
	}

	public static void play(UUID player, Emote emote) {
		PLAYING.put(player, new Active(emote, System.currentTimeMillis()));
	}

	/** Fades out from here. */
	public static void stop(UUID player) {
		Active a = PLAYING.get(player);
		if (a != null && a.stopAt == 0) a.stopAt = System.currentTimeMillis();
	}

	public static void clear() {
		PLAYING.clear();
	}

	/** The emote this player is playing and not already fading out of, or null. */
	public static Emote current(UUID player) {
		Active a = PLAYING.get(player);
		return a == null || a.stopAt != 0 ? null : a.emote;
	}

	/** Fills {@code pose} and returns its weight (0..1); 0 when the player plays nothing. */
	public static float sample(UUID player, EmotePose pose) {
		Active a = PLAYING.get(player);
		if (a == null) return 0;
		long now = System.currentTimeMillis();
		float t = frozenAt >= 0 ? frozenAt : (now - a.start) / 1000f;
		Emote e = a.emote;
		if (a.stopAt == 0 && !e.loop() && t >= e.duration()) a.stopAt = now;
		float weight = Math.min(1, t / FADE_IN);
		if (a.stopAt != 0) {
			float fade = (now - a.stopAt) / 1000f / FADE_OUT;
			if (fade >= 1) {
				PLAYING.remove(player, a);
				return 0;
			}
			weight = Math.min(weight, 1 - fade);
		}
		pose.reset();
		e.animation().sample(e.loop() ? t % e.duration() : Math.min(t, e.duration()), pose);
		return weight * weight * (3 - 2 * weight);
	}
}
