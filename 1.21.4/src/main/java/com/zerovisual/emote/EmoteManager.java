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
		/** Where the animation counts from: a group dance joins the beat of the others, the fade in still starts now. */
		final long beat;
		volatile long stopAt;

		Active(Emote emote, long start, long beat) {
			this.emote = emote;
			this.start = start;
			this.beat = beat;
		}
	}

	/** DevScreenshots: holds every emote at this moment, seconds; negative in normal use. */
	public static float frozenAt = -1;

	private static final Map<UUID, Active> PLAYING = new ConcurrentHashMap<>();

	private EmoteManager() {
	}

	public static void play(UUID player, Emote emote) {
		long now = System.currentTimeMillis();
		PLAYING.put(player, new Active(emote, now, emote.group() ? groupBeat(player, emote, now) : now));
	}

	/** Every client works this out from its own clock, so the dancers it shows are together. */
	private static long groupBeat(UUID player, Emote emote, long now) {
		long beat = now;
		for (Map.Entry<UUID, Active> entry : PLAYING.entrySet()) {
			Active a = entry.getValue();
			if (!entry.getKey().equals(player) && a.emote == emote && a.stopAt == 0) beat = Math.min(beat, a.beat);
		}
		return beat;
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
		float beat = frozenAt >= 0 ? frozenAt : (now - a.beat) / 1000f;
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
		e.animation().sample(e.loop() ? beat % e.duration() : Math.min(beat, e.duration()), pose);
		return weight * weight * (3 - 2 * weight);
	}
}
