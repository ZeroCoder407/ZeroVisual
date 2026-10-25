package com.zerovisual.render;

/** Frame-rate independent exponential easing towards a target value. */
public class Anim {
	private final float speed;
	private float value;
	private long lastUpdate = System.nanoTime();

	public Anim(float speed, float initial) {
		this.speed = speed;
		this.value = initial;
	}

	public float update(float target) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastUpdate) / 1_000_000_000f, 0.1f);
		lastUpdate = now;
		value += (target - value) * (1f - (float) Math.exp(-speed * dt));
		if (Math.abs(target - value) < 0.001f) value = target;
		return value;
	}

	public float update(boolean on) {
		return update(on ? 1f : 0f);
	}

	public float get() {
		return value;
	}

	public void set(float value) {
		this.value = value;
		this.lastUpdate = System.nanoTime();
	}
}
