package com.zerovisual.util;

import net.minecraft.client.util.math.MatrixStack;
import com.zerovisual.emote.EmotePose;
import com.zerovisual.mixin.ModelPartAccessor;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bendable limbs of one player or armor model. The arms, legs and body are cut in two at the middle, keeping their
 * skin mapping: the upper half stays in the vanilla part, the lower half becomes a child with its hinge at the cut.
 * With no rotation it looks exactly like the vanilla model, so sleeves, pants, the jacket and armor just work.
 * While a joint bends, a band of faces rebuilt every frame closes the gap around it, so a limb stays one piece.
 */
public final class EmoteRig {
	private static final String LOWER = "zerovisual_lower", JOINT = "zerovisual_joint";
	/** Half a texture row: the skin row just under the cut stretches over the bend. */
	private static final float ROW = 0.5f / 64;
	private static final float ARM_CUT = 4, LEG_CUT = 6, BODY_CUT = 6;
	/** Where the parts hang in the vanilla pose. */
	private static final float WAIST_Y = 6, HIP_Y = 12;

	private final ModelPart root, head, body, rightArm, leftArm, rightLeg, leftLeg;
	private final ModelPart bodyLow, rightArmLow, leftArmLow, rightLegLow, leftLegLow;
	/** Sleeves and pants: the lower half follows the visibility the game sets on the upper one. */
	private final List<ModelPart[]> mirrored = new ArrayList<>();
	private final List<Joint> joints = new ArrayList<>();

	/** One side of a cut limb: three points along the cut, around the hinge, and the faces between them. */
	private record Face(float[] x, float[] z, float[] u, float v, Vector3f normal, ModelPart.Quad[] quads, Vector3f[] normals) {
	}

	/** The band of one cut. {@code source} is the part that really turns: sleeves follow the arm. */
	private static final class Joint {
		final ModelPart band;
		final float cut;
		final List<Face> faces;
		ModelPart source;

		Joint(ModelPart band, float cut, List<Face> faces) {
			this.band = band;
			this.cut = cut;
			this.faces = faces;
		}
	}

	private final Quaternionf hip = new Quaternionf(), waist = new Quaternionf(), q = new Quaternionf();
	private final Quaternionf full = new Quaternionf(), half = new Quaternionf();
	private final Vector3f v = new Vector3f(), euler = new Vector3f();

	public EmoteRig(BipedEntityModel<?> model) {
		root = model.getRootPart();
		head = model.head;
		body = model.body;
		rightArm = model.rightArm;
		leftArm = model.leftArm;
		rightLeg = model.rightLeg;
		leftLeg = model.leftLeg;
		bodyLow = split(body, BODY_CUT);
		rightArmLow = split(rightArm, ARM_CUT);
		leftArmLow = split(leftArm, ARM_CUT);
		rightLegLow = split(rightLeg, LEG_CUT);
		leftLegLow = split(leftLeg, LEG_CUT);
	}

	/** Moves everything from below {@code cut} into a new child part hinged there; returns that child. */
	private ModelPart split(ModelPart part, float cut) {
		ModelPartAccessor access = (ModelPartAccessor) (Object) part;
		List<ModelPart.Cuboid> upper = new ArrayList<>(), lower = new ArrayList<>();
		List<Face> faces = new ArrayList<>();
		int firstChild = joints.size();
		for (ModelPart.Cuboid cube : access.zerovisual$getCuboids()) {
			List<ModelPart.Quad> up = new ArrayList<>(), low = new ArrayList<>();
			ModelPart.Quad top = null, bottom = null;
			for (ModelPart.Quad polygon : cube.sides) {
				float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
				for (ModelPart.Vertex vertex : polygon.vertices()) {
					min = Math.min(min, vertex.pos().y);
					max = Math.max(max, vertex.pos().y);
				}
				if (max <= cut) {
					up.add(polygon);
					if (min == max) top = polygon;
				} else if (min >= cut) {
					low.add(shifted(polygon, cut));
					if (min == max) bottom = polygon;
				} else {
					ModelPart.Quad clipped = clip(polygon, cut, true);
					up.add(clipped);
					faces.add(face(clipped, polygon.direction(), cut));
					low.add(shifted(clip(polygon, cut, false), cut));
				}
			}
			// caps close the hole at the joint when the limb bends
			if (bottom != null) up.add(flat(bottom, cut, 0));
			if (top != null) low.add(flat(top, cut, cut));
			upper.add(cube(up));
			lower.add(cube(low));
		}
		Map<String, ModelPart> lowerChildren = new HashMap<>();
		for (Map.Entry<String, ModelPart> child : access.zerovisual$getChildren().entrySet()) {
			// the cape hangs from the chest in one piece, only the clothes on the body are cut
			if (child.getKey().equals("cape")) continue;
			ModelPartAccessor c = (ModelPartAccessor) (Object) child.getValue();
			if (c.zerovisual$getCuboids().isEmpty()) continue;
			ModelPart low = split(child.getValue(), cut);
			// the child of a split part hangs on the lower part of its parent instead
			Map<String, ModelPart> kept = new HashMap<>();
			if (c.zerovisual$getChildren().containsKey(JOINT)) kept.put(JOINT, c.zerovisual$getChildren().get(JOINT));
			c.zerovisual$setChildren(kept);
			low.setPivot(0, 0, 0);
			low.setDefaultTransform(low.getTransform());
			lowerChildren.put(child.getKey(), low);
			mirrored.add(new ModelPart[]{child.getValue(), low});
		}
		ModelPart low = new ModelPart(lower, lowerChildren);
		low.setPivot(0, cut, 0);
		low.setDefaultTransform(low.getTransform());
		Map<String, ModelPart> children = new HashMap<>(access.zerovisual$getChildren());
		children.put(LOWER, low);
		for (int i = firstChild; i < joints.size(); i++) joints.get(i).source = low;
		if (!faces.isEmpty()) {
			List<ModelPart.Quad> quads = new ArrayList<>();
			for (Face face : faces) quads.addAll(List.of(face.quads));
			List<ModelPart.Cuboid> cubes = new ArrayList<>();
			for (int i = 0; i < quads.size(); i += 6) cubes.add(cube(quads.subList(i, Math.min(i + 6, quads.size()))));
			ModelPart band = new ModelPart(cubes, Map.of());
			band.visible = false;
			children.put(JOINT, band);
			Joint joint = new Joint(band, cut, faces);
			joint.source = low;
			joints.add(joint);
		}
		access.zerovisual$setChildren(children);
		access.zerovisual$setCuboids(upper);
		return low;
	}

	private static ModelPart.Cuboid cube(List<ModelPart.Quad> polygons) {
		Set<Direction> faces = EnumSet.noneOf(Direction.class);
		for (int i = 0; i < polygons.size(); i++) faces.add(Direction.values()[i]);
		ModelPart.Cuboid cube = new ModelPart.Cuboid(0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, false, 64, 64, faces);
		for (int i = 0; i < polygons.size(); i++) cube.sides[i] = polygons.get(i);
		return cube;
	}

	private static ModelPart.Quad shifted(ModelPart.Quad polygon, float dy) {
		ModelPart.Vertex[] out = new ModelPart.Vertex[polygon.vertices().length];
		for (int i = 0; i < out.length; i++) {
			ModelPart.Vertex a = polygon.vertices()[i];
			out[i] = new ModelPart.Vertex(a.pos().x, a.pos().y - dy, a.pos().z, a.u(), a.v());
		}
		return new ModelPart.Quad(out, polygon.direction());
	}

	/** The band under the clipped part of a side face: {@code clipped} is that part, its two points on the cut give the edge. */
	private static Face face(ModelPart.Quad clipped, Vector3f normal, float cut) {
		List<ModelPart.Vertex> edge = new ArrayList<>();
		for (ModelPart.Vertex a : clipped.vertices()) if (a.pos().y == cut) edge.add(a);
		ModelPart.Vertex a = edge.get(0), b = edge.get(1);
		float[] x = {b.pos().x, (a.pos().x + b.pos().x) / 2, a.pos().x}, z = {b.pos().z, (a.pos().z + b.pos().z) / 2, a.pos().z};
		float[] u = {b.u(), (a.u() + b.u()) / 2, a.u()};
		ModelPart.Quad[] quads = new ModelPart.Quad[4];
		Vector3f[] normals = new Vector3f[4];
		for (int i = 0; i < 4; i++) {
			normals[i] = new Vector3f(normal);
			ModelPart.Vertex[] corners = new ModelPart.Vertex[4];
			java.util.Arrays.fill(corners, a);
			quads[i] = new ModelPart.Quad(corners, normals[i]);
		}
		return new Face(x, z, u, a.v() + ROW, normal, quads, normals);
	}

	/** Rebuilds the bands: two steps around each hinge, so the outside of a bend comes out round. */
	private void closeJoints() {
		for (Joint joint : joints) {
			ModelPart low = joint.source;
			boolean bent = Math.abs(low.pitch) + Math.abs(low.yaw) + Math.abs(low.roll) > 0.01f;
			joint.band.visible = bent;
			if (!bent) continue;
			full.rotationZYX(low.roll, low.yaw, low.pitch);
			half.identity().slerp(full, 0.5f);
			for (Face face : joint.faces) {
				for (int step = 0; step < 2; step++) {
					Quaternionf from = step == 0 ? null : half, to = step == 0 ? half : full;
					for (int side = 0; side < 2; side++) {
						ModelPart.Quad quad = face.quads[step * 2 + side];
						ModelPart.Vertex[] corners = quad.vertices();
						corners[0] = ring(face, side, from, joint.cut);
						corners[1] = ring(face, side + 1, from, joint.cut);
						corners[2] = ring(face, side + 1, to, joint.cut);
						corners[3] = ring(face, side, to, joint.cut);
						half.transform(face.normals[step * 2 + side].set(face.normal));
					}
				}
			}
		}
	}

	private ModelPart.Vertex ring(Face face, int i, Quaternionf turn, float cut) {
		v.set(face.x[i], 0, face.z[i]);
		if (turn != null) turn.transform(v);
		return new ModelPart.Vertex(v.x, v.y + cut, v.z, face.u[i], face.v);
	}

	/** A copy of a flat face moved to height {@code y}, then shifted by {@code dy}. */
	private static ModelPart.Quad flat(ModelPart.Quad polygon, float y, float dy) {
		ModelPart.Vertex[] out = new ModelPart.Vertex[polygon.vertices().length];
		for (int i = 0; i < out.length; i++) {
			ModelPart.Vertex a = polygon.vertices()[i];
			out[i] = new ModelPart.Vertex(a.pos().x, y - dy, a.pos().z, a.u(), a.v());
		}
		return new ModelPart.Quad(out, polygon.direction());
	}

	/** The part of a side face above (or below) the cut, with the texture coordinates carried along. */
	private static ModelPart.Quad clip(ModelPart.Quad polygon, float cut, boolean above) {
		ModelPart.Vertex[] in = polygon.vertices();
		List<ModelPart.Vertex> out = new ArrayList<>();
		for (int i = 0; i < in.length; i++) {
			ModelPart.Vertex a = in[i], b = in[(i + 1) % in.length];
			boolean aIn = above ? a.pos().y <= cut : a.pos().y >= cut, bIn = above ? b.pos().y <= cut : b.pos().y >= cut;
			if (aIn) out.add(a);
			if (aIn != bIn) {
				float k = (cut - a.pos().y) / (b.pos().y - a.pos().y);
				out.add(new ModelPart.Vertex(MathHelper.lerp(k, a.pos().x, b.pos().x), cut, MathHelper.lerp(k, a.pos().z, b.pos().z),
					MathHelper.lerp(k, a.u(), b.u()), MathHelper.lerp(k, a.v(), b.v())));
			}
		}
		return new ModelPart.Quad(out.toArray(new ModelPart.Vertex[0]), polygon.direction());
	}

	/** 1.21.4 poses the armor by copying the transforms of the player model: the bends and the hips go along. */
	public void copyFrom(EmoteRig other) {
		root.copyTransform(other.root);
		bodyLow.copyTransform(other.bodyLow);
		rightArmLow.copyTransform(other.rightArmLow);
		leftArmLow.copyTransform(other.leftArmLow);
		rightLegLow.copyTransform(other.rightLegLow);
		leftLegLow.copyTransform(other.leftLegLow);
		closeJoints();
	}

	/** Every frame: the lower halves back to rest, and the visibility of the sleeves copied over. */
	public void rest() {
		rest(bodyLow);
		rest(rightArmLow);
		rest(leftArmLow);
		rest(rightLegLow);
		rest(leftLegLow);
		for (ModelPart[] pair : mirrored) pair[1].visible = pair[0].visible;
		for (Joint joint : joints) joint.band.visible = false;
	}

	private static void rest(ModelPart part) {
		part.pitch = part.yaw = part.roll = 0;
	}

	/** Puts the model into the pose; {@code weight} fades between what the game set and the pose. */
	public void apply(EmotePose p, float w) {
		hip.identity().rotateZYX(p.hipRz * w, p.hipRy * w, p.hipRx * w);
		hip.transform(v.set(0, HIP_Y, 0));
		root.pivotX = p.hipX * w - v.x;
		root.pivotY = HIP_Y + p.hipY * w - v.y;
		root.pivotZ = p.hipZ * w - v.z;
		hip.getEulerAnglesZYX(euler);
		root.pitch = euler.x;
		root.yaw = euler.y;
		root.roll = euler.z;

		// the body part is the chest, hanging from the waist; its lower half stays with the hips
		waist.identity().rotateZYX(p.waistRz * w, p.waistRy * w, p.waistRx * w);
		waist.transform(v.set(0, WAIST_Y, 0));
		body.pivotX = -v.x;
		body.pivotY = WAIST_Y - v.y;
		body.pivotZ = -v.z;
		waist.getEulerAnglesZYX(euler);
		body.pitch = euler.x;
		body.yaw = euler.y;
		body.roll = euler.z;
		q.set(waist).conjugate().getEulerAnglesZYX(euler);
		bodyLow.pitch = euler.x;
		bodyLow.yaw = euler.y;
		bodyLow.roll = euler.z;

		onChest(head, 0, 0, p.headRx, p.headRy, p.headRz, w);
		onChest(rightArm, -5, 2, p.rArmRx, p.rArmRy, p.rArmRz, w);
		onChest(leftArm, 5, 2, p.lArmRx, p.lArmRy, p.lArmRz, w);
		rightArmLow.pitch = p.rElbow * w;
		leftArmLow.pitch = p.lElbow * w;

		rightLeg.pitch = MathHelper.lerp(w, rightLeg.pitch, p.rLegRx);
		rightLeg.yaw = MathHelper.lerp(w, rightLeg.yaw, p.rLegRy);
		rightLeg.roll = MathHelper.lerp(w, rightLeg.roll, p.rLegRz);
		leftLeg.pitch = MathHelper.lerp(w, leftLeg.pitch, p.lLegRx);
		leftLeg.yaw = MathHelper.lerp(w, leftLeg.yaw, p.lLegRy);
		leftLeg.roll = MathHelper.lerp(w, leftLeg.roll, p.lLegRz);
		rightLeg.pivotX = MathHelper.lerp(w, rightLeg.pivotX, -1.9f);
		rightLeg.pivotY = MathHelper.lerp(w, rightLeg.pivotY, HIP_Y);
		rightLeg.pivotZ = MathHelper.lerp(w, rightLeg.pivotZ, 0);
		leftLeg.pivotX = MathHelper.lerp(w, leftLeg.pivotX, 1.9f);
		leftLeg.pivotY = MathHelper.lerp(w, leftLeg.pivotY, HIP_Y);
		leftLeg.pivotZ = MathHelper.lerp(w, leftLeg.pivotZ, 0);
		rightLegLow.pitch = p.rKnee * w;
		leftLegLow.pitch = p.lKnee * w;
		closeJoints();
	}

	/** The head and the arms ride on the chest: their joint moves and turns with the waist. */
	private void onChest(ModelPart part, float x, float y, float rx, float ry, float rz, float w) {
		waist.transform(v.set(x, y - WAIST_Y, 0));
		part.pivotX = MathHelper.lerp(w, part.pivotX, v.x);
		part.pivotY = MathHelper.lerp(w, part.pivotY, WAIST_Y + v.y);
		part.pivotZ = MathHelper.lerp(w, part.pivotZ, v.z);
		q.set(waist).rotateZYX(rz, ry, rx).getEulerAnglesZYX(euler);
		part.pitch = MathHelper.lerp(w, part.pitch, euler.x);
		part.yaw = MathHelper.lerp(w, part.yaw, euler.y);
		part.roll = MathHelper.lerp(w, part.roll, euler.z);
	}

	/** After the game moved the pose stack to the arm: on to the forearm, so held items stay in the hand. */
	public void toHand(Arm arm, MatrixStack matrices) {
		ModelPart low = arm == Arm.RIGHT ? rightArmLow : leftArmLow;
		low.rotate(matrices);
		matrices.translate(0, -ARM_CUT / 16, 0);
	}
}
