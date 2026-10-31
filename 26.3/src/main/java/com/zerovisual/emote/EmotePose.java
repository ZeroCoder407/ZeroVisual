package com.zerovisual.emote;

/**
 * Pose of a player model for one frame. Angles are radians, offsets are model pixels (16 per block).
 * Model axes: +x to the left of the player, +y down, -z forward. Rotations are local to the parent joint:
 * hips carry everything, the waist carries the chest, head and arms. The elbow and knee bend around x.
 */
public final class EmotePose {
	public float hipX, hipY, hipZ, hipRx, hipRy, hipRz;
	public float waistRx, waistRy, waistRz;
	public float headRx, headRy, headRz;
	public float rArmRx, rArmRy, rArmRz, rElbow;
	public float lArmRx, lArmRy, lArmRz, lElbow;
	public float rLegRx, rLegRy, rLegRz, rKnee;
	public float lLegRx, lLegRy, lLegRz, lKnee;

	/** This pose becomes the mix of two others, {@code k} of the way from a to b. */
	public void set(EmotePose a, EmotePose b, float k) {
		hipX = a.hipX + (b.hipX - a.hipX) * k;
		hipY = a.hipY + (b.hipY - a.hipY) * k;
		hipZ = a.hipZ + (b.hipZ - a.hipZ) * k;
		hipRx = a.hipRx + (b.hipRx - a.hipRx) * k;
		hipRy = a.hipRy + (b.hipRy - a.hipRy) * k;
		hipRz = a.hipRz + (b.hipRz - a.hipRz) * k;
		waistRx = a.waistRx + (b.waistRx - a.waistRx) * k;
		waistRy = a.waistRy + (b.waistRy - a.waistRy) * k;
		waistRz = a.waistRz + (b.waistRz - a.waistRz) * k;
		headRx = a.headRx + (b.headRx - a.headRx) * k;
		headRy = a.headRy + (b.headRy - a.headRy) * k;
		headRz = a.headRz + (b.headRz - a.headRz) * k;
		rArmRx = a.rArmRx + (b.rArmRx - a.rArmRx) * k;
		rArmRy = a.rArmRy + (b.rArmRy - a.rArmRy) * k;
		rArmRz = a.rArmRz + (b.rArmRz - a.rArmRz) * k;
		rElbow = a.rElbow + (b.rElbow - a.rElbow) * k;
		lArmRx = a.lArmRx + (b.lArmRx - a.lArmRx) * k;
		lArmRy = a.lArmRy + (b.lArmRy - a.lArmRy) * k;
		lArmRz = a.lArmRz + (b.lArmRz - a.lArmRz) * k;
		lElbow = a.lElbow + (b.lElbow - a.lElbow) * k;
		rLegRx = a.rLegRx + (b.rLegRx - a.rLegRx) * k;
		rLegRy = a.rLegRy + (b.rLegRy - a.rLegRy) * k;
		rLegRz = a.rLegRz + (b.rLegRz - a.rLegRz) * k;
		rKnee = a.rKnee + (b.rKnee - a.rKnee) * k;
		lLegRx = a.lLegRx + (b.lLegRx - a.lLegRx) * k;
		lLegRy = a.lLegRy + (b.lLegRy - a.lLegRy) * k;
		lLegRz = a.lLegRz + (b.lLegRz - a.lLegRz) * k;
		lKnee = a.lKnee + (b.lKnee - a.lKnee) * k;
	}

	public void reset() {
		hipX = hipY = hipZ = hipRx = hipRy = hipRz = 0;
		waistRx = waistRy = waistRz = 0;
		headRx = headRy = headRz = 0;
		rArmRx = rArmRy = rArmRz = rElbow = 0;
		lArmRx = lArmRy = lArmRz = lElbow = 0;
		rLegRx = rLegRy = rLegRz = rKnee = 0;
		lLegRx = lLegRy = lLegRz = lKnee = 0;
	}
}
