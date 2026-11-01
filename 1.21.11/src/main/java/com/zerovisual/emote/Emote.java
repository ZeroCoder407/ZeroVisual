package com.zerovisual.emote;

/**
 * One emote: a function from time to pose. {@code duration} of a looping emote is the length of one cycle.
 * Players in a {@code group} emote dance to one beat.
 */
public record Emote(String id, String name, float duration, boolean loop, boolean group, Animation animation) {
	public Emote(String id, String name, float duration, boolean loop, Animation animation) {
		this(id, name, duration, loop, false, animation);
	}

	@FunctionalInterface
	public interface Animation {
		void sample(float t, EmotePose p);
	}
}
