package com.zerovisual.render;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Freelook;
import com.zerovisual.util.Vec3d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * What the newer versions' Fabric WorldRenderContext gives the world effects, built from RenderWorldLastEvent.
 * The GL model-view matrix there holds the camera rotation with the origin at the render position of the
 * viewing entity, so effects draw relative to {@link Camera#getPos()}.
 */
public final class WorldRenderContext {
	private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

	private final Camera camera;
	private final TickCounter tickCounter;

	public WorldRenderContext(float partialTicks) {
		this.camera = new Camera(partialTicks);
		this.tickCounter = new TickCounter(partialTicks);
	}

	public Camera camera() {
		return camera;
	}

	public TickCounter tickCounter() {
		return tickCounter;
	}

	/** The current model-view matrix. */
	public Matrix4f positionMatrix() {
		MATRIX.clear();
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
		return new Matrix4f(MATRIX);
	}

	public static final class TickCounter {
		private final float partialTicks;

		TickCounter(float partialTicks) {
			this.partialTicks = partialTicks;
		}

		public float getTickDelta(boolean ignoreFreeze) {
			return partialTicks;
		}
	}

	public static final class Camera {
		private final Vec3d pos;
		private final Quaternionf rotation;
		private final boolean thirdPerson;
		private final float yaw, pitch;

		Camera(float partialTicks) {
			Minecraft mc = Minecraft.getMinecraft();
			Entity view = mc.getRenderViewEntity();
			pos = Vec3d.lerpedPos(view, partialTicks);
			float y = view.prevRotationYaw + (view.rotationYaw - view.prevRotationYaw) * partialTicks;
			float p = view.prevRotationPitch + (view.rotationPitch - view.prevRotationPitch) * partialTicks;
			ZeroVisualClient client = ZeroVisualClient.get();
			Freelook freelook = client == null ? null : client.modules().get(Freelook.class);
			if (freelook != null && freelook.isActive()) {
				y = freelook.getYaw();
				p = freelook.getPitch();
			}
			if (mc.gameSettings.thirdPersonView == 2) {
				y += 180;
				p = -p;
			}
			yaw = y;
			pitch = p;
			thirdPerson = mc.gameSettings.thirdPersonView != 0;
			// as the newer versions build Camera#rotation: local XY faces the viewer
			rotation = new Quaternionf().rotationYXZ((float) Math.PI - (float) Math.toRadians(y), (float) -Math.toRadians(p), 0);
		}

		/** The interpolated position of the viewing entity: the origin of the world drawing. */
		public Vec3d getPos() {
			return pos;
		}

		public Quaternionf getRotation() {
			return rotation;
		}

		public boolean isThirdPerson() {
			return thirdPerson;
		}

		public float getYaw() {
			return yaw;
		}

		public float getPitch() {
			return pitch;
		}
	}

	/** Puts the GL state the world effects expect (depth test on, no depth writes, no culling). */
	public static void begin() {
		GlStateManager.enableDepth();
		GlStateManager.depthMask(false);
		GlStateManager.disableCull();
		GlStateManager.disableLighting();
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager.disableAlpha();
		GlStateManager.color(1, 1, 1, 1);
	}

	/** Back to what the game has after drawing the world. */
	public static void end() {
		GlStateManager.enableAlpha();
		GlStateManager.enableTexture2D();
		GlStateManager.enableCull();
		GlStateManager.enableDepth();
		GlStateManager.depthMask(true);
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager.disableBlend();
		GlStateManager.shadeModel(GL11.GL_FLAT);
		GlStateManager.color(1, 1, 1, 1);
	}
}
