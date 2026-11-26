package com.zerovisual.util;

import java.util.ArrayDeque;
import java.util.Deque;

/** Clicks per second for the left and right mouse buttons, fed from MouseMixin. */
public final class CpsCounter {
	private static final Deque<Long> LEFT = new ArrayDeque<>();
	private static final Deque<Long> RIGHT = new ArrayDeque<>();

	private CpsCounter() {
	}

	public static void click(int button) {
		if (button == 0) LEFT.addLast(System.currentTimeMillis());
		else if (button == 1) RIGHT.addLast(System.currentTimeMillis());
	}

	public static int left() {
		return count(LEFT);
	}

	public static int right() {
		return count(RIGHT);
	}

	private static int count(Deque<Long> clicks) {
		long cutoff = System.currentTimeMillis() - 1000;
		while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) clicks.removeFirst();
		return clicks.size();
	}
}
