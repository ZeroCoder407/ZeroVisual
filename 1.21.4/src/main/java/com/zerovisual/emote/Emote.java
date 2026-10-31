package com.zerovisual.emote;

/** One emote: a function from time to pose. {@code duration} of a looping emote is the length of one cycle. */
public record Emote(String id, String name, float duration, boolean loop, Animation animation) {
	@FunctionalInterface
	public interface Animation {
		void sample(float t, EmotePose p);
	}
}
