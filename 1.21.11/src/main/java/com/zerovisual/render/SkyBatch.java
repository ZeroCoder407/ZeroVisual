package com.zerovisual.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Immediate drawing of the sky (the submit collector draws after the world, the sky must come first).
 * Every call builds its quads, uploads them to a small vertex buffer and draws them in a short render pass of its own:
 * nothing may be uploaded while a pass is open.
 * The buffers of a frame are closed when the next sky frame starts.
 */
public final class SkyBatch {
	private static final ByteBufferBuilder BYTES = new ByteBufferBuilder(1 << 16);
	private static final List<GpuBuffer> USED = new ArrayList<>();

	private final RenderTarget target;
	private final Matrix4f modelView;

	public SkyBatch(RenderTarget target, Matrix4f modelView) {
		this.target = target;
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
		BufferBuilder builder = new BufferBuilder(BYTES, VertexFormat.Mode.QUADS, pipeline.getVertexFormat());
		vertices.accept(builder);
		try (MeshData mesh = builder.build()) {
			if (mesh == null) return;
			int indexCount = mesh.drawState().vertexCount() / 4 * 6;
			GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "ZeroVisual sky", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
			USED.add(vertexBuffer);
			RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
			GpuBuffer indexBuffer = indices.getBuffer(indexCount);
			var transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView), new org.joml.Vector4f(1, 1, 1, 1), new org.joml.Vector3f(), new Matrix4f());
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "ZeroVisual sky",
				target.getColorTextureView(), java.util.OptionalInt.empty(), target.getDepthTextureView(), java.util.OptionalDouble.empty())) {
				pass.setPipeline(pipeline);
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transform);
				if (texture != null) pass.bindTexture("Sampler0", texture, sampler);
				pass.setVertexBuffer(0, vertexBuffer);
				pass.setIndexBuffer(indexBuffer, indices.type());
				pass.drawIndexed(0, 0, indexCount, 1);
			}
		}
	}
}
