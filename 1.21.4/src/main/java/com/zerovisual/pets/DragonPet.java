package com.zerovisual.pets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EnderDragonEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * The mini dragon pet: the vanilla ender dragon model, scaled down and flown like a companion.
 * Wings, neck, jaw and tail animate from the dragon's own wing position; the body sway comes from
 * a frame tracker we feed by hand, so it floats along a soft S-curve, banks and bobs.
 */
public final class DragonPet {
	private EnderDragonEntity dummy;
	private EntityRenderer<EnderDragonEntity, EnderDragonEntityRenderState> renderer;
	private EnderDragonEntityRenderState state;
	private boolean ready, failed;
	private long lastSample;
	private static final float YAW_FIX = 180f;

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void ensure() {
		if (ready || failed) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) return;
		try {
			dummy = EntityType.ENDER_DRAGON.create(mc.world, SpawnReason.TRIGGERED);
			if (dummy == null) {
				failed = true;
				return;
			}
			renderer = (EntityRenderer) mc.getEntityRenderDispatcher().getRenderer(dummy);
			state = renderer.createRenderState();
			ready = true;
		} catch (Throwable t) {
			failed = true;
			com.zerovisual.ZeroVisualClient.LOGGER.warn("Mini dragon pet failed to set up", t);
		}
	}

	public void render(Vec3d p, Vec3d cam, float yawNow, float running, float phase, float size) {
		ensure();
		if (!ready || MinecraftClient.getInstance().world == null) return;
		long now = System.currentTimeMillis();
		float time = (now % 100_000L) / 1000f;

		if (now - lastSample > 50) {
			lastSample = now;
			float sway = MathHelper.sin(time * 0.62f) * 8f + MathHelper.sin(time * 0.31f + 1.7f) * 4f;
			float lift = MathHelper.sin(time * 0.8f) * 0.12f;
			state.frameTracker.tick(lift, sway);
		}

		float flapSpeed = 0.55f + running * 1.3f;
		state.wingPosition = (state.wingPosition + flapSpeed * 0.016f) % 1f;
		state.ticksSinceDeath = 0;
		state.hurt = false;
		state.crystalBeamPos = null;
		state.inLandingOrTakeoffPhase = false;
		state.sittingOrHovering = false;
		state.squaredDistanceFromOrigin = 1000;
		state.tickDelta = 0;
		state.displayName = null;
		state.onFire = false;
		state.invisible = false;

		float s = size * 0.085f;
		float bob = MathHelper.sin(time * 2.1f) * 0.05f;
		float hover = 0.7f + bob + running * 0.2f;

		shadow(p, cam, 0.32f * size * (1 - bob));

		MatrixStack matrices = new MatrixStack();
		matrices.translate(p.x - cam.x, p.y - cam.y + hover, p.z - cam.z);
		matrices.multiply(new Quaternionf().rotateY((float) Math.toRadians(-yawNow + YAW_FIX)));
		matrices.multiply(new Quaternionf().rotateZ((float) Math.toRadians(
			-MathHelper.clamp(MathHelper.wrapDegrees(state.getLerpedFrame(5).yRot() - state.getLerpedFrame(10).yRot()), -18, 18) * 0.3f)));
		matrices.scale(s, s, s);
		VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
		renderer.render(state, matrices, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		consumers.draw();
	}

	private void shadow(Vec3d p, Vec3d cam, float r) {
		org.joml.Matrix4f m = new org.joml.Matrix4f()
			.translate((float) (p.x - cam.x), (float) (p.y - cam.y) + 0.015f, (float) (p.z - cam.z))
			.rotateX((float) Math.toRadians(90));
		int c = 0x4A000000;
		com.zerovisual.render.Render2D.shape(m, -r, -r * 0.55f, r * 2, r * 1.1f, r, 0, r * 0.5f, c, c, c, c);
	}
}
