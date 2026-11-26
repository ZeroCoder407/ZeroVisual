package com.zerovisual.util;

import com.zerovisual.emote.EmotePose;
import com.zerovisual.mixin.ModelBoxAccessor;
import com.zerovisual.mixin.ModelRendererAccessor;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Bendable limbs of one player or armor model. The arms, legs and body are cut in two at the middle, keeping their
 * skin mapping: the upper half stays in the vanilla part, the lower half becomes a child with its hinge at the cut.
 * With no rotation it looks exactly like the vanilla model, so sleeves, pants, the jacket and armor just work.
 * While a joint bends, a band of faces rebuilt every frame closes the gap around it, so a limb stays one piece.
 * <p>
 * 1.8.9 has no root part: the hips are a GL transform around the whole model ({@link #applyHips}), and the sleeves
 * of ModelPlayer are parts of their own that copy the arm angles; their lower halves follow in {@link #syncWear}.
 */
public final class EmoteRig {
	/** Half a texture row: the skin row just under the cut stretches over the bend. */
	private static final float ARM_CUT = 4, LEG_CUT = 6, BODY_CUT = 6;
	/** Where the parts hang in the vanilla pose. */
	private static final float WAIST_Y = 6, HIP_Y = 12;

	private final ModelBiped model;
	private final ModelRenderer head, body, rightArm, leftArm, rightLeg, leftLeg;
	private final Part bodyLow, rightArmLow, leftArmLow, rightLegLow, leftLegLow;
	/** Sleeves, pants and jacket of ModelPlayer with their lower halves, following the arm, leg or body. */
	private final List<Part[]> wear = new ArrayList<Part[]>();
	private final List<Joint> joints = new ArrayList<Joint>();
	private final float rightArmY, leftArmY, rightLegX, leftLegX;

	/** The hip transform for this frame: pivot in model units, angles in radians (Z, Y, X order). */
	private float hipX, hipY, hipZ, hipPitch, hipYaw, hipRoll;
	private boolean posed;

	private final Quaternionf hip = new Quaternionf(), waist = new Quaternionf(), q = new Quaternionf();
	private final Quaternionf full = new Quaternionf(), half = new Quaternionf();
	private final Vector3f v = new Vector3f(), euler = new Vector3f();

	public EmoteRig(ModelBiped model) {
		this.model = model;
		head = model.bipedHead;
		body = model.bipedBody;
		rightArm = model.bipedRightArm;
		leftArm = model.bipedLeftArm;
		rightLeg = model.bipedRightLeg;
		leftLeg = model.bipedLeftLeg;
		rightArmY = rightArm.rotationPointY;
		leftArmY = leftArm.rotationPointY;
		rightLegX = rightLeg.rotationPointX;
		leftLegX = leftLeg.rotationPointX;
		bodyLow = split(body, BODY_CUT);
		rightArmLow = split(rightArm, ARM_CUT);
		leftArmLow = split(leftArm, ARM_CUT);
		rightLegLow = split(rightLeg, LEG_CUT);
		leftLegLow = split(leftLeg, LEG_CUT);
		if (model instanceof ModelPlayer) {
			ModelPlayer player = (ModelPlayer) model;
			wear(player.bipedBodyWear, BODY_CUT, bodyLow);
			wear(player.bipedRightArmwear, ARM_CUT, rightArmLow);
			wear(player.bipedLeftArmwear, ARM_CUT, leftArmLow);
			wear(player.bipedRightLegwear, LEG_CUT, rightLegLow);
			wear(player.bipedLeftLegwear, LEG_CUT, leftLegLow);
		}
	}

	private void wear(ModelRenderer part, float cut, Part follows) {
		Part low = split(part, cut);
		if (low != null) wear.add(new Part[]{low, follows});
	}

	// ---- geometry ----

	/** A face of the model: four corners in model units with texture coordinates already divided by the texture size. */
	static final class Quad {
		final float[] x, y, z, u, v;

		Quad(int n) {
			x = new float[n];
			y = new float[n];
			z = new float[n];
			u = new float[n];
			v = new float[n];
		}

		int size() {
			return x.length;
		}

		static Quad of(TexturedQuad quad) {
			Quad out = new Quad(quad.nVertices);
			for (int i = 0; i < quad.nVertices; i++) {
				PositionTextureVertex p = quad.vertexPositions[i];
				out.x[i] = (float) p.vector3D.xCoord;
				out.y[i] = (float) p.vector3D.yCoord;
				out.z[i] = (float) p.vector3D.zCoord;
				out.u[i] = p.texturePositionX;
				out.v[i] = p.texturePositionY;
			}
			return out;
		}

		/** As TexturedQuad#draw: the normal from two edges, one quad per draw call. */
		void draw(float scale) {
			if (size() < 3) return;
			float ax = x[0] - x[1], ay = y[0] - y[1], az = z[0] - z[1];
			float bx = x[2] - x[1], by = y[2] - y[1], bz = z[2] - z[1];
			float nx = by * az - bz * ay, ny = bz * ax - bx * az, nz = bx * ay - by * ax;
			float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (l > 1e-6f) {
				nx /= l;
				ny /= l;
				nz /= l;
			}
			WorldRenderer b = Tessellator.getInstance().getWorldRenderer();
			b.begin(GL11.GL_QUADS, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL);
			for (int i = 0; i < 4; i++) {
				int k = Math.min(i, size() - 1);
				b.pos(x[k] * scale, y[k] * scale, z[k] * scale).tex(u[k], v[k]).normal(nx, ny, nz).endVertex();
			}
			Tessellator.getInstance().draw();
		}
	}

	/** A box that draws the faces it is given (the vanilla ModelBox builds its own from a size). */
	static final class QuadBox extends ModelBox {
		final List<Quad> quads;

		QuadBox(ModelRenderer owner, List<Quad> quads) {
			super(owner, 0, 0, 0, 0, 0, 0, 0, 0, 0);
			this.quads = quads;
		}

		@Override
		public void render(WorldRenderer renderer, float scale) {
			for (Quad quad : quads) quad.draw(scale);
		}
	}

	/**
	 * A part drawn right away every frame instead of from a display list, so faces that change (the bands) are
	 * current; transforms as the vanilla ModelRenderer.
	 */
	static final class Part extends ModelRenderer {
		final List<Quad> quads;

		Part(ModelBase base, List<Quad> quads) {
			super(base);
			this.quads = quads;
			// arrows stuck in a player pick a random part with boxes; these have none
			base.boxList.remove(this);
		}

		@Override
		public void render(float scale) {
			if (isHidden || !showModel) return;
			GlStateManager.pushMatrix();
			GlStateManager.translate(offsetX, offsetY, offsetZ);
			GlStateManager.translate(rotationPointX * scale, rotationPointY * scale, rotationPointZ * scale);
			if (rotateAngleZ != 0) GlStateManager.rotate((float) Math.toDegrees(rotateAngleZ), 0, 0, 1);
			if (rotateAngleY != 0) GlStateManager.rotate((float) Math.toDegrees(rotateAngleY), 0, 1, 0);
			if (rotateAngleX != 0) GlStateManager.rotate((float) Math.toDegrees(rotateAngleX), 1, 0, 0);
			for (Quad quad : quads) quad.draw(scale);
			if (childModels != null) for (ModelRenderer child : childModels) child.render(scale);
			GlStateManager.popMatrix();
		}
	}

	/** One side of a cut limb: three points along the cut, around the hinge, and the faces between them. */
	private static final class Face {
		final float[] x, z, u;
		final float v;
		final Quad[] quads;

		Face(float[] x, float[] z, float[] u, float v) {
			this.x = x;
			this.z = z;
			this.u = u;
			this.v = v;
			this.quads = new Quad[4];
			for (int i = 0; i < 4; i++) quads[i] = new Quad(4);
		}
	}

	/** The band of one cut. {@code source} is the part that really turns: sleeves follow the arm. */
	private static final class Joint {
		final Part band;
		final float cut;
		final List<Face> faces;
		final ModelRenderer source;
		final float rowHeight;

		Joint(Part band, float cut, List<Face> faces, ModelRenderer source, float rowHeight) {
			this.band = band;
			this.cut = cut;
			this.faces = faces;
			this.source = source;
			this.rowHeight = rowHeight;
		}
	}

	/** Moves everything from below {@code cut} into a new child part hinged there; returns that child (null for an empty part). */
	private Part split(ModelRenderer part, float cut) {
		if (part == null || part.cubeList.isEmpty()) return null;
		List<Quad> upper = new ArrayList<Quad>(), lower = new ArrayList<Quad>();
		List<Face> faces = new ArrayList<Face>();
		for (ModelBox box : part.cubeList) {
			Quad top = null, bottom = null;
			for (TexturedQuad tq : ((ModelBoxAccessor) box).zerovisual$getQuadList()) {
				Quad polygon = Quad.of(tq);
				float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
				for (int i = 0; i < polygon.size(); i++) {
					min = Math.min(min, polygon.y[i]);
					max = Math.max(max, polygon.y[i]);
				}
				if (max <= cut) {
					upper.add(polygon);
					if (min == max) top = polygon;
				} else if (min >= cut) {
					lower.add(shifted(polygon, cut));
					if (min == max) bottom = polygon;
				} else {
					Quad clipped = clip(polygon, cut, true);
					upper.add(clipped);
					Face face = face(clipped, cut, part.textureHeight);
					if (face != null) faces.add(face);
					lower.add(shifted(clip(polygon, cut, false), cut));
				}
			}
			// caps close the hole at the joint when the limb bends
			if (bottom != null) upper.add(flat(bottom, cut, 0));
			if (top != null) lower.add(flat(top, cut, cut));
		}
		ModelBase base = model;
		Part low = new Part(base, lower);
		low.setRotationPoint(0, cut, 0);
		part.addChild(low);
		if (!faces.isEmpty()) {
			Part band = new Part(base, new ArrayList<Quad>());
			for (Face face : faces) for (Quad quad : face.quads) band.quads.add(quad);
			band.showModel = false;
			part.addChild(band);
			joints.add(new Joint(band, cut, faces, low, 0.5f / part.textureHeight));
		}
		part.cubeList.clear();
		part.cubeList.add(new QuadBox(part, upper));
		((ModelRendererAccessor) part).zerovisual$setCompiled(false);
		return low;
	}

	private static Quad shifted(Quad polygon, float dy) {
		Quad out = new Quad(polygon.size());
		for (int i = 0; i < out.size(); i++) {
			out.x[i] = polygon.x[i];
			out.y[i] = polygon.y[i] - dy;
			out.z[i] = polygon.z[i];
			out.u[i] = polygon.u[i];
			out.v[i] = polygon.v[i];
		}
		return out;
	}

	/** The band under the clipped part of a side face: its two points on the cut give the edge. */
	private static Face face(Quad clipped, float cut, float textureHeight) {
		int a = -1, b = -1;
		for (int i = 0; i < clipped.size(); i++) {
			if (clipped.y[i] != cut) continue;
			if (a < 0) a = i;
			else if (b < 0) b = i;
		}
		if (a < 0 || b < 0) return null;
		float[] x = {clipped.x[b], (clipped.x[a] + clipped.x[b]) / 2, clipped.x[a]};
		float[] z = {clipped.z[b], (clipped.z[a] + clipped.z[b]) / 2, clipped.z[a]};
		float[] u = {clipped.u[b], (clipped.u[a] + clipped.u[b]) / 2, clipped.u[a]};
		return new Face(x, z, u, clipped.v[a] + 0.5f / textureHeight);
	}

	/** Rebuilds the bands: two steps around each hinge, so the outside of a bend comes out round. */
	private void closeJoints() {
		for (Joint joint : joints) {
			ModelRenderer low = joint.source;
			boolean bent = Math.abs(low.rotateAngleX) + Math.abs(low.rotateAngleY) + Math.abs(low.rotateAngleZ) > 0.01f;
			joint.band.showModel = bent;
			if (!bent) continue;
			full.rotationZYX(low.rotateAngleZ, low.rotateAngleY, low.rotateAngleX);
			half.identity().slerp(full, 0.5f);
			for (Face face : joint.faces) {
				for (int step = 0; step < 2; step++) {
					Quaternionf from = step == 0 ? null : half, to = step == 0 ? half : full;
					for (int side = 0; side < 2; side++) {
						Quad quad = face.quads[step * 2 + side];
						ring(quad, 0, face, side, from, joint.cut);
						ring(quad, 1, face, side + 1, from, joint.cut);
						ring(quad, 2, face, side + 1, to, joint.cut);
						ring(quad, 3, face, side, to, joint.cut);
					}
				}
			}
		}
	}

	private void ring(Quad quad, int corner, Face face, int i, Quaternionf turn, float cut) {
		v.set(face.x[i], 0, face.z[i]);
		if (turn != null) turn.transform(v);
		quad.x[corner] = v.x;
		quad.y[corner] = v.y + cut;
		quad.z[corner] = v.z;
		quad.u[corner] = face.u[i];
		quad.v[corner] = face.v;
	}

	/** A copy of a flat face moved to height {@code y}, then shifted by {@code dy}. */
	private static Quad flat(Quad polygon, float y, float dy) {
		Quad out = new Quad(polygon.size());
		for (int i = 0; i < out.size(); i++) {
			out.x[i] = polygon.x[i];
			out.y[i] = y - dy;
			out.z[i] = polygon.z[i];
			out.u[i] = polygon.u[i];
			out.v[i] = polygon.v[i];
		}
		return out;
	}

	/** The part of a side face above (or below) the cut, with the texture coordinates carried along. */
	private static Quad clip(Quad polygon, float cut, boolean above) {
		int n = polygon.size();
		List<float[]> out = new ArrayList<float[]>();
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			boolean aIn = above ? polygon.y[i] <= cut : polygon.y[i] >= cut, bIn = above ? polygon.y[j] <= cut : polygon.y[j] >= cut;
			if (aIn) out.add(new float[]{polygon.x[i], polygon.y[i], polygon.z[i], polygon.u[i], polygon.v[i]});
			if (aIn != bIn) {
				float k = (cut - polygon.y[i]) / (polygon.y[j] - polygon.y[i]);
				out.add(new float[]{Mth.lerp(k, polygon.x[i], polygon.x[j]), cut, Mth.lerp(k, polygon.z[i], polygon.z[j]),
					Mth.lerp(k, polygon.u[i], polygon.u[j]), Mth.lerp(k, polygon.v[i], polygon.v[j])});
			}
		}
		Quad q = new Quad(out.size());
		for (int i = 0; i < out.size(); i++) {
			float[] p = out.get(i);
			q.x[i] = p[0];
			q.y[i] = p[1];
			q.z[i] = p[2];
			q.u[i] = p[3];
			q.v[i] = p[4];
		}
		return q;
	}

	// ---- pose ----

	/** Every frame after the vanilla angles: the lower halves back to rest, and what vanilla never sets back to default. */
	public void rest() {
		rest(bodyLow);
		rest(rightArmLow);
		rest(leftArmLow);
		rest(rightLegLow);
		rest(leftLegLow);
		body.rotationPointX = body.rotationPointY = body.rotationPointZ = 0;
		body.rotateAngleZ = 0;
		head.rotateAngleZ = 0;
		// 1.8.9 sets the X and Y of the legs every frame, never Z
		rightLeg.rotateAngleZ = 0;
		leftLeg.rotateAngleZ = 0;
		rightArm.rotationPointY = rightArmY;
		leftArm.rotationPointY = leftArmY;
		rightLeg.rotationPointX = rightLegX;
		leftLeg.rotationPointX = leftLegX;
		for (Joint joint : joints) joint.band.showModel = false;
		posed = false;
	}

	private static void rest(ModelRenderer part) {
		if (part != null) part.rotateAngleX = part.rotateAngleY = part.rotateAngleZ = 0;
	}

	/** Puts the model into the pose; {@code w} fades between what the game set and the pose. */
	public void apply(EmotePose p, float w) {
		hip.identity().rotateZYX(p.hipRz * w, p.hipRy * w, p.hipRx * w);
		hip.transform(v.set(0, HIP_Y, 0));
		hipX = p.hipX * w - v.x;
		hipY = HIP_Y + p.hipY * w - v.y;
		hipZ = p.hipZ * w - v.z;
		hip.getEulerAnglesZYX(euler);
		hipPitch = euler.x;
		hipYaw = euler.y;
		hipRoll = euler.z;
		posed = true;

		// the body part is the chest, hanging from the waist; its lower half stays with the hips
		waist.identity().rotateZYX(p.waistRz * w, p.waistRy * w, p.waistRx * w);
		waist.transform(v.set(0, WAIST_Y, 0));
		body.rotationPointX = -v.x;
		body.rotationPointY = WAIST_Y - v.y;
		body.rotationPointZ = -v.z;
		waist.getEulerAnglesZYX(euler);
		body.rotateAngleX = euler.x;
		body.rotateAngleY = euler.y;
		body.rotateAngleZ = euler.z;
		q.set(waist).conjugate().getEulerAnglesZYX(euler);
		if (bodyLow != null) {
			bodyLow.rotateAngleX = euler.x;
			bodyLow.rotateAngleY = euler.y;
			bodyLow.rotateAngleZ = euler.z;
		}

		onChest(head, 0, 0, p.headRx, p.headRy, p.headRz, w);
		onChest(rightArm, -5, 2, p.rArmRx, p.rArmRy, p.rArmRz, w);
		onChest(leftArm, 5, 2, p.lArmRx, p.lArmRy, p.lArmRz, w);
		if (rightArmLow != null) rightArmLow.rotateAngleX = p.rElbow * w;
		if (leftArmLow != null) leftArmLow.rotateAngleX = p.lElbow * w;

		rightLeg.rotateAngleX = Mth.lerp(w, rightLeg.rotateAngleX, p.rLegRx);
		rightLeg.rotateAngleY = Mth.lerp(w, rightLeg.rotateAngleY, p.rLegRy);
		rightLeg.rotateAngleZ = Mth.lerp(w, rightLeg.rotateAngleZ, p.rLegRz);
		leftLeg.rotateAngleX = Mth.lerp(w, leftLeg.rotateAngleX, p.lLegRx);
		leftLeg.rotateAngleY = Mth.lerp(w, leftLeg.rotateAngleY, p.lLegRy);
		leftLeg.rotateAngleZ = Mth.lerp(w, leftLeg.rotateAngleZ, p.lLegRz);
		rightLeg.rotationPointX = Mth.lerp(w, rightLeg.rotationPointX, -1.9f);
		rightLeg.rotationPointY = Mth.lerp(w, rightLeg.rotationPointY, HIP_Y);
		rightLeg.rotationPointZ = Mth.lerp(w, rightLeg.rotationPointZ, 0);
		leftLeg.rotationPointX = Mth.lerp(w, leftLeg.rotationPointX, 1.9f);
		leftLeg.rotationPointY = Mth.lerp(w, leftLeg.rotationPointY, HIP_Y);
		leftLeg.rotationPointZ = Mth.lerp(w, leftLeg.rotationPointZ, 0);
		if (rightLegLow != null) rightLegLow.rotateAngleX = p.rKnee * w;
		if (leftLegLow != null) leftLegLow.rotateAngleX = p.lKnee * w;
		// vanilla copied the head into the hat before the pose was set
		ModelBiped.copyModelAngles(head, model.bipedHeadwear);
		closeJoints();
	}

	/** The head and the arms ride on the chest: their joint moves and turns with the waist. */
	private void onChest(ModelRenderer part, float x, float y, float rx, float ry, float rz, float w) {
		waist.transform(v.set(x, y - WAIST_Y, 0));
		part.rotationPointX = Mth.lerp(w, part.rotationPointX, v.x);
		part.rotationPointY = Mth.lerp(w, part.rotationPointY, WAIST_Y + v.y);
		part.rotationPointZ = Mth.lerp(w, part.rotationPointZ, v.z);
		q.set(waist).rotateZYX(rz, ry, rx).getEulerAnglesZYX(euler);
		part.rotateAngleX = Mth.lerp(w, part.rotateAngleX, euler.x);
		part.rotateAngleY = Mth.lerp(w, part.rotateAngleY, euler.y);
		part.rotateAngleZ = Mth.lerp(w, part.rotateAngleZ, euler.z);
	}

	/** After ModelPlayer copied the limb angles into the sleeves: their lower halves bend the same way. */
	public void syncWear() {
		for (Part[] pair : wear) {
			pair[0].rotateAngleX = pair[1].rotateAngleX;
			pair[0].rotateAngleY = pair[1].rotateAngleY;
			pair[0].rotateAngleZ = pair[1].rotateAngleZ;
		}
		closeJoints();
	}

	/** Around the whole model while it is drawn: the hips of the pose. Pair with {@link GlStateManager#popMatrix()}. */
	/** Into the space of the chest, as the body part is drawn: the cape hangs from it. False when not posed. */
	public boolean toChest(float scale) {
		if (!posed) return false;
		GlStateManager.translate(hipX * scale, hipY * scale, hipZ * scale);
		if (hipRoll != 0) GlStateManager.rotate((float) Math.toDegrees(hipRoll), 0, 0, 1);
		if (hipYaw != 0) GlStateManager.rotate((float) Math.toDegrees(hipYaw), 0, 1, 0);
		if (hipPitch != 0) GlStateManager.rotate((float) Math.toDegrees(hipPitch), 1, 0, 0);
		body.postRender(scale);
		return true;
	}

	public void applyHips(float scale) {
		GlStateManager.pushMatrix();
		if (!posed) return;
		GlStateManager.translate(hipX * scale, hipY * scale, hipZ * scale);
		if (hipRoll != 0) GlStateManager.rotate((float) Math.toDegrees(hipRoll), 0, 0, 1);
		if (hipYaw != 0) GlStateManager.rotate((float) Math.toDegrees(hipYaw), 0, 1, 0);
		if (hipPitch != 0) GlStateManager.rotate((float) Math.toDegrees(hipPitch), 1, 0, 0);
	}

	/** After the game moved the GL matrix to the arm: on to the forearm, so held items stay in the hand. */
	public void toHand(boolean right, float scale) {
		ModelRenderer low = right ? rightArmLow : leftArmLow;
		if (low == null) return;
		low.postRender(scale);
		GlStateManager.translate(0, -ARM_CUT * scale, 0);
	}
}
