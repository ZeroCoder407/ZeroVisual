package com.zerovisual.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

/**
 * Helpers for effects drawn in the world. Call {@link #begin} at the start of WorldRenderEvents.BEFORE_ENTITIES;
 * every draw is then submitted to the frame's SubmitNodeCollector and rendered later by the game.
 * Positions are camera-relative (pos - camera), the camera rotation is applied by the game.
 * <p>
 * Submissions run later, so everything they use is copied at submit time.
 */
public final class WorldRender {
	private static final PoseStack IDENTITY = new PoseStack();
	private static @Nullable SubmitNodeCollector collector;
	private static @Nullable CameraRenderState cameraState;

	private WorldRender() {
	}

	/** Starts a frame of world effects. */
	public static void begin(WorldRenderContext context) {
		collector = context.commandQueue();
		cameraState = context.worldState().cameraRenderState;
	}

	/** The camera as the game passes it to entity renderers (for submitting entity models). */
	public static @Nullable CameraRenderState cameraState() {
		return cameraState;
	}

	public static void end() {
		collector = null;
	}

	/** The collector of the current frame (null outside of world rendering). */
	public static @Nullable SubmitNodeCollector collector() {
		return collector;
	}

	/** Runs {@code draw} with a given collector, e.g. from inside a vanilla entity renderer submit. */
	public static void withCollector(SubmitNodeCollector target, Runnable draw) {
		SubmitNodeCollector previous = collector;
		collector = target;
		try {
			draw.run();
		} finally {
			collector = previous;
		}
	}

	/** Matrix at a world position, facing the camera. */
	public static Matrix4f billboard(Vec3 pos, Vec3 cam, Quaternionf cameraRotation) {
		return new Matrix4f()
			.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z))
			.rotate(cameraRotation);
	}

	/** Matrix lying flat (XZ plane) at a world position. */
	public static Matrix4f flat(Vec3 pos, Vec3 cam) {
		return new Matrix4f()
			.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z))
			.rotateX((float) Math.toRadians(90));
	}

	/** Rounded shape (see Render2D) with a 3D matrix; hidden behind blocks unless {@code seeThrough}. */
	public static void shape(Matrix4fc m, float x, float y, float w, float h, float radius, float thickness, float softness,
							 int topLeft, int topRight, int bottomRight, int bottomLeft, boolean seeThrough) {
		if (collector == null || w <= 0 || h <= 0) return;
		Matrix4f pose = new Matrix4f(m);
		int c0 = Render2D.applyAlpha(topLeft), c1 = Render2D.applyAlpha(topRight), c2 = Render2D.applyAlpha(bottomRight), c3 = Render2D.applyAlpha(bottomLeft);
		RenderType type = seeThrough ? ZvPipelines.WORLD_SHAPE_SEE_THROUGH_TYPE : ZvPipelines.WORLD_SHAPE_TYPE;
		collector.submitCustomGeometry(IDENTITY, type,
			(p, buffer) -> Render2D.shapeVertices(buffer, null, pose, x, y, w, h, radius, thickness, softness, c0, c1, c2, c3));
	}

	public static void shape(Matrix4fc m, float x, float y, float w, float h, float radius, float thickness, float softness,
							 int topLeft, int topRight, int bottomRight, int bottomLeft) {
		shape(m, x, y, w, h, radius, thickness, softness, topLeft, topRight, bottomRight, bottomLeft, false);
	}

	/** Soft glowing dot: a blurred halo plus a solid core. */
	public static void glowDot(Matrix4fc m, float radius, int color) {
		int halo = ColorUtil.fade(color, 0.45f);
		shape(m, -radius * 3, -radius * 3, radius * 6, radius * 6, radius * 3, 0, radius * 2.5f, halo, halo, halo, halo);
		shape(m, -radius, -radius, radius * 2, radius * 2, radius, 0, 0, color, color, color, color);
	}

	/** Ring with a gradient and a soft glow. */
	public static void ring(Matrix4fc m, float radius, float thickness, int colorA, int colorB, float glow) {
		if (glow > 0) {
			int ga = ColorUtil.fade(colorA, 0.35f), gb = ColorUtil.fade(colorB, 0.35f);
			shape(m, -radius, -radius, radius * 2, radius * 2, radius, 0, glow, ga, gb, gb, ga);
		}
		shape(m, -radius, -radius, radius * 2, radius * 2, radius, thickness, 0, colorA, colorB, colorB, colorA);
	}

	/**
	 * Free geometry in camera-relative coordinates, drawn as quads (a triangle is a quad with its last vertex repeated).
	 * The builder runs later: it must only use values that do not change after this call.
	 */
	public static void geometry(RenderType type, java.util.function.Consumer<VertexConsumer> builder) {
		if (collector == null) return;
		collector.submitCustomGeometry(IDENTITY, type, (p, buffer) -> builder.accept(buffer));
	}

	/** Nothing to do here: everything goes through the collector (1.21.4 draws queued blocks and text at this point). */
	public static void flush() {
	}

	/** Colored quads with normal blending, hidden behind blocks. */
	public static void quads(java.util.function.Consumer<com.zerovisual.fx.Quads> body) {
		geometry(ZvPipelines.WORLD_COLOR_TYPE, b -> body.accept((m, x, y, z, c) -> b.addVertex(m, x, y, z).setColor(c)));
	}

	/** Colored quads that add light. */
	public static void quadsAdditive(java.util.function.Consumer<com.zerovisual.fx.Quads> body) {
		geometry(ZvPipelines.WORLD_COLOR_ADDITIVE_TYPE, b -> body.accept((m, x, y, z, c) -> b.addVertex(m, x, y, z).setColor(c)));
	}

	/**
	 * The top of a block bent by a height field, with strips of its side texture along the {@code open} edges
	 * (see WaveSheet for their order). {@code x, y, z} is the camera-relative corner of the top.
	 */
	public static void bentTop(net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos, float x, float y, float z,
							   boolean[] open, com.zerovisual.fx.WaveSheet.Heights heights) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		var level = mc.level;
		if (level == null) return;
		java.util.List<net.minecraft.client.renderer.block.model.BlockModelPart> parts = new java.util.ArrayList<>();
		mc.getBlockRenderer().getBlockModel(state).collectParts(net.minecraft.util.RandomSource.create(state.getSeed(pos)), parts);
		net.minecraft.core.BlockPos above = pos.above();
		int light = net.minecraft.client.renderer.LightTexture.pack(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, above), level.getBrightness(net.minecraft.world.level.LightLayer.SKY, above));
		int overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
		geometry(net.minecraft.client.renderer.Sheets.cutoutBlockSheet(), b -> {
			com.zerovisual.fx.WaveSheet.Sink sink = (vx, vy, vz, color, u, v, nx, ny, nz) -> b.addVertex(vx, vy, vz, color, u, v, overlay, light, nx, ny, nz);
			float wx = pos.getX(), wz = pos.getZ();
			for (var part : parts) {
				for (var quad : part.getQuads(net.minecraft.core.Direction.UP)) {
					var sprite = quad.sprite();
					com.zerovisual.fx.WaveSheet.top(sink, x, y, z, wx, wz, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), tint(mc, state, level, pos, quad), heights);
				}
				net.minecraft.core.Direction[] sides = {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST};
				for (int side = 0; side < 4; side++) {
					if (!open[side]) continue;
					for (var quad : part.getQuads(sides[side])) {
						var sprite = quad.sprite();
						com.zerovisual.fx.WaveSheet.side(sink, side, x, y, z, wx, wz, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), tint(mc, state, level, pos, quad), heights);
					}
				}
			}
		});
	}

	private static int tint(net.minecraft.client.Minecraft mc, net.minecraft.world.level.block.state.BlockState state, net.minecraft.client.multiplayer.ClientLevel level,
							net.minecraft.core.BlockPos pos, net.minecraft.client.renderer.block.model.BakedQuad quad) {
		return quad.isTinted() ? 0xFF000000 | mc.getBlockColors().getColor(state, level, pos, quad.tintIndex()) : 0xFFFFFFFF;
	}

	/** A line of the given width in the local XY plane of {@code m}, as a quad (for use inside {@link #geometry}). */
	public static void line(VertexConsumer buffer, Matrix4fc m, float x0, float y0, float x1, float y1, float width, int color0, int color1) {
		float dx = x1 - x0, dy = y1 - y0;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1e-6f) return;
		float nx = -dy / len * width / 2, ny = dx / len * width / 2;
		buffer.addVertex(m, x0 + nx, y0 + ny, 0).setColor(color0);
		buffer.addVertex(m, x0 - nx, y0 - ny, 0).setColor(color0);
		buffer.addVertex(m, x1 - nx, y1 - ny, 0).setColor(color1);
		buffer.addVertex(m, x1 + nx, y1 + ny, 0).setColor(color1);
	}

	/** Text facing whatever {@code m} faces, at full brightness. */
	public static void text(Matrix4fc m, Component text, float x, float y, int color, int background, boolean seeThrough) {
		text(m, text, x, y, color, background, seeThrough, false);
	}

	public static void text(Matrix4fc m, Component text, float x, float y, int color, int background, boolean seeThrough, boolean shadow) {
		if (collector == null) return;
		PoseStack stack = new PoseStack();
		stack.last().pose().set(m);
		// order 1: in order 0 the game draws text before custom geometry, a background would cover it
		collector.order(1).submitText(stack, x, y, text.getVisualOrderText(), shadow,
			seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, LightTexture.FULL_BRIGHT, color, background, 0);
	}

	public static Font font() {
		return Minecraft.getInstance().font;
	}
}
