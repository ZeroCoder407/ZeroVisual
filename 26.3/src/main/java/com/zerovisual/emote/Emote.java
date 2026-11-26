package com.zerovisual.emote;

/**
 * One emote: a function from time to pose. {@code duration} of a looping emote is the length of one cycle.
 * Players in a {@code group} emote dance to one beat; {@code groupSize 0} means everyone together,
 * {@code groupSize 2} makes pairs (the first two to start dance as one pair, the next two as another).
 */
public record Emote(String id, String name, float duration, boolean loop, boolean group, int groupSize, Animation animation) {
	public Emote(String id, String name, float duration, boolean loop, Animation animation) {
		this(id, name, duration, loop, false, 0, animation);
	}

	public Emote(String id, String name, float duration, boolean loop, boolean group, Animation animation) {
		this(id, name, duration, loop, group, 0, animation);
	}

	@FunctionalInterface
	public interface Animation {
		void sample(float t, EmotePose p);
	}
}
