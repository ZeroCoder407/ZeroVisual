package com.zerovisual.module.impl.cosmetics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.render.StarShape;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A little companion on the ground that only you see. It stays where it is until you are more than
 * 4 blocks away, then runs up to 1 block from you.
 * Star: a puffy 3D logo star whose two lower rays are its legs; it hops, leans and turns where it runs.
 * Mini me: a small copy of your skin in the armor chosen in the settings, with empty hands. It follows you
 * with its head, glances around, breathes, bounces while running and hops when it arrives. Standing still it
 * plays idle animations in turn: swinging both arms, tapping a foot and sitting on the floor.
 */
public class Pet extends Module {
	/** Ray angles, tip up: 0 top, 1 left arm, 2 left leg, 3 right leg, 4 right arm. */
	private static final float INNER = 0.47f;
	private static final float DEPTH = 0.42f;
	private static final Vector3f LIGHT = new Vector3f(0.35f, 0.9f, 0.45f).normalize();
	/** Starts running when the player is farther than this, stops this close to them, teleports beyond the last. */
	private static final double START = 4, STOP = 1, TELEPORT = 24;
	/** Mini me idle animations, in the order they play. */
	public static final int IDLE_NONE = -1, IDLE_ARMS = 0, IDLE_TAP = 1, IDLE_SIT = 2;
	private static final float IDLE_DELAY = 2, IDLE_ARMS_SECONDS = 6, IDLE_TAP_SECONDS = 5, IDLE_SIT_SECONDS = 9;

	/** The Mini me render state being drawn right now and its idle pose, read by BipedEntityModelMixin. */
	public static Object posedState;
	public static int poseAnimation = IDLE_NONE;
	public static float poseBlend, poseTime;

	public final ModeSetting type = add(new ModeSetting("Type", "Star", "Star", "Mini me"));
	public final NumberSetting size = add(new NumberSetting("Size", 1, 0.5, 2, 0.1));
	/** Armor worn by Mini me (your own armor and items are not copied). */
	public final ModeSetting armor = add(new ModeSetting("Armor", "None", "None", "Iron", "Diamond", "Netherite"));

	private Vec3d pos, prevPos;
	private double fallSpeed;
	private boolean chasing;
	private float yaw, prevYaw;
	/** Walk cycle, advanced by the distance run. */
	private float phase, prevPhase;
	/** 0 standing .. 1 running, smoothed. */
	private float run, prevRun;
	/** Since when Mini me stands still (-1 while running) and its 0..1 sitting progress. */
	private long idleSince = -1;
	private float sit, prevSit;
	private PlayerEntityRenderState miniState;
	/** Mini me: smoothed head direction, when it last arrived next to you (a happy hop) and frame timing. */
	private float headYaw, headPitch;
	private long arrivedAt, lastFrame;

	public Pet() {
		super("Pet", "A star or a mini you that runs up when you are more than 4 blocks away (only you see it)", Category.COSMETICS);
	}

	@Override
	protected void onDisable() {
		pos = null;
	}

	private float radius() {
		return 0.3f * size.getFloat();
	}

	// ---- movement (20 times per second) ----

	@Override
	public void onTick() {
		if (mc.player == null || mc.world == null) {
			pos = null;
			return;
		}
		Vec3d player = mc.player.getPos();
		// horizontal distance only: flying high above it does not make it teleport
		if (pos == null || horizontalDistance(pos, player) > TELEPORT) {
			double yawRad = Math.toRadians(mc.player.getBodyYaw());
			// (re)appear one block behind the player, on the ground below them
			double x = player.x + Math.sin(yawRad) * STOP, z = player.z - Math.cos(yawRad) * STOP;
			double ground = groundY(x, z, player.y);
			pos = new Vec3d(x, Double.isNaN(ground) ? player.y : ground, z);
			prevPos = pos;
			fallSpeed = 0;
			chasing = false;
		}
		prevPos = pos;
		prevYaw = yaw;
		prevPhase = phase;
		prevRun = run;

		// stays where it is until the player is more than 4 blocks away, then runs up to 1 block from them
		double dx = player.x - pos.x, dz = player.z - pos.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist > START) chasing = true;
		double left = dist - STOP;
		if (left <= 0.05 && chasing) {
			chasing = false;
			arrivedAt = System.currentTimeMillis();
		}
		double step = chasing ? Math.min(Math.max(left * 0.3, 0.12), Math.min(0.42, left)) : 0;
		double nx = pos.x + (step > 0 ? dx / dist * step : 0), nz = pos.z + (step > 0 ? dz / dist * step : 0);

		// the ground under the star itself (not the player): it keeps running on the ground while you fly
		double ground = groundY(nx, nz, pos.y);
		if (Double.isNaN(ground)) ground = pos.y;
		double ny;
		if (ground >= pos.y - 0.01) {
			// steps up instantly (the hop hides it)
			ny = Math.min(ground, pos.y + 1.1);
			fallSpeed = 0;
		} else {
			fallSpeed = Math.min(fallSpeed + 0.08, 1.5);
			ny = Math.max(ground, pos.y - fallSpeed);
		}
		pos = new Vec3d(nx, ny, nz);

		run += ((step > 0.02 ? 1f : 0f) - run) * 0.3f;
		phase += (float) step * 4.2f;
		// face where it runs, or the player when standing
		float wanted = step > 0.02
			? (float) Math.toDegrees(Math.atan2(-dx, dz))
			: (float) Math.toDegrees(Math.atan2(-(player.x - pos.x), player.z - pos.z));
		yaw += MathHelper.wrapDegrees(wanted - yaw) * 0.35f;

		// Mini me idle animations start a moment after it stops; running interrupts them
		if (chasing || step > 0.02) idleSince = -1;
		else if (idleSince < 0) idleSince = System.currentTimeMillis();
		prevSit = sit;
		sit += ((idleAnimation(System.currentTimeMillis())[0] == IDLE_SIT ? 1f : 0f) - sit) * 0.15f;
	}

	/** Dev screenshots: put the pet at a spot, standing (its idle animations start from now). */
	public void placeAt(Vec3d spot, float facingYaw) {
		pos = spot;
		prevPos = spot;
		yaw = prevYaw = facingYaw;
		chasing = false;
		idleSince = System.currentTimeMillis();
	}

	/**
	 * Current idle animation of Mini me and its 0..1 strength (eased in and out) and local time in seconds:
	 * after standing for a moment it cycles: swinging both arms, tapping a foot, sitting on the floor.
	 */
	private float[] idleAnimation(long now) {
		if (idleSince < 0) return new float[]{IDLE_NONE, 0, 0};
		float t = (now - idleSince) / 1000f - IDLE_DELAY;
		if (t < 0) return new float[]{IDLE_NONE, 0, 0};
		float cycle = IDLE_ARMS_SECONDS + IDLE_TAP_SECONDS + IDLE_SIT_SECONDS;
		t %= cycle;
		int animation;
		float local, length;
		if (t < IDLE_ARMS_SECONDS) {
			animation = IDLE_ARMS;
			local = t;
			length = IDLE_ARMS_SECONDS;
		} else if (t < IDLE_ARMS_SECONDS + IDLE_TAP_SECONDS) {
			animation = IDLE_TAP;
			local = t - IDLE_ARMS_SECONDS;
			length = IDLE_TAP_SECONDS;
		} else {
			animation = IDLE_SIT;
			local = t - IDLE_ARMS_SECONDS - IDLE_TAP_SECONDS;
			length = IDLE_SIT_SECONDS;
		}
		float blend = Math.min(1f, Math.min(local / 0.5f, (length - local) / 0.5f));
		return new float[]{animation, Math.max(0, blend), local};
	}

	private static double horizontalDistance(Vec3d a, Vec3d b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Top of the first block with collision from {@code fromY + 2} down (up to 64 blocks), or NaN. */
	private double groundY(double x, double z, double fromY) {
		BlockPos.Mutable p = new BlockPos.Mutable(MathHelper.floor(x), MathHelper.floor(fromY) + 2, MathHelper.floor(z));
		for (int i = 0; i < 66 && p.getY() >= mc.world.getBottomY(); i++, p.move(Direction.DOWN)) {
			BlockState state = mc.world.getBlockState(p);
			VoxelShape shape = state.getCollisionShape(mc.world, p);
			if (!shape.isEmpty()) return p.getY() + shape.getMax(Direction.Axis.Y);
		}
		return Double.NaN;
	}

	// ---- rendering ----

	public void render(WorldRenderContext context) {
		if (pos == null || prevPos == null || mc.player == null) return;
		float delta = context.tickCounter().getTickDelta(false);
		Vec3d p = prevPos.lerp(pos, delta);
		float yawNow = prevYaw + MathHelper.wrapDegrees(yaw - prevYaw) * delta;
		float cycle = MathHelper.lerp(delta, prevPhase, phase);
		float running = MathHelper.lerp(delta, prevRun, run);
		float time = (System.currentTimeMillis() % 100_000L) / 1000f;
		Vec3d cam = context.camera().getPos();
		float r = radius();

		// a hop on every step while running, slow breathing while standing
		float hop = Math.abs(MathHelper.sin(cycle)) * 0.16f * r * 3 * running;
		float breathe = (1 - running) * MathHelper.sin(time * 2.5f) * 0.03f;
		float lean = running * 14f;
		float legSwing = MathHelper.sin(cycle) * running;

		// legs reach the ground: the lowest outline point is sin(54 deg) * r below the center
		float centerY = r * 0.81f + hop;

		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.disableCull();

		if (type.is("Mini me")) {
			drawShadow(p, cam, 0.25f * size.getFloat(), 0);
			RenderSystem.enableCull();
			renderMini(p, cam, yawNow, cycle, running, MathHelper.lerp(delta, prevSit, sit), time, delta);
			return;
		}

		drawShadow(p, cam, r, hop);

		Matrix4f model = new Matrix4f()
			.translate((float) (p.x - cam.x), (float) (p.y - cam.y) + centerY, (float) (p.z - cam.z))
			.rotateY((float) Math.toRadians(-yawNow))
			.rotateX((float) Math.toRadians(lean))
			.scale(1 + breathe, 1 - breathe, 1 + breathe);
		drawBody(model, r, legSwing, running, time);

		RenderSystem.enableCull();
	}

	/** A small copy of the player's own model, posed by the pet's movement. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void renderMini(Vec3d p, Vec3d cam, float bodyYaw, float cycle, float running, float sitting, float time, float delta) {
		EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(mc.player);
		if (!(renderer instanceof PlayerEntityRenderer)) return;
		if (miniState == null) miniState = (PlayerEntityRenderState) renderer.createRenderState();
		PlayerEntityRenderState state = miniState;
		renderer.updateRenderState(mc.player, state, delta);
		float scale = 0.4f * size.getFloat();

		// no name tag, no fire, no sneaking or flying poses: just a little you
		state.displayName = null;
		state.playerName = null;
		state.onFire = false;
		state.invisible = false;
		state.isInSneakingPose = false;
		state.sneaking = false;
		state.isGliding = false;
		state.isSwimming = false;
		state.usingRiptide = false;
		state.leaningPitch = 0;
		state.hurt = false;
		state.deathTime = 0;
		state.pose = EntityPose.STANDING;
		state.isUsingItem = false;
		state.itemUseTime = 0;
		state.squaredDistanceToCamera = p.squaredDistanceTo(cam);

		// empty hands, no swings, and the armor chosen in the settings instead of yours
		state.rightHandItemState.clear();
		state.leftHandItemState.clear();
		state.headItemRenderState.clear();
		state.wearingSkullType = null;
		state.handSwingProgress = 0;
		state.rightArmPose = BipedEntityModel.ArmPose.EMPTY;
		state.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
		ItemStack[] set = armorSet();
		state.equippedHeadStack = set[0];
		state.equippedChestStack = set[1];
		state.equippedLegsStack = set[2];
		state.equippedFeetStack = set[3];

		long now = System.currentTimeMillis();
		float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;

		// walk cycle from its own running; the legs slow down smoothly when it stops
		state.limbFrequency = cycle * 1.6f;
		state.limbAmplitudeMultiplier = running * 0.85f;
		state.bodyYaw = bodyYaw;
		state.hasVehicle = sitting > 0.5f;

		// the head follows you smoothly (at most 60 degrees from the body) and now and then glances aside
		Vec3d eyes = mc.player.getLerpedPos(delta).add(0, mc.player.getStandingEyeHeight(), 0);
		Vec3d head = p.add(0, 1.6 * scale, 0);
		double dx = eyes.x - head.x, dy = eyes.y - head.y, dz = eyes.z - head.z;
		float lookYaw = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - bodyYaw);
		float lookPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		float glanceCycle = time % 8f;
		if (glanceCycle > 6f && glanceCycle < 7.4f && running < 0.1f) {
			lookYaw += ((int) (time / 8f) % 2 == 0 ? 40f : -40f);
			lookPitch *= 0.3f;
		}
		lookYaw = MathHelper.clamp(lookYaw, -60, 60) * (1 - running * 0.8f);
		lookPitch *= 1 - running * 0.7f;
		float follow = 1 - (float) Math.exp(-dt * 7);
		headYaw += (lookYaw - headYaw) * follow;
		headPitch += (lookPitch - headPitch) * follow;
		state.yawDegrees = headYaw;
		state.pitch = headPitch;

		// a happy hop when it arrives next to you
		float hopT = (now - arrivedAt) / 420f;
		float hop = hopT >= 0 && hopT < 1 ? MathHelper.sin(hopT * MathHelper.PI) * 0.3f : 0;
		// a bounce on every step while running
		float bounce = Math.abs(MathHelper.sin(state.limbFrequency * 0.6662f)) * 0.08f * running;
		// breathing while standing, a gentle sway while sitting
		float breathe = (1 - running) * MathHelper.sin(time * 2.3f) * 0.015f;
		float lean = running * 9f;
		float sway = sitting * MathHelper.sin(time * 1.4f) * 3.5f;

		MatrixStack matrices = new MatrixStack();
		// sitting on the ground: the hips go down to the ground with the legs stretched forward
		matrices.translate(p.x - cam.x, p.y - cam.y + (hop + bounce - sitting * 0.62f) * scale, p.z - cam.z);
		// lean forward while running and sway sideways while sitting, both around the feet
		double yawRad = Math.toRadians(bodyYaw);
		float fx = (float) -Math.sin(yawRad), fz = (float) Math.cos(yawRad);
		matrices.multiply(new Quaternionf().rotationAxis((float) Math.toRadians(lean), fz, 0, -fx));
		matrices.multiply(new Quaternionf().rotationAxis((float) Math.toRadians(sway), fx, 0, fz));
		matrices.scale(scale, scale * (1 + breathe), scale);
		VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
		int light = WorldRenderer.getLightmapCoordinates(mc.world, BlockPos.ofFloored(p.add(0, 0.3, 0)));
		float[] idle = idleAnimation(now);
		posedState = state;
		poseAnimation = (int) idle[0];
		poseBlend = idle[1] * (1 - running);
		poseTime = idle[2];
		try {
			renderer.render(state, matrices, consumers, light);
			consumers.draw();
		} finally {
			posedState = null;
		}
	}

	private static final ItemStack[] NO_ARMOR = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
	private static final ItemStack[] IRON = armor(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
	private static final ItemStack[] DIAMOND = armor(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
	private static final ItemStack[] NETHERITE = armor(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);

	private static ItemStack[] armor(Item head, Item chest, Item legs, Item feet) {
		return new ItemStack[]{new ItemStack(head), new ItemStack(chest), new ItemStack(legs), new ItemStack(feet)};
	}

	/** Head, chest, legs, feet for the selected Armor setting. */
	private ItemStack[] armorSet() {
		return switch (armor.get()) {
			case "Iron" -> IRON;
			case "Diamond" -> DIAMOND;
			case "Netherite" -> NETHERITE;
			default -> NO_ARMOR;
		};
	}

	private void drawShadow(Vec3d p, Vec3d cam, float r, float hop) {
		float s = r * (1.1f - hop * 0.8f);
		Matrix4f m = new Matrix4f()
			.translate((float) (p.x - cam.x), (float) (p.y - cam.y) + 0.015f, (float) (p.z - cam.z))
			.rotateX((float) Math.toRadians(90));
		int c = 0x55000000;
		RenderSystem.depthMask(false);
		Render2D.shape(m, -s, -s * 0.7f, s * 2, s * 1.4f, s, 0, s * 0.5f, c, c, c, c);
		RenderSystem.depthMask(true);
	}

	/** Outline point {@code i} (0..9, even = tips) of the star in the XY plane, with the legs swinging. */
	private static Vector3f outline(int i, float r, float legSwing, float running, float time) {
		double angle = Math.PI / 2 + i * Math.PI / 5;
		boolean tip = i % 2 == 0;
		float len = tip ? r : r * INNER;
		Vector3f v = new Vector3f((float) Math.cos(angle) * len, (float) Math.sin(angle) * len, 0);
		if (tip) {
			int ray = i / 2;
			if (ray == 2) v.z += legSwing * r * 0.55f;
			if (ray == 3) v.z -= legSwing * r * 0.55f;
			// arms swing opposite to the legs, and wave a little when standing
			float arm = -legSwing * r * 0.35f + (1 - running) * MathHelper.sin(time * 3) * r * 0.06f;
			if (ray == 1) v.z += arm;
			if (ray == 4) v.z -= arm;
			if (ray == 2 || ray == 3) v.y += Math.max(0, (ray == 2 ? legSwing : -legSwing)) * r * 0.2f;
		}
		return v;
	}

	private void drawBody(Matrix4f model, float r, float legSwing, float running, float time) {
		Vector3f[] ring = new Vector3f[10];
		for (int i = 0; i < 10; i++) ring[i] = outline(i, r, legSwing, running, time);
		Vector3f front = new Vector3f(0, 0, r * DEPTH), back = new Vector3f(0, 0, -r * DEPTH);

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
		for (int i = 0; i < 10; i++) {
			Vector3f a = ring[i], c = ring[(i + 1) % 10];
			triangle(b, model, front, a, c, r);
			triangle(b, model, back, c, a, r);
		}
		BufferRenderer.drawWithGlobalProgram(b.end());
		RenderSystem.disableBlend();
	}

	/** Flat-shaded triangle: the logo gradient by x, lit by a fixed light in world space. */
	private static void triangle(BufferBuilder b, Matrix4f model, Vector3f p0, Vector3f p1, Vector3f p2, float r) {
		Vector3f normal = new Vector3f(p1).sub(p0).cross(new Vector3f(p2).sub(p0)).normalize();
		// rotate the normal with the model (no translation) to light it in world space
		model.transformDirection(normal).normalize();
		float light = 0.55f + 0.45f * Math.max(0, Math.abs(normal.dot(LIGHT)));
		vertex(b, model, p0, r, light);
		vertex(b, model, p1, r, light);
		vertex(b, model, p2, r, light);
	}

	private static void vertex(BufferBuilder b, Matrix4f model, Vector3f p, float r, float light) {
		float t = Math.max(0f, Math.min(1f, (p.x + r) / (2 * r)));
		int c = ColorUtil.lerp(StarShape.LEFT, StarShape.RIGHT, t);
		int red = Math.round(((c >> 16) & 0xFF) * light), green = Math.round(((c >> 8) & 0xFF) * light), blue = Math.round((c & 0xFF) * light);
		b.vertex(model, p.x, p.y, p.z).color(0xFF000000 | red << 16 | green << 8 | blue);
	}
}
