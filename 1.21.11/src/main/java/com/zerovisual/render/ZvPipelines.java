package com.zerovisual.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Render pipelines of ZeroVisual. The rounded shape shader takes its parameters from every vertex
 * (see shaders/core/shape.vsh), so any number of shapes batch into one draw.
 */
public final class ZvPipelines {
	/** Position, color, local position (UV0), half size (UV1), radius + thickness (UV2), softness (LineWidth). */
	public static final VertexFormat SHAPE_FORMAT = VertexFormat.builder()
		.add("Position", VertexFormatElement.POSITION)
		.add("Color", VertexFormatElement.COLOR)
		.add("UV0", VertexFormatElement.UV0)
		.add("UV1", VertexFormatElement.UV1)
		.add("UV2", VertexFormatElement.UV2)
		.add("LineWidth", VertexFormatElement.LINE_WIDTH)
		.build();

	private static final RenderPipeline.Snippet SHAPE_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
		.withVertexShader(id("core/shape"))
		.withFragmentShader(id("core/shape"))
		.withBlend(BlendFunction.TRANSLUCENT)
		.withVertexFormat(SHAPE_FORMAT, VertexFormat.Mode.QUADS)
		.withCull(false)
		.withDepthWrite(false)
		.buildSnippet();

	private static final RenderPipeline.Snippet COLOR_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
		.withVertexShader("core/position_color")
		.withFragmentShader("core/position_color")
		.withBlend(BlendFunction.TRANSLUCENT)
		.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
		.withCull(false)
		.withDepthWrite(false)
		.buildSnippet();

	/** Rounded shapes in the HUD and screens. */
	public static final RenderPipeline GUI_SHAPE = RenderPipelines.register(
		RenderPipeline.builder(SHAPE_SNIPPET).withLocation(id("pipeline/gui_shape")).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build());

	/** Rounded shapes in the world, hidden behind blocks (no depth writes: they are translucent). */
	public static final RenderPipeline WORLD_SHAPE = RenderPipelines.register(
		RenderPipeline.builder(SHAPE_SNIPPET).withLocation(id("pipeline/world_shape")).build());

	/** Rounded shapes in the world, visible through blocks (waypoints). */
	public static final RenderPipeline WORLD_SHAPE_SEE_THROUGH = RenderPipelines.register(
		RenderPipeline.builder(SHAPE_SNIPPET).withLocation(id("pipeline/world_shape_see_through")).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build());

	/** Flat colored geometry in the world (stars, wings, cones), as quads. */
	public static final RenderPipeline WORLD_COLOR = RenderPipelines.register(
		RenderPipeline.builder(COLOR_SNIPPET).withLocation(id("pipeline/world_color")).build());

	/** Solid colored geometry in the world that hides what is behind it (the 3D pet). */
	public static final RenderPipeline WORLD_COLOR_SOLID = RenderPipelines.register(
		RenderPipeline.builder(COLOR_SNIPPET).withLocation(id("pipeline/world_color_solid")).withDepthWrite(true).build());

	/** Additive colored geometry (glows, accretion disk). */
	public static final RenderPipeline WORLD_COLOR_ADDITIVE = RenderPipelines.register(
		RenderPipeline.builder(COLOR_SNIPPET).withLocation(id("pipeline/world_color_additive")).withBlend(BlendFunction.LIGHTNING).build());

	// the sky pass draws before the world, without depth (CustomSky through SkyBatch)
	public static final RenderPipeline SKY_SHAPE = RenderPipelines.register(
		RenderPipeline.builder(SHAPE_SNIPPET).withLocation(id("pipeline/sky_shape")).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build());
	public static final RenderPipeline SKY_COLOR = RenderPipelines.register(
		RenderPipeline.builder(COLOR_SNIPPET).withLocation(id("pipeline/sky_color")).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build());
	public static final RenderPipeline SKY_COLOR_ADDITIVE = RenderPipelines.register(
		RenderPipeline.builder(COLOR_SNIPPET).withLocation(id("pipeline/sky_color_additive")).withBlend(BlendFunction.LIGHTNING)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build());
	public static final RenderPipeline SKY_TEXTURE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withLocation(id("pipeline/sky_texture"))
			.withVertexShader("core/position_tex_color")
			.withFragmentShader("core/position_tex_color")
			.withSampler("Sampler0")
			.withBlend(BlendFunction.TRANSLUCENT)
			.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
			.withCull(false)
			.withDepthWrite(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.build());

	/** Previous frames over the current one (MotionBlur), color only. */
	public static final RenderPipeline MOTION_BLUR = RenderPipelines.register(
		RenderPipeline.builder()
			.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
			.withLocation(id("pipeline/motion_blur"))
			.withVertexShader("core/screenquad")
			.withFragmentShader(id("core/motion_blur"))
			.withSampler("InSampler")
			.withBlend(BlendFunction.TRANSLUCENT)
			.withDepthWrite(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withColorWrite(true, false)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.build());

	public static final RenderType WORLD_SHAPE_TYPE = RenderType.create("zerovisual_world_shape", RenderSetup.builder(WORLD_SHAPE).createRenderSetup());
	public static final RenderType WORLD_SHAPE_SEE_THROUGH_TYPE = RenderType.create("zerovisual_world_shape_see_through",
		RenderSetup.builder(WORLD_SHAPE_SEE_THROUGH).createRenderSetup());
	public static final RenderType WORLD_COLOR_TYPE = RenderType.create("zerovisual_world_color", RenderSetup.builder(WORLD_COLOR).createRenderSetup());
	public static final RenderType WORLD_COLOR_SOLID_TYPE = RenderType.create("zerovisual_world_color_solid",
		RenderSetup.builder(WORLD_COLOR_SOLID).createRenderSetup());
	public static final RenderType WORLD_COLOR_ADDITIVE_TYPE = RenderType.create("zerovisual_world_color_additive",
		RenderSetup.builder(WORLD_COLOR_ADDITIVE).createRenderSetup());

	private ZvPipelines() {
	}

	/** Forces class loading (and pipeline registration) during client init. */
	public static void init() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("zerovisual", path);
	}
}
