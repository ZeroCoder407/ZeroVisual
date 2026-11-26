package com.zerovisual.emote;

/**
 * One emote: a function from time to pose. {@code duration} of a looping emote is the length of one cycle.
 * Players in a {@code group} emote dance to one beat; {@code groupSize 0} means everyone together,
 * {@code groupSize 2} makes pairs (the first two to start dance as one pair, the next two as another).
 */
public final class Emote {
	final String id;
	final String name;
	final float duration;
	final boolean loop;
	final boolean group;
	final int groupSize;
	final Animation animation;

	public Emote(String id, String name, float duration, boolean loop, boolean group, int groupSize, Animation animation) {
		this.id = id;
		this.name = name;
		this.duration = duration;
		this.loop = loop;
		this.group = group;
		this.groupSize = groupSize;
		this.animation = animation;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public float duration() {
		return duration;
	}

	public boolean loop() {
		return loop;
	}

	public boolean group() {
		return group;
	}

	public int groupSize() {
		return groupSize;
	}

	public Animation animation() {
		return animation;
	}

	public Emote(String id, String name, float duration, boolean loop, boolean group, Animation animation) {
		this(id, name, duration, loop, group, 0, animation);
	}

	public Emote(String id, String name, float duration, boolean loop, Animation animation) {
		this(id, name, duration, loop, false, 0, animation);
	}

	@FunctionalInterface
	public interface Animation {
		void sample(float t, EmotePose p);
	}
}
