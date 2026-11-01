package com.zerovisual.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zerovisual.emote.EmotePose;
import com.zerovisual.mixin.ModelPartAccessor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

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
	private record Face(float[] x, float[] z, float[] u, float v, Vector3fc normal, ModelPart.Polygon[] quads, Vector3f[] normals) {
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

	public EmoteRig(HumanoidModel<?> model) {
		root = model.root();
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
		List<ModelPart.Cube> upper = new ArrayList<>(), lower = new ArrayList<>();
		List<Face> faces = new ArrayList<>();
		int firstChild = joints.size();
		for (ModelPart.Cube cube : access.zerovisual$getCubes()) {
			List<ModelPart.Polygon> up = new ArrayList<>(), low = new ArrayList<>();
			ModelPart.Polygon top = null, bottom = null;
			for (ModelPart.Polygon polygon : cube.polygons) {
				float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
				for (ModelPart.Vertex vertex : polygon.vertices()) {
					min = Math.min(min, vertex.y());
					max = Math.max(max, vertex.y());
				}
				if (max <= cut) {
					up.add(polygon);
					if (min == max) top = polygon;
				} else if (min >= cut) {
					low.add(shifted(polygon, cut));
					if (min == max) bottom = polygon;
				} else {
					ModelPart.Polygon clipped = clip(polygon, cut, true);
					up.add(clipped);
					faces.add(face(clipped, polygon.normal(), cut));
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
			if (c.zerovisual$getCubes().isEmpty()) continue;
			ModelPart low = split(child.getValue(), cut);
			// the child of a split part hangs on the lower part of its parent instead
			Map<String, ModelPart> kept = new HashMap<>();
			if (c.zerovisual$getChildren().containsKey(JOINT)) kept.put(JOINT, c.zerovisual$getChildren().get(JOINT));
			c.zerovisual$setChildren(kept);
			low.setPos(0, 0, 0);
			low.setInitialPose(low.storePose());
			lowerChildren.put(child.getKey(), low);
			mirrored.add(new ModelPart[]{child.getValue(), low});
		}
		ModelPart low = new ModelPart(lower, lowerChildren);
		low.setPos(0, cut, 0);
		low.setInitialPose(low.storePose());
		Map<String, ModelPart> children = new HashMap<>(access.zerovisual$getChildren());
		children.put(LOWER, low);
		for (int i = firstChild; i < joints.size(); i++) joints.get(i).source = low;
		if (!faces.isEmpty()) {
			List<ModelPart.Polygon> quads = new ArrayList<>();
			for (Face face : faces) quads.addAll(List.of(face.quads));
			List<ModelPart.Cube> cubes = new ArrayList<>();
			for (int i = 0; i < quads.size(); i += 6) cubes.add(cube(quads.subList(i, Math.min(i + 6, quads.size()))));
			ModelPart band = new ModelPart(cubes, Map.of());
			band.visible = false;
			children.put(JOINT, band);
			Joint joint = new Joint(band, cut, faces);
			joint.source = low;
			joints.add(joint);
		}
		access.zerovisual$setChildren(children);
		access.zerovisual$setCubes(upper);
		return low;
	}

	private static ModelPart.Cube cube(List<ModelPart.Polygon> polygons) {
		Set<Direction> faces = EnumSet.noneOf(Direction.class);
		for (int i = 0; i < polygons.size(); i++) faces.add(Direction.values()[i]);
		ModelPart.Cube cube = new ModelPart.Cube(0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, false, 64, 64, faces);
		for (int i = 0; i < polygons.size(); i++) cube.polygons[i] = polygons.get(i);
		return cube;
	}

	private static ModelPart.Polygon shifted(ModelPart.Polygon polygon, float dy) {
		ModelPart.Vertex[] out = new ModelPart.Vertex[polygon.vertices().length];
		for (int i = 0; i < out.length; i++) {
			ModelPart.Vertex a = polygon.vertices()[i];
			out[i] = new ModelPart.Vertex(a.x(), a.y() - dy, a.z(), a.u(), a.v());
		}
		return new ModelPart.Polygon(out, polygon.normal());
	}

	/** The band under the clipped part of a side face: {@code clipped} is that part, its two points on the cut give the edge. */
	private static Face face(ModelPart.Polygon clipped, Vector3fc normal, float cut) {
		List<ModelPart.Vertex> edge = new ArrayList<>();
		for (ModelPart.Vertex a : clipped.vertices()) if (a.y() == cut) edge.add(a);
		ModelPart.Vertex a = edge.get(0), b = edge.get(1);
		float[] x = {b.x(), (a.x() + b.x()) / 2, a.x()}, z = {b.z(), (a.z() + b.z()) / 2, a.z()};
		float[] u = {b.u(), (a.u() + b.u()) / 2, a.u()};
		ModelPart.Polygon[] quads = new ModelPart.Polygon[4];
		Vector3f[] normals = new Vector3f[4];
		for (int i = 0; i < 4; i++) {
			normals[i] = new Vector3f(normal);
			ModelPart.Vertex[] corners = new ModelPart.Vertex[4];
			java.util.Arrays.fill(corners, a);
			quads[i] = new ModelPart.Polygon(corners, normals[i]);
		}
		return new Face(x, z, u, a.v() + ROW, normal, quads, normals);
	}

	/** Rebuilds the bands: two steps around each hinge, so the outside of a bend comes out round. */
	private void closeJoints() {
		for (Joint joint : joints) {
			ModelPart low = joint.source;
			boolean bent = Math.abs(low.xRot) + Math.abs(low.yRot) + Math.abs(low.zRot) > 0.01f;
			joint.band.visible = bent;
			if (!bent) continue;
			full.rotationZYX(low.zRot, low.yRot, low.xRot);
			half.identity().slerp(full, 0.5f);
			for (Face face : joint.faces) {
				for (int step = 0; step < 2; step++) {
					Quaternionf from = step == 0 ? null : half, to = step == 0 ? half : full;
					for (int side = 0; side < 2; side++) {
						ModelPart.Polygon quad = face.quads[step * 2 + side];
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
	private static ModelPart.Polygon flat(ModelPart.Polygon polygon, float y, float dy) {
		ModelPart.Vertex[] out = new ModelPart.Vertex[polygon.vertices().length];
		for (int i = 0; i < out.length; i++) {
			ModelPart.Vertex a = polygon.vertices()[i];
			out[i] = new ModelPart.Vertex(a.x(), y - dy, a.z(), a.u(), a.v());
		}
		return new ModelPart.Polygon(out, polygon.normal());
	}

	/** The part of a side face above (or below) the cut, with the texture coordinates carried along. */
	private static ModelPart.Polygon clip(ModelPart.Polygon polygon, float cut, boolean above) {
		ModelPart.Vertex[] in = polygon.vertices();
		List<ModelPart.Vertex> out = new ArrayList<>();
		for (int i = 0; i < in.length; i++) {
			ModelPart.Vertex a = in[i], b = in[(i + 1) % in.length];
			boolean aIn = above ? a.y() <= cut : a.y() >= cut, bIn = above ? b.y() <= cut : b.y() >= cut;
			if (aIn) out.add(a);
			if (aIn != bIn) {
				float k = (cut - a.y()) / (b.y() - a.y());
				out.add(new ModelPart.Vertex(Mth.lerp(k, a.x(), b.x()), cut, Mth.lerp(k, a.z(), b.z()),
					Mth.lerp(k, a.u(), b.u()), Mth.lerp(k, a.v(), b.v())));
			}
		}
		return new ModelPart.Polygon(out.toArray(new ModelPart.Vertex[0]), polygon.normal());
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
		part.xRot = part.yRot = part.zRot = 0;
	}

	/** Puts the model into the pose; {@code weight} fades between what the game set and the pose. */
	public void apply(EmotePose p, float w) {
		hip.identity().rotateZYX(p.hipRz * w, p.hipRy * w, p.hipRx * w);
		hip.transform(v.set(0, HIP_Y, 0));
		root.x = p.hipX * w - v.x;
		root.y = HIP_Y + p.hipY * w - v.y;
		root.z = p.hipZ * w - v.z;
		hip.getEulerAnglesZYX(euler);
		root.xRot = euler.x;
		root.yRot = euler.y;
		root.zRot = euler.z;

		// the body part is the chest, hanging from the waist; its lower half stays with the hips
		waist.identity().rotateZYX(p.waistRz * w, p.waistRy * w, p.waistRx * w);
		waist.transform(v.set(0, WAIST_Y, 0));
		body.x = -v.x;
		body.y = WAIST_Y - v.y;
		body.z = -v.z;
		waist.getEulerAnglesZYX(euler);
		body.xRot = euler.x;
		body.yRot = euler.y;
		body.zRot = euler.z;
		q.set(waist).conjugate().getEulerAnglesZYX(euler);
		bodyLow.xRot = euler.x;
		bodyLow.yRot = euler.y;
		bodyLow.zRot = euler.z;

		onChest(head, 0, 0, p.headRx, p.headRy, p.headRz, w);
		onChest(rightArm, -5, 2, p.rArmRx, p.rArmRy, p.rArmRz, w);
		onChest(leftArm, 5, 2, p.lArmRx, p.lArmRy, p.lArmRz, w);
		rightArmLow.xRot = p.rElbow * w;
		leftArmLow.xRot = p.lElbow * w;

		rightLeg.xRot = Mth.lerp(w, rightLeg.xRot, p.rLegRx);
		rightLeg.yRot = Mth.lerp(w, rightLeg.yRot, p.rLegRy);
		rightLeg.zRot = Mth.lerp(w, rightLeg.zRot, p.rLegRz);
		leftLeg.xRot = Mth.lerp(w, leftLeg.xRot, p.lLegRx);
		leftLeg.yRot = Mth.lerp(w, leftLeg.yRot, p.lLegRy);
		leftLeg.zRot = Mth.lerp(w, leftLeg.zRot, p.lLegRz);
		rightLeg.x = Mth.lerp(w, rightLeg.x, -1.9f);
		rightLeg.y = Mth.lerp(w, rightLeg.y, HIP_Y);
		rightLeg.z = Mth.lerp(w, rightLeg.z, 0);
		leftLeg.x = Mth.lerp(w, leftLeg.x, 1.9f);
		leftLeg.y = Mth.lerp(w, leftLeg.y, HIP_Y);
		leftLeg.z = Mth.lerp(w, leftLeg.z, 0);
		rightLegLow.xRot = p.rKnee * w;
		leftLegLow.xRot = p.lKnee * w;
		closeJoints();
	}

	/** The head and the arms ride on the chest: their joint moves and turns with the waist. */
	private void onChest(ModelPart part, float x, float y, float rx, float ry, float rz, float w) {
		waist.transform(v.set(x, y - WAIST_Y, 0));
		part.x = Mth.lerp(w, part.x, v.x);
		part.y = Mth.lerp(w, part.y, WAIST_Y + v.y);
		part.z = Mth.lerp(w, part.z, v.z);
		q.set(waist).rotateZYX(rz, ry, rx).getEulerAnglesZYX(euler);
		part.xRot = Mth.lerp(w, part.xRot, euler.x);
		part.yRot = Mth.lerp(w, part.yRot, euler.y);
		part.zRot = Mth.lerp(w, part.zRot, euler.z);
	}

	/** After the game moved the pose stack to the arm: on to the forearm, so held items stay in the hand. */
	public void toHand(HumanoidArm arm, PoseStack poseStack) {
		ModelPart low = arm == HumanoidArm.RIGHT ? rightArmLow : leftArmLow;
		low.translateAndRotate(poseStack);
		poseStack.translate(0, -ARM_CUT / 16, 0);
	}
}
