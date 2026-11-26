package com.zerovisual.render;

import com.zerovisual.fx.Quads;
import com.zerovisual.util.Vec3d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.function.Consumer;

/**
 * Helpers for effects drawn in the world from RenderWorldLastEvent, with the API of the newer versions.
 * Positions are camera-relative: the camera rotation is already on the model-view stack. Everything is drawn
 * immediately; {@link #begin} and {@link #end} wrap a group of effects.
 */
public final class WorldRender {
	private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

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

	/** Multiplies {@code m} onto the GL model-view matrix; pair with {@link GlStateManager#popMatrix()}. */
	public static void push(Matrix4f m) {
		GlStateManager.pushMatrix();
		MATRIX.clear();
		m.get(MATRIX);
		GL11.glMultMatrix(MATRIX);
	}

	/** Rounded shape (see Render2D) in the plane of {@code m}. */
	public static void shape(Matrix4f m, float x, float y, float w, float h, float radius, float thickness, float softness,
							 int topLeft, int topRight, int bottomRight, int bottomLeft) {
		push(m);
		int c0 = Render2D.applyAlpha(topLeft), c1 = Render2D.applyAlpha(topRight), c2 = Render2D.applyAlpha(bottomRight), c3 = Render2D.applyAlpha(bottomLeft);
		Render2D.shape(x, y, w, h, radius, thickness, softness, c0, c1, c2, c3);
		GlStateManager.popMatrix();
	}

	/** Soft glowing dot: a blurred halo plus a solid core. */
	public static void glowDot(Matrix4f m, float radius, int color) {
		int halo = ColorUtil.fade(color, 0.45f);
		shape(m, -radius * 3, -radius * 3, radius * 6, radius * 6, radius * 3, 0, radius * 2.5f, halo, halo, halo, halo);
		shape(m, -radius, -radius, radius * 2, radius * 2, radius, 0, 0, color, color, color, color);
	}

	/** Ring with a gradient and a soft glow. */
	public static void ring(Matrix4f m, float radius, float thickness, int colorA, int colorB, float glow) {
		if (glow > 0) {
			int ga = ColorUtil.fade(colorA, 0.35f), gb = ColorUtil.fade(colorB, 0.35f);
			shape(m, -radius, -radius, radius * 2, radius * 2, radius, 0, glow, ga, gb, gb, ga);
		}
		shape(m, -radius, -radius, radius * 2, radius * 2, radius, thickness, 0, colorA, colorB, colorB, colorA);
	}

	/** Colored quads with normal blending. */
	public static void quads(Consumer<Quads> body) {
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		draw(body);
	}

	/** Colored quads that add light. */
	public static void quadsAdditive(Consumer<Quads> body) {
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		draw(body);
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}

	private static void draw(Consumer<Quads> body) {
		GlStateManager.disableTexture2D();
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		final WorldRenderer b = Tessellator.getInstance().getWorldRenderer();
		b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		final Vector3f v = new Vector3f();
		body.accept(new Quads() {
			@Override
			public void vertex(Matrix4f m, float x, float y, float z, int c) {
				m.transformPosition(x, y, z, v);
				int a = Render2D.applyAlpha(c);
				b.pos(v.x, v.y, v.z).color(a >> 16 & 255, a >> 8 & 255, a & 255, a >>> 24).endVertex();
			}
		});
		Tessellator.getInstance().draw();
		GlStateManager.shadeModel(GL11.GL_FLAT);
		GlStateManager.enableTexture2D();
	}

	/** Text in the plane of {@code m} (Y down, as on screen), at full brightness, with an optional background. */
	public static void text(Matrix4f m, String text, float x, float y, int color, int background, boolean seeThrough, boolean shadow) {
		Minecraft mc = Minecraft.getMinecraft();
		push(m);
		if (seeThrough) GlStateManager.disableDepth();
		if ((background >>> 24) != 0) {
			GlStateManager.disableTexture2D();
			float w = mc.fontRendererObj.getStringWidth(text);
			WorldRenderer b = Tessellator.getInstance().getWorldRenderer();
			b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
			int r = background >> 16 & 255, g = background >> 8 & 255, bl = background & 255, a = background >>> 24;
			b.pos(x - 1, y - 1, 0.01).color(r, g, bl, a).endVertex();
			b.pos(x - 1, y + 8, 0.01).color(r, g, bl, a).endVertex();
			b.pos(x + w, y + 8, 0.01).color(r, g, bl, a).endVertex();
			b.pos(x + w, y - 1, 0.01).color(r, g, bl, a).endVertex();
			Tessellator.getInstance().draw();
		}
		GlStateManager.enableTexture2D();
		GlStateManager.enableAlpha();
		mc.fontRendererObj.drawString(text, x, y, color, shadow);
		GlStateManager.disableAlpha();
		if (seeThrough) GlStateManager.enableDepth();
		GlStateManager.popMatrix();
	}

	/**
	 * The top of a block bent by a height field, with strips of its side texture along the {@code open} edges
	 * (see WaveSheet for their order). {@code x, y, z} is the camera-relative corner of the top.
	 */
	public static void bentTop(net.minecraft.block.state.IBlockState state, net.minecraft.util.BlockPos pos, float x, float y, float z,
							   boolean[] open, com.zerovisual.fx.WaveSheet.Heights heights) {
		Minecraft mc = Minecraft.getMinecraft();
		net.minecraft.client.resources.model.IBakedModel model = mc.getBlockRendererDispatcher().getModelFromBlockState(state, mc.theWorld, pos);
		// no lightmap here: the light of the block above darkens the colors
		float light = mc.theWorld.getLightBrightness(pos.up());
		mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.locationBlocksTexture);
		GlStateManager.enableTexture2D();
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		final WorldRenderer b = Tessellator.getInstance().getWorldRenderer();
		b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
		com.zerovisual.fx.WaveSheet.Sink sink = (vx, vy, vz, color, u, v, nx, ny, nz) ->
			b.pos(vx, vy, vz).tex(u, v).color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24).endVertex();
		float wx = pos.getX(), wz = pos.getZ();
		for (net.minecraft.client.renderer.block.model.BakedQuad quad : model.getFaceQuads(net.minecraft.util.EnumFacing.UP)) {
			float[] uv = uvBounds(quad);
			com.zerovisual.fx.WaveSheet.top(sink, x, y, z, wx, wz, uv[0], uv[1], uv[2], uv[3], tint(mc, state, pos, quad, light), heights);
		}
		net.minecraft.util.EnumFacing[] sides = {net.minecraft.util.EnumFacing.NORTH, net.minecraft.util.EnumFacing.EAST,
			net.minecraft.util.EnumFacing.SOUTH, net.minecraft.util.EnumFacing.WEST};
		for (int side = 0; side < 4; side++) {
			if (!open[side]) continue;
			for (net.minecraft.client.renderer.block.model.BakedQuad quad : model.getFaceQuads(sides[side])) {
				float[] uv = uvBounds(quad);
				com.zerovisual.fx.WaveSheet.side(sink, side, x, y, z, wx, wz, uv[0], uv[1], uv[2], uv[3], tint(mc, state, pos, quad, light), heights);
			}
		}
		Tessellator.getInstance().draw();
		GlStateManager.shadeModel(GL11.GL_FLAT);
	}

	/** The corners of the texture of a quad in the block atlas; 1.8.9 keeps them only in the vertex data. */
	private static float[] uvBounds(net.minecraft.client.renderer.block.model.BakedQuad quad) {
		int[] data = quad.getVertexData();
		int stride = data.length / 4;
		float u0 = Float.MAX_VALUE, v0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
		for (int i = 0; i < 4; i++) {
			float u = Float.intBitsToFloat(data[i * stride + 4]), v = Float.intBitsToFloat(data[i * stride + 5]);
			u0 = Math.min(u0, u);
			v0 = Math.min(v0, v);
			u1 = Math.max(u1, u);
			v1 = Math.max(v1, v);
		}
		return new float[]{u0, v0, u1, v1};
	}

	private static int tint(Minecraft mc, net.minecraft.block.state.IBlockState state, net.minecraft.util.BlockPos pos,
							net.minecraft.client.renderer.block.model.BakedQuad quad, float light) {
		int rgb = quad.hasTintIndex() ? state.getBlock().colorMultiplier(mc.theWorld, pos, quad.getTintIndex()) : 0xFFFFFF;
		int r = (int) ((rgb >> 16 & 255) * light), g = (int) ((rgb >> 8 & 255) * light), bl = (int) ((rgb & 255) * light);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	/** Nothing is queued on 1.8.9. */
	public static void flush() {
	}

	public static void begin() {
		WorldRenderContext.begin();
	}

	public static void end() {
		WorldRenderContext.end();
	}
}
