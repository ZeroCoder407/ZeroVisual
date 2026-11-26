package com.zerovisual.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Colored geometry with the calls of the newer versions' BufferBuilder: {@code vertex(m, x, y, z).color(c)}, then
 * {@link #draw()}. Positions go through the matrix on the CPU; the GL matrix applies on top.
 */
public final class Mesh {
	public static final int QUADS = GL11.GL_QUADS, TRIANGLES = GL11.GL_TRIANGLES, TRIANGLE_STRIP = GL11.GL_TRIANGLE_STRIP,
		TRIANGLE_FAN = GL11.GL_TRIANGLE_FAN, LINES = GL11.GL_LINES, LINE_STRIP = GL11.GL_LINE_STRIP, DEBUG_LINES = GL11.GL_LINES,
		DEBUG_LINE_STRIP = GL11.GL_LINE_STRIP;

	private final WorldRenderer buffer = Tessellator.getInstance().getWorldRenderer();
	private final Vector3f v = new Vector3f();
	private final boolean textured;
	private int count;

	private Mesh(int mode, boolean textured) {
		this.textured = textured;
		buffer.begin(mode, textured ? DefaultVertexFormats.POSITION_TEX_COLOR : DefaultVertexFormats.POSITION_COLOR);
	}

	public static Mesh begin(int mode) {
		return new Mesh(mode, false);
	}

	/** With texture coordinates, for the texture bound when {@link #draw()} runs. */
	public static Mesh beginTextured(int mode) {
		return new Mesh(mode, true);
	}

	public Mesh texture(float u, float v) {
		buffer.tex(u, v);
		return this;
	}

	public Mesh vertex(Matrix4f m, float x, float y, float z) {
		m.transformPosition(x, y, z, v);
		buffer.pos(v.x, v.y, v.z);
		return this;
	}

	public Mesh vertex(float x, float y, float z) {
		buffer.pos(x, y, z);
		return this;
	}

	/** Ends the vertex. */
	public Mesh color(int argb) {
		int c = Render2D.applyAlpha(argb);
		buffer.color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).endVertex();
		count++;
		return this;
	}

	public Mesh color(float r, float g, float b, float a) {
		return color((int) (a * 255) << 24 | (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255));
	}

	/** Draws the vertices (nothing when there are none). */
	public void draw() {
		if (textured) GlStateManager.enableTexture2D();
		else GlStateManager.disableTexture2D();
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		if (count > 0) Tessellator.getInstance().draw();
		else buffer.finishDrawing();
		GlStateManager.shadeModel(GL11.GL_FLAT);
		GlStateManager.enableTexture2D();
	}

	/** Drops the vertices without drawing. */
	public void discard() {
		buffer.finishDrawing();
	}

	/** Normal alpha blending. */
	public static void defaultBlendFunc() {
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}

	/** Blending that adds light. */
	public static void additiveBlendFunc() {
		GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
	}
}
