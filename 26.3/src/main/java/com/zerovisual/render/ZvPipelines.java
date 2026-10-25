package com.zerovisual.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Render pipelines of ZeroVisual. The rounded shape shader takes its parameters from every vertex
 * (see shaders/core/shape.vsh), so any number of shapes batch into one draw.
 * Depth in 26.x is reversed: nearer is greater, hence GREATER_THAN_OR_EQUAL.
 */
public final class ZvPipelines {
	/** Position, color, local position (UV0), radius + thickness (UV3), half size (UV1), softness (LineWidth). */
	public static final VertexFormat SHAPE_FORMAT = VertexFormat.builder(0)
		.addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("Color", GpuFormat.RGBA8_UNORM)
		.addAttribute("UV0", GpuFormat.RG32_FLOAT)
		.addAttribute("UV3", GpuFormat.RG32_FLOAT)
		.addAttribute("UV1", GpuFormat.RG16_SINT)
		.addAttribute("LineWidth", GpuFormat.R32_FLOAT)
		.build();

	private static final RenderPipeline.Snippet SHAPE_SNIPPET = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withBindGroupLayout(BindGroupLayouts.PROJECTION)
		.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
		.withVertexShader(id("core/shape"))
		.withFragmentShader(id("core/shape"))
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withVertexBinding(0, SHAPE_FORMAT)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.withCull(false)
		.buildSnippet();

	private static final RenderPipeline.Snippet COLOR_SNIPPET = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withBindGroupLayout(BindGroupLayouts.PROJECTION)
		.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
		.withVertexShader("core/position_color")
		.withFragmentShader("core/position_color")
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.withCull(false)
		.buildSnippet();

	/** Rounded shapes in the HUD and screens. */
	public static final RenderPipeline GUI_SHAPE = RenderPipelines.register(
		RenderPipeline.builder(SHAPE_SNIPPET).withLocation(id("pipeline/gui_shape")).build());

	/** Rounded shapes in the world, hidden behind blocks (no depth writes: they are translucent). */
	public static final RenderPipeline WORLD_SHAPE = RenderPipelines.register(
		RenderPipeline.builder(SHAPE_SNIPPET).withLocation(id("pipeline/world_shape"))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)).build());

	/** Rounded shapes in the world, visible through blocks (waypoints). */
	public static final RenderPipeline WORLD_SHAPE_SEE_THROUGH = RenderPipelines.register(
		RenderPipeline.builder(SHAPE_SNIPPET).withLocation(id("pipeline/world_shape_see_through"))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false)).build());

	/** Flat colored geometry in the world (stars, wings, cones), as quads. */
	public static final RenderPipeline WORLD_COLOR = RenderPipelines.register(
		RenderPipeline.builder(COLOR_SNIPPET).withLocation(id("pipeline/world_color"))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)).build());

	/** Solid colored geometry in the world that hides what is behind it (the 3D pet). */
	public static final RenderPipeline WORLD_COLOR_SOLID = RenderPipelines.register(
		RenderPipeline.builder(COLOR_SNIPPET).withLocation(id("pipeline/world_color_solid"))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true)).build());

	/** Additive colored geometry (glows, accretion disk). */
	public static final RenderPipeline WORLD_COLOR_ADDITIVE = RenderPipelines.register(
		RenderPipeline.builder(COLOR_SNIPPET).withLocation(id("pipeline/world_color_additive"))
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)).build());

	// the sky pass draws before the world, without depth (CustomSky through SkyBatch)
	public static final RenderPipeline SKY_SHAPE = RenderPipelines.register(
		RenderPipeline.builder(SHAPE_SNIPPET).withLocation(id("pipeline/sky_shape")).build());
	public static final RenderPipeline SKY_COLOR = RenderPipelines.register(
		RenderPipeline.builder(COLOR_SNIPPET).withLocation(id("pipeline/sky_color")).build());
	public static final RenderPipeline SKY_COLOR_ADDITIVE = RenderPipelines.register(
		RenderPipeline.builder(COLOR_SNIPPET).withLocation(id("pipeline/sky_color_additive"))
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING)).build());
	public static final RenderPipeline SKY_TEXTURE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
			.withLocation(id("pipeline/sky_texture"))
			.withVertexShader("core/position_tex_color")
			.withFragmentShader("core/position_tex_color")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withCull(false)
			.build());

	/** Previous frames over the current one (MotionBlur), color only. */
	public static final RenderPipeline MOTION_BLUR = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
			.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
			.withLocation(id("pipeline/motion_blur"))
			.withVertexShader("core/screenquad")
			.withFragmentShader(id("core/motion_blur"))
			.withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA8_UNORM, 7))
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
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
