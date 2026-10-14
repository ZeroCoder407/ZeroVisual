package com.zerovisual.module.impl.cosmetics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.render.StarShape;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A puffy 3D logo star on the ground. It stays where it is until you are more than 4 blocks away,
 * then runs up to 1 block from you: the two lower rays are its legs, it hops with every step,
 * leans forward and turns where it runs. Only you see it.
 */
public class StarPet extends Module {
	/** Ray angles, tip up: 0 top, 1 left arm, 2 left leg, 3 right leg, 4 right arm. */
	private static final float INNER = 0.47f;
	private static final float DEPTH = 0.42f;
	private static final Vector3f LIGHT = new Vector3f(0.35f, 0.9f, 0.45f).normalize();
	/** Starts running when the player is farther than this, stops this close to them, teleports beyond the last. */
	private static final double START = 4, STOP = 1, TELEPORT = 24;

	public final NumberSetting size = add(new NumberSetting("Size", 1, 0.5, 2, 0.1));

	private Vec3d pos, prevPos;
	private double fallSpeed;
	private boolean chasing;
	private float yaw, prevYaw;
	/** Walk cycle, advanced by the distance run. */
	private float phase, prevPhase;
	/** 0 standing .. 1 running, smoothed. */
	private float run, prevRun;

	public StarPet() {
		super("StarPet", "A 3D star that runs up to you when you are more than 4 blocks away (only you see it)", Category.COSMETICS);
	}

	@Override
	protected void onDisable() {
		pos = null;
	}

	private float radius() {
		return 0.3f * size.getFloat();
	}

	// ---- movement (20 times per second) ----

	@Override
	public void onTick() {
		if (mc.player == null || mc.world == null) {
			pos = null;
			return;
		}
		Vec3d player = mc.player.getPos();
		// horizontal distance only: flying high above it does not make it teleport
		if (pos == null || horizontalDistance(pos, player) > TELEPORT) {
			double yawRad = Math.toRadians(mc.player.getBodyYaw());
			// (re)appear one block behind the player, on the ground below them
			double x = player.x + Math.sin(yawRad) * STOP, z = player.z - Math.cos(yawRad) * STOP;
			double ground = groundY(x, z, player.y);
			pos = new Vec3d(x, Double.isNaN(ground) ? player.y : ground, z);
			prevPos = pos;
			fallSpeed = 0;
			chasing = false;
		}
		prevPos = pos;
		prevYaw = yaw;
		prevPhase = phase;
		prevRun = run;

		// stays where it is until the player is more than 4 blocks away, then runs up to 1 block from them
		double dx = player.x - pos.x, dz = player.z - pos.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist > START) chasing = true;
		double left = dist - STOP;
		if (left <= 0.05) chasing = false;
		double step = chasing ? Math.min(Math.max(left * 0.3, 0.12), Math.min(0.42, left)) : 0;
		double nx = pos.x + (step > 0 ? dx / dist * step : 0), nz = pos.z + (step > 0 ? dz / dist * step : 0);

		// the ground under the star itself (not the player): it keeps running on the ground while you fly
		double ground = groundY(nx, nz, pos.y);
		if (Double.isNaN(ground)) ground = pos.y;
		double ny;
		if (ground >= pos.y - 0.01) {
			// steps up instantly (the hop hides it)
			ny = Math.min(ground, pos.y + 1.1);
			fallSpeed = 0;
		} else {
			fallSpeed = Math.min(fallSpeed + 0.08, 1.5);
			ny = Math.max(ground, pos.y - fallSpeed);
		}
		pos = new Vec3d(nx, ny, nz);

		run += ((step > 0.02 ? 1f : 0f) - run) * 0.3f;
		phase += (float) step * 4.2f;
		// face where it runs, or the player when standing
		float wanted = step > 0.02
			? (float) Math.toDegrees(Math.atan2(-dx, dz))
			: (float) Math.toDegrees(Math.atan2(-(player.x - pos.x), player.z - pos.z));
		yaw += MathHelper.wrapDegrees(wanted - yaw) * 0.35f;
	}

	private static double horizontalDistance(Vec3d a, Vec3d b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Top of the first block with collision from {@code fromY + 2} down (up to 64 blocks), or NaN. */
	private double groundY(double x, double z, double fromY) {
		BlockPos.Mutable p = new BlockPos.Mutable(MathHelper.floor(x), MathHelper.floor(fromY) + 2, MathHelper.floor(z));
		for (int i = 0; i < 66 && p.getY() >= mc.world.getBottomY(); i++, p.move(Direction.DOWN)) {
			BlockState state = mc.world.getBlockState(p);
			VoxelShape shape = state.getCollisionShape(mc.world, p);
			if (!shape.isEmpty()) return p.getY() + shape.getMax(Direction.Axis.Y);
		}
		return Double.NaN;
	}

	// ---- rendering ----

	public void render(WorldRenderContext context) {
		if (pos == null || prevPos == null || mc.player == null) return;
		float delta = context.tickCounter().getTickDelta(false);
		Vec3d p = prevPos.lerp(pos, delta);
		float yawNow = prevYaw + MathHelper.wrapDegrees(yaw - prevYaw) * delta;
		float cycle = MathHelper.lerp(delta, prevPhase, phase);
		float running = MathHelper.lerp(delta, prevRun, run);
		float time = (System.currentTimeMillis() % 100_000L) / 1000f;
		Vec3d cam = context.camera().getPos();
		float r = radius();

		// a hop on every step while running, slow breathing while standing
		float hop = Math.abs(MathHelper.sin(cycle)) * 0.16f * r * 3 * running;
		float breathe = (1 - running) * MathHelper.sin(time * 2.5f) * 0.03f;
		float lean = running * 14f;
		float legSwing = MathHelper.sin(cycle) * running;

		// legs reach the ground: the lowest outline point is sin(54 deg) * r below the center
		float centerY = r * 0.81f + hop;

		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.disableCull();

		drawShadow(p, cam, r, hop);

		Matrix4f model = new Matrix4f()
			.translate((float) (p.x - cam.x), (float) (p.y - cam.y) + centerY, (float) (p.z - cam.z))
			.rotateY((float) Math.toRadians(-yawNow))
			.rotateX((float) Math.toRadians(lean))
			.scale(1 + breathe, 1 - breathe, 1 + breathe);
		drawBody(model, r, legSwing, running, time);

		RenderSystem.enableCull();
	}

	private void drawShadow(Vec3d p, Vec3d cam, float r, float hop) {
		float s = r * (1.1f - hop * 0.8f);
		Matrix4f m = new Matrix4f()
			.translate((float) (p.x - cam.x), (float) (p.y - cam.y) + 0.015f, (float) (p.z - cam.z))
			.rotateX((float) Math.toRadians(90));
		int c = 0x55000000;
		RenderSystem.depthMask(false);
		Render2D.shape(m, -s, -s * 0.7f, s * 2, s * 1.4f, s, 0, s * 0.5f, c, c, c, c);
		RenderSystem.depthMask(true);
	}

	/** Outline point {@code i} (0..9, even = tips) of the star in the XY plane, with the legs swinging. */
	private static Vector3f outline(int i, float r, float legSwing, float running, float time) {
		double angle = Math.PI / 2 + i * Math.PI / 5;
		boolean tip = i % 2 == 0;
		float len = tip ? r : r * INNER;
		Vector3f v = new Vector3f((float) Math.cos(angle) * len, (float) Math.sin(angle) * len, 0);
		if (tip) {
			int ray = i / 2;
			if (ray == 2) v.z += legSwing * r * 0.55f;
			if (ray == 3) v.z -= legSwing * r * 0.55f;
			// arms swing opposite to the legs, and wave a little when standing
			float arm = -legSwing * r * 0.35f + (1 - running) * MathHelper.sin(time * 3) * r * 0.06f;
			if (ray == 1) v.z += arm;
			if (ray == 4) v.z -= arm;
			if (ray == 2 || ray == 3) v.y += Math.max(0, (ray == 2 ? legSwing : -legSwing)) * r * 0.2f;
		}
		return v;
	}

	private void drawBody(Matrix4f model, float r, float legSwing, float running, float time) {
		Vector3f[] ring = new Vector3f[10];
		for (int i = 0; i < 10; i++) ring[i] = outline(i, r, legSwing, running, time);
		Vector3f front = new Vector3f(0, 0, r * DEPTH), back = new Vector3f(0, 0, -r * DEPTH);

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
		for (int i = 0; i < 10; i++) {
			Vector3f a = ring[i], c = ring[(i + 1) % 10];
			triangle(b, model, front, a, c, r);
			triangle(b, model, back, c, a, r);
		}
		BufferRenderer.drawWithGlobalProgram(b.end());
		RenderSystem.disableBlend();
	}

	/** Flat-shaded triangle: the logo gradient by x, lit by a fixed light in world space. */
	private static void triangle(BufferBuilder b, Matrix4f model, Vector3f p0, Vector3f p1, Vector3f p2, float r) {
		Vector3f normal = new Vector3f(p1).sub(p0).cross(new Vector3f(p2).sub(p0)).normalize();
		// rotate the normal with the model (no translation) to light it in world space
		model.transformDirection(normal).normalize();
		float light = 0.55f + 0.45f * Math.max(0, Math.abs(normal.dot(LIGHT)));
		vertex(b, model, p0, r, light);
		vertex(b, model, p1, r, light);
		vertex(b, model, p2, r, light);
	}

	private static void vertex(BufferBuilder b, Matrix4f model, Vector3f p, float r, float light) {
		float t = Math.max(0f, Math.min(1f, (p.x + r) / (2 * r)));
		int c = ColorUtil.lerp(StarShape.LEFT, StarShape.RIGHT, t);
		int red = Math.round(((c >> 16) & 0xFF) * light), green = Math.round(((c >> 8) & 0xFF) * light), blue = Math.round((c & 0xFF) * light);
		b.vertex(model, p.x, p.y, p.z).color(0xFF000000 | red << 16 | green << 8 | blue);
	}
}
