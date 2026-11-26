package com.zerovisual.pets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zerovisual.render.WorldRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.DragonFlightHistory;
import net.minecraft.world.phys.Vec3;

/**
 * The mini dragon pet: the vanilla ender dragon model, scaled down and flown like a companion.
 * Wings, neck, jaw and tail animate from the dragon's own flap time; the body sway comes from a
 * flight history we feed by hand, so it floats along a soft S-curve, banks and bobs. It hovers above
 * the ground, beats its wings faster while chasing you and hovers with slow lazy flaps when idle.
 */
public final class DragonPet {
	private EnderDragon dummy;
	private EntityRenderer<EnderDragon, EnderDragonRenderState> renderer;
	private EnderDragonRenderState state;
	private boolean ready, failed;
	private long lastSample;
	/** the model's own heading is the opposite of the pet's movement yaw */
	private static final float YAW_FIX = 180f;

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void ensure() {
		if (ready || failed) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		try {
			dummy = EntityType.ENDER_DRAGON.create(mc.level, EntitySpawnReason.TRIGGERED);
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

	public void render(Vec3 p, Vec3 cam, float yawNow, float running, float phase, float size, Vec3 playerPos) {
		ensure();
		if (!ready || mc() == null) return;
		long now = System.currentTimeMillis();
		float time = (now % 100_000L) / 1000f;

		// feed the flight history ~20 times a second: a slow S-curve sway and a gentle bob
		if (now - lastSample > 50) {
			lastSample = now;
			float sway = Mth.sin(time * 0.62f) * 8f + Mth.sin(time * 0.31f + 1.7f) * 4f;
			float lift = Mth.sin(time * 0.8f) * 0.12f;
			// the history sway is local: world orientation is done once, outside
			state.flightHistory.record(lift, sway);
		}

		float flapSpeed = 0.55f + running * 1.3f;
		state.flapTime = (state.flapTime + flapSpeed * 0.016f) % 1f;
		state.deathTime = 0;
		state.hasRedOverlay = false;
		state.beamOffset = null;
		state.isLandingOrTakingOff = false;
		state.isSitting = false;
		state.distanceToEgg = 1000;
		state.partialTicks = 0;
		state.nameTag = null;
		state.displayFireAnimation = false;
		state.isInvisible = false;
		state.outlineColor = 0;
		state.lightCoords = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;

		float s = size * 0.085f;
		float bob = Mth.sin(time * 2.1f) * 0.05f;
		float hover = 0.7f + bob + running * 0.2f;

		shadow(p, cam, 0.32f * size * (1 - bob));

		PoseStack matrices = new PoseStack();
		matrices.translate(p.x - cam.x, p.y - cam.y + hover, p.z - cam.z);
		matrices.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(-yawNow + YAW_FIX)));
		// bank into turns a little
		matrices.mulPose(new org.joml.Quaternionf().rotateZ((float) Math.toRadians(-Mth.clamp(Mth.wrapDegrees(state.getHistoricalPos(5).yRot() - state.getHistoricalPos(10).yRot()), -18, 18) * 0.3f)));
		matrices.scale(s, s, s);
		SubmitNodeCollector collector = WorldRender.collector();
		CameraRenderState cameraState = WorldRender.cameraState();
		if (collector == null || cameraState == null) return;
		renderer.submit(state, matrices, collector, cameraState);
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	private void shadow(Vec3 p, Vec3 cam, float r) {
		org.joml.Matrix4f m = new org.joml.Matrix4f()
			.translate((float) (p.x - cam.x), (float) (p.y - cam.y) + 0.015f, (float) (p.z - cam.z))
			.rotateX((float) Math.toRadians(90));
		int c = 0x4A000000;
		WorldRender.shape(m, -r, -r * 0.55f, r * 2, r * 1.1f, r, 0, r * 0.5f, c, c, c, c);
	}
}
