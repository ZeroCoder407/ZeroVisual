package com.zerovisual.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Helpers for effects drawn in the world from WorldRenderEvents.
 * Positions are camera-relative: the camera rotation is already on the model-view stack.
 */
public final class WorldRender {
	private WorldRender() {
	}

	/** Matrix at a world position, facing the camera. */
	public static Matrix4f billboard(Vec3d pos, Vec3d cam, Quaternionf cameraRotation) {
		return new Matrix4f()
			.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z))
			.rotate(cameraRotation);
	}

	/** Matrix lying flat (XZ plane) at a world position. */
	public static Matrix4f flat(Vec3d pos, Vec3d cam) {
		return new Matrix4f()
			.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z))
			.rotateX((float) Math.toRadians(90));
	}

	/** Soft glowing dot: a blurred halo plus a solid core. */
	public static void glowDot(Matrix4f m, float radius, int color) {
		int halo = ColorUtil.fade(color, 0.45f);
		Render2D.shape(m, -radius * 3, -radius * 3, radius * 6, radius * 6, radius * 3, 0, radius * 2.5f, halo, halo, halo, halo);
		Render2D.shape(m, -radius, -radius, radius * 2, radius * 2, radius, 0, 0, color, color, color, color);
	}

	/** Ring with a gradient and a soft glow. */
	public static void ring(Matrix4f m, float radius, float thickness, int colorA, int colorB, float glow) {
		if (glow > 0) {
			int ga = ColorUtil.fade(colorA, 0.35f), gb = ColorUtil.fade(colorB, 0.35f);
			Render2D.shape(m, -radius, -radius, radius * 2, radius * 2, radius, 0, glow, ga, gb, gb, ga);
		}
		Render2D.shape(m, -radius, -radius, radius * 2, radius * 2, radius, thickness, 0, colorA, colorB, colorB, colorA);
	}

	/** Colored quads with normal blending. */
	public static void quads(java.util.function.Consumer<com.zerovisual.fx.Quads> body) {
		com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
		draw(body);
	}

	/** Colored quads that add light. */
	public static void quadsAdditive(java.util.function.Consumer<com.zerovisual.fx.Quads> body) {
		com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
		draw(body);
		com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
	}

	private static void draw(java.util.function.Consumer<com.zerovisual.fx.Quads> body) {
		com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_COLOR);
		net.minecraft.client.render.BufferBuilder b = net.minecraft.client.render.Tessellator.getInstance()
			.begin(net.minecraft.client.render.VertexFormat.DrawMode.QUADS, net.minecraft.client.render.VertexFormats.POSITION_COLOR);
		body.accept((m, x, y, z, c) -> b.vertex(m, x, y, z).color(c));
		net.minecraft.client.render.BuiltBuffer built = b.endNullable();
		if (built != null) net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(built);
	}

	/**
	 * The top of a block bent by a height field, with strips of its side texture along the {@code open} edges
	 * (see WaveSheet for their order). {@code x, y, z} is the camera-relative corner of the top. Call {@link #flush} after the last one.
	 */
	public static void bentTop(net.minecraft.block.BlockState state, net.minecraft.util.math.BlockPos pos, float x, float y, float z,
							   boolean[] open, com.zerovisual.fx.WaveSheet.Heights heights) {
		net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
		int light = net.minecraft.client.render.WorldRenderer.getLightmapCoordinates(mc.world, pos.up());
		int overlay = net.minecraft.client.render.OverlayTexture.DEFAULT_UV;
		var buffer = mc.getBufferBuilders().getEntityVertexConsumers().getBuffer(net.minecraft.client.render.TexturedRenderLayers.getEntityCutout());
		com.zerovisual.fx.WaveSheet.Sink sink = (vx, vy, vz, color, u, v, nx, ny, nz) -> buffer.vertex(vx, vy, vz, color, u, v, overlay, light, nx, ny, nz);
		var model = mc.getBlockRenderManager().getModel(state);
		var random = net.minecraft.util.math.random.Random.create(state.getRenderingSeed(pos));
		float wx = pos.getX(), wz = pos.getZ();
		for (var quad : model.getQuads(state, net.minecraft.util.math.Direction.UP, random)) {
			var sprite = quad.getSprite();
			com.zerovisual.fx.WaveSheet.top(sink, x, y, z, wx, wz, sprite.getMinU(), sprite.getMinV(), sprite.getMaxU(), sprite.getMaxV(), tint(mc, state, pos, quad), heights);
		}
		net.minecraft.util.math.Direction[] sides = {net.minecraft.util.math.Direction.NORTH, net.minecraft.util.math.Direction.EAST, net.minecraft.util.math.Direction.SOUTH, net.minecraft.util.math.Direction.WEST};
		for (int side = 0; side < 4; side++) {
			if (!open[side]) continue;
			for (var quad : model.getQuads(state, sides[side], random)) {
				var sprite = quad.getSprite();
				com.zerovisual.fx.WaveSheet.side(sink, side, x, y, z, wx, wz, sprite.getMinU(), sprite.getMinV(), sprite.getMaxU(), sprite.getMaxV(), tint(mc, state, pos, quad), heights);
			}
		}
	}

	private static int tint(net.minecraft.client.MinecraftClient mc, net.minecraft.block.BlockState state, net.minecraft.util.math.BlockPos pos, net.minecraft.client.render.model.BakedQuad quad) {
		return quad.hasTint() ? 0xFF000000 | mc.getBlockColors().getColor(state, mc.world, pos, quad.getTintIndex()) : 0xFFFFFFFF;
	}

	/** Draws what {@link #bentTop} and {@link #text} queued. */
	public static void flush() {
		net.minecraft.client.MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().draw();
	}

	/** Text facing whatever {@code m} faces, at full brightness. Call {@link #flush} after the last one. */
	public static void text(Matrix4f m, net.minecraft.text.Text text, float x, float y, int color, int background, boolean seeThrough, boolean shadow) {
		net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
		mc.textRenderer.draw(text, x, y, color, shadow, m, mc.getBufferBuilders().getEntityVertexConsumers(),
			net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL, background, net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE);
	}

	public static void begin() {
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
	}

	public static void end() {
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
	}
}
