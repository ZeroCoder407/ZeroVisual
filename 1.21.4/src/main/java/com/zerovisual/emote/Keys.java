package com.zerovisual.emote;

import java.util.ArrayList;
import java.util.List;

/**
 * A looping emote as a list of poses at given times. Between the poses everything eases.
 * The height of the hips comes from the legs, so the lower foot always stays on the floor; {@code lift} adds a jump.
 */
final class Keys implements Emote.Animation {
	/** Length of the thigh and of the shin, in model pixels. */
	private static final float SEGMENT = 6;

	/** One pose. All the setters return this, so a pose reads as one line. */
	static final class Pose {
		final EmotePose p = new EmotePose();
		float lift;

		Pose legR(float rx, float ry, float rz, float knee) {
			p.rLegRx = rx;
			p.rLegRy = ry;
			p.rLegRz = rz;
			p.rKnee = knee;
			return this;
		}

		Pose legL(float rx, float ry, float rz, float knee) {
			p.lLegRx = rx;
			p.lLegRy = ry;
			p.lLegRz = rz;
			p.lKnee = knee;
			return this;
		}

		Pose armR(float rx, float ry, float rz, float elbow) {
			p.rArmRx = rx;
			p.rArmRy = ry;
			p.rArmRz = rz;
			p.rElbow = elbow;
			return this;
		}

		Pose armL(float rx, float ry, float rz, float elbow) {
			p.lArmRx = rx;
			p.lArmRy = ry;
			p.lArmRz = rz;
			p.lElbow = elbow;
			return this;
		}

		Pose waist(float rx, float ry, float rz) {
			p.waistRx = rx;
			p.waistRy = ry;
			p.waistRz = rz;
			return this;
		}

		Pose head(float rx, float ry, float rz) {
			p.headRx = rx;
			p.headRy = ry;
			p.headRz = rz;
			return this;
		}

		/** Weight shifted sideways, and the hips turned and tilted. */
		Pose hip(float x, float ry, float rz) {
			p.hipX = x;
			p.hipRy = ry;
			p.hipRz = rz;
			return this;
		}

		Pose lift(float pixels) {
			lift = pixels;
			return this;
		}
	}

	private record Frame(float time, Pose pose) {
	}

	private final List<Frame> frames = new ArrayList<>();
	private final float period;

	Keys(float period) {
		this.period = period;
	}

	Keys at(float time, Pose pose) {
		frames.add(new Frame(time, pose));
		return this;
	}

	private static float legHeight(float rx, float rz, float knee) {
		return SEGMENT * (float) (Math.cos(rx) * Math.cos(rz) + Math.cos(rx + knee) * Math.cos(rz));
	}

	@Override
	public void sample(float t, EmotePose out) {
		int next = 0;
		while (next < frames.size() && frames.get(next).time <= t) next++;
		Frame a = frames.get(next == 0 ? frames.size() - 1 : next - 1), b = frames.get(next % frames.size());
		float span = b.time - a.time;
		if (span <= 0) span += period;
		float k = t - a.time;
		if (k < 0) k += period;
		k = Math.min(1, k / span);
		k = k * k * (3 - 2 * k);
		out.set(a.pose.p, b.pose.p, k);
		float lift = a.pose.lift + (b.pose.lift - a.pose.lift) * k;
		float stand = Math.max(legHeight(out.rLegRx, out.rLegRz, out.rKnee), legHeight(out.lLegRx, out.lLegRz, out.lKnee));
		out.hipY = SEGMENT * 2 - stand - lift;
	}
}
