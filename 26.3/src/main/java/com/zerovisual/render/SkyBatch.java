package com.zerovisual.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Immediate drawing inside the sky render pass (the submit collector draws after the world, the sky must come first).
 * Every call builds its quads, uploads them to a small vertex buffer and draws them right away.
 * The buffers of a frame are closed when the next sky frame starts.
 */
public final class SkyBatch {
	private static final ByteBufferBuilder BYTES = new ByteBufferBuilder(1 << 16);
	private static final List<GpuBuffer> USED = new ArrayList<>();

	private final RenderPass pass;
	private final Matrix4f modelView;

	public SkyBatch(RenderPass pass, Matrix4f modelView) {
		this.pass = pass;
		this.modelView = modelView;
		for (GpuBuffer buffer : USED) buffer.close();
		USED.clear();
	}

	/** Colored quads (position + color) with the given pipeline. */
	public void quads(RenderPipeline pipeline, Consumer<VertexConsumer> vertices) {
		draw(pipeline, null, null, vertices);
	}

	/** Textured quads (position + uv + color). */
	public void texturedQuads(GpuTextureView texture, GpuSampler sampler, Consumer<VertexConsumer> vertices) {
		draw(ZvPipelines.SKY_TEXTURE, texture, sampler, vertices);
	}

	/** Rounded shape, same parameters as {@link WorldRender#shape}. */
	public void shape(Matrix4fc m, float x, float y, float w, float h, float radius, float thickness, float softness,
					  int topLeft, int topRight, int bottomRight, int bottomLeft) {
		if (w <= 0 || h <= 0) return;
		draw(ZvPipelines.SKY_SHAPE, null, null, buffer -> Render2D.shapeVertices(buffer, null, m, x, y, w, h, radius, thickness, softness,
			Render2D.applyAlpha(topLeft), Render2D.applyAlpha(topRight), Render2D.applyAlpha(bottomRight), Render2D.applyAlpha(bottomLeft)));
	}

	private void draw(RenderPipeline pipeline, @Nullable GpuTextureView texture, @Nullable GpuSampler sampler, Consumer<VertexConsumer> vertices) {
		BufferBuilder builder = new BufferBuilder(BYTES, PrimitiveTopology.QUADS, java.util.Objects.requireNonNull(pipeline.getVertexFormatBinding(0)));
		vertices.accept(builder);
		try (MeshData mesh = builder.build()) {
			if (mesh == null) return;
			int indexCount = mesh.drawState().vertexCount() / 4 * 6;
			GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "ZeroVisual sky", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
			USED.add(vertexBuffer);
			RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
			GpuBuffer indexBuffer = indices.getBuffer(indexCount);
			pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView)));
			if (texture != null) pass.setUniform("Sampler0", texture, sampler);
			pass.setVertexBuffer(0, vertexBuffer.slice());
			pass.setIndexBuffer(indexBuffer, indices.type());
			pass.drawIndexed(indexCount, 1, 0, 0, 0);
		}
	}
}
