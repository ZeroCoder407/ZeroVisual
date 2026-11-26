package com.zerovisual.emote;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/** Where the head and the chest of a player really are during an emote, for things worn on them. No Minecraft classes here. */
public final class EmoteAnchor {
	/** Block size of a model pixel on a rendered player. */
	private static final float PIXEL = 0.9375f / 16;
	/** Height of the head joint above the feet, and the height of the player box above that joint. */
	private static final float HEAD_JOINT = 1.501f * 0.9375f, TOP = 1.8f - HEAD_JOINT;
	private static final float HIP_Y = 12, WAIST_Y = 6;
	/** Model space has y and z the other way round from the world. */
	private static final Quaternionf FLIP = new Quaternionf().rotationX((float) Math.PI);

	private static final EmotePose POSE = new EmotePose();

	private EmoteAnchor() {
	}

	/**
	 * Transform from the space of a thing worn on the head (y up, origin on the head) to the world, relative to the
	 * feet of the player. Without an emote this is the usual spot above the player, turned to where the head looks.
	 *
	 * @param above how far above the top of the player box the thing normally floats
	 */
	public static Matrix4f head(UUID player, float bodyYaw, float headYaw, float boxHeight, float above) {
		Vector3f rest = new Vector3f(0, boxHeight + above, 0);
		Quaternionf restTurn = new Quaternionf().rotationY((float) Math.toRadians(-headYaw));
		float w = EmoteManager.sample(player, POSE);
		if (w <= 0) return new Matrix4f().translation(rest).rotate(restTurn);
		EmotePose p = POSE;
		Quaternionf hip = hip(p, w), waist = waist(p, w);
		Vector3f neck = waist.transform(new Vector3f(0, -WAIST_Y, 0), new Vector3f()).add(0, WAIST_Y, 0);
		Quaternionf turn = new Quaternionf(hip).mul(waist).rotateZYX(p.headRz * w, p.headRy * w, p.headRx * w);
		return place(rest, restTurn, hip.transform(neck).add(root(p, hip, w)), turn, TOP + above, bodyYaw, w);
	}

	/**
	 * The same for a thing worn on the back: y up and z forward, origin on the spine at {@code height} above the feet.
	 */
	public static Matrix4f chest(UUID player, float bodyYaw, float height) {
		Vector3f rest = new Vector3f(0, height, 0);
		Quaternionf restTurn = new Quaternionf().rotationY((float) Math.toRadians(-bodyYaw));
		float w = EmoteManager.sample(player, POSE);
		if (w <= 0) return new Matrix4f().translation(rest).rotate(restTurn);
		EmotePose p = POSE;
		Quaternionf hip = hip(p, w), waist = waist(p, w);
		Vector3f spot = waist.transform(new Vector3f(0, (HEAD_JOINT - height) / PIXEL - WAIST_Y, 0), new Vector3f()).add(0, WAIST_Y, 0);
		return place(rest, restTurn, hip.transform(spot).add(root(p, hip, w)), new Quaternionf(hip).mul(waist), 0, bodyYaw, w);
	}

	private static Quaternionf hip(EmotePose p, float w) {
		return new Quaternionf().rotateZYX(p.hipRz * w, p.hipRy * w, p.hipRx * w);
	}

	private static Quaternionf waist(EmotePose p, float w) {
		return new Quaternionf().rotateZYX(p.waistRz * w, p.waistRy * w, p.waistRx * w);
	}

	/** Where the root of the model is: the hips turn around their own joint, as in EmoteRig. */
	private static Vector3f root(EmotePose p, Quaternionf hip, float w) {
		return hip.transform(new Vector3f(0, HIP_Y, 0), new Vector3f()).negate().add(p.hipX * w, HIP_Y + p.hipY * w, p.hipZ * w);
	}

	/** Model pixels to blocks above the feet; what is up for the worn thing is up for that part of the model. */
	private static Matrix4f place(Vector3f rest, Quaternionf restTurn, Vector3f joint, Quaternionf turn, float lift, float bodyYaw, float w) {
		Quaternionf body = new Quaternionf().rotationY((float) Math.toRadians(-bodyYaw));
		Vector3f position = FLIP.transform(joint, new Vector3f()).mul(PIXEL).add(0, HEAD_JOINT, 0);
		Quaternionf worn = new Quaternionf(FLIP).mul(turn).mul(FLIP);
		Vector3f lifted = worn.transform(new Vector3f(0, lift, 0)).add(position);
		body.transform(lifted);
		Quaternionf facing = new Quaternionf(body).mul(worn);
		return new Matrix4f().translation(rest.lerp(lifted, w)).rotate(restTurn.nlerp(facing, w));
	}
}
