package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.render.Mesh;
import com.zerovisual.emote.EmoteManager;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.render.StarShape;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.render.WorldRenderContext;
import com.zerovisual.util.Vec3d;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
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
public class Pet extends Module implements com.zerovisual.ZeroVisualClient.WorldEffect {
	/** Ray angles, tip up: 0 top, 1 left arm, 2 left leg, 3 right leg, 4 right arm. */
	private static final float INNER = 0.47f;
	private static final float DEPTH = 0.42f;
	private static final Vector3f LIGHT = new Vector3f(0.35f, 0.9f, 0.45f).normalize();
	/** Starts running when the player is farther than this, stops this close to them, teleports beyond the last. */
	private static final double START = 4, STOP = 1, TELEPORT = 24;
	/** Mini me idle animations, in the order they play. */
	public static final int IDLE_NONE = -1, IDLE_ARMS = 0, IDLE_TAP = 1, IDLE_SIT = 2;
	private static final float IDLE_DELAY = 2, IDLE_ARMS_SECONDS = 6, IDLE_TAP_SECONDS = 5, IDLE_SIT_SECONDS = 9;

	/** The Mini me entity being drawn right now and its idle pose, read by ModelBipedMixin. */
	public static Entity posedState;
	/** Whether Mini me sits right now (ModelBipedMixin bends the legs like riding). */
	public static boolean posedSitting;
	public static int poseAnimation = IDLE_NONE;
	public static float poseBlend, poseTime;

	public final ModeSetting type = add(new ModeSetting("Type", "Star", "Star", "Mini me"));
	public final NumberSetting size = add(new NumberSetting("Size", 1, 0.5, 2, 0.1));
	/** Armor worn by Mini me (your own armor and items are not copied). */
	public final ModeSetting armor = add(new ModeSetting("Armor", "None", "None", "Iron", "Diamond", "Chain"));

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
	/** A copy of your player that only this client knows, drawn as Mini me. */
	private EntityOtherPlayerMP mini;
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
		if (mc.thePlayer == null || mc.theWorld == null) {
			pos = null;
			return;
		}
		Vec3d player = Vec3d.pos(mc.thePlayer);
		// horizontal distance only: flying high above it does not make it teleport
		if (pos == null || horizontalDistance(pos, player) > TELEPORT) {
			double yawRad = Math.toRadians(mc.thePlayer.renderYawOffset);
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
		yaw += MathHelper.wrapAngleTo180_float(wanted - yaw) * 0.35f;

		// Mini me idle animations start a moment after it stops; running interrupts them, and it copies the
		// emotes of the player, so they also wait until the emote is over
		if (chasing || step > 0.02 || EmoteManager.current(mc.thePlayer.getUniqueID()) != null) idleSince = -1;
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
		int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
		for (int y = MathHelper.floor_double(fromY) + 2, i = 0; i < 66 && y >= 0; i++, y--) {
			BlockPos at = new BlockPos(bx, y, bz);
			IBlockState state = mc.theWorld.getBlockState(at);
			AxisAlignedBB box = state.getBlock().getCollisionBoundingBox(mc.theWorld, at, state);
			if (box != null) return box.maxY;
		}
		return Double.NaN;
	}

	// ---- rendering ----

	@Override
	public void render(WorldRenderContext context) {
		if (pos == null || prevPos == null || mc.thePlayer == null) return;
		float delta = context.tickCounter().getTickDelta(false);
		Vec3d p = prevPos.lerp(pos, delta);
		float yawNow = prevYaw + MathHelper.wrapAngleTo180_float(yaw - prevYaw) * delta;
		float cycle = com.zerovisual.util.Mth.lerp(delta, prevPhase, phase);
		float running = com.zerovisual.util.Mth.lerp(delta, prevRun, run);
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

		GlStateManager.enableDepth();
		GlStateManager.depthMask(true);
		GlStateManager.disableCull();

		if (type.is("Mini me")) {
			drawShadow(p, cam, 0.25f * size.getFloat(), 0);
			GlStateManager.enableCull();
			renderMini(p, cam, yawNow, cycle, running, com.zerovisual.util.Mth.lerp(delta, prevSit, sit), time, delta);
			return;
		}

		drawShadow(p, cam, r, hop);

		Matrix4f model = new Matrix4f()
			.translate((float) (p.x - cam.x), (float) (p.y - cam.y) + centerY, (float) (p.z - cam.z))
			.rotateY((float) Math.toRadians(-yawNow))
			.rotateX((float) Math.toRadians(lean))
			.scale(1 + breathe, 1 - breathe, 1 + breathe);
		drawBody(model, r, legSwing, running, time);

		GlStateManager.enableCull();
	}

	/** Mini me has no name tag. */
	@SubscribeEvent
	public void onName(RenderLivingEvent.Specials.Pre<?> event) {
		if (mini != null && event.entity == mini) event.setCanceled(true);
	}

	/** A small copy of the player's own model, posed by the pet's movement. */
	private void renderMini(Vec3d p, Vec3d cam, float bodyYaw, float cycle, float running, float sitting, float time, float delta) {
		if (mini == null || mini.worldObj != mc.theWorld) mini = new EntityOtherPlayerMP(mc.theWorld, mc.thePlayer.getGameProfile());
		EntityOtherPlayerMP e = mini;
		float scale = 0.4f * size.getFloat();
		e.setPosition(p.x, p.y, p.z);
		e.prevPosX = e.lastTickPosX = p.x;
		e.prevPosY = e.lastTickPosY = p.y;
		e.prevPosZ = e.lastTickPosZ = p.z;
		e.ticksExisted = mc.thePlayer.ticksExisted;
		e.setSneaking(false);
		e.setInvisible(false);
		e.hurtTime = 0;
		e.deathTime = 0;
		e.swingProgress = e.prevSwingProgress = 0;
		e.isSwingInProgress = false;
		// empty hands and the armor chosen in the settings instead of yours
		ItemStack[] set = armorSet();
		e.setCurrentItemOrArmor(0, null);
		for (int slot = 0; slot < 4; slot++) e.setCurrentItemOrArmor(4 - slot, set[slot]);

		long now = System.currentTimeMillis();
		float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		// walk cycle from its own running; the legs slow down smoothly when it stops
		e.limbSwing = cycle * 1.6f;
		e.limbSwingAmount = e.prevLimbSwingAmount = running * 0.85f;
		e.renderYawOffset = e.prevRenderYawOffset = bodyYaw;
		e.rotationYaw = e.prevRotationYaw = bodyYaw;

		// the head follows you smoothly (at most 60 degrees from the body) and now and then glances aside
		Vec3d eyes = Vec3d.lerpedPos(mc.thePlayer, delta).add(0, mc.thePlayer.getEyeHeight(), 0);
		Vec3d head = p.add(0, 1.6 * scale, 0);
		double dx = eyes.x - head.x, dy = eyes.y - head.y, dz = eyes.z - head.z;
		float lookYaw = MathHelper.wrapAngleTo180_float((float) Math.toDegrees(Math.atan2(-dx, dz)) - bodyYaw);
		float lookPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		float glanceCycle = time % 8f;
		if (glanceCycle > 6f && glanceCycle < 7.4f && running < 0.1f) {
			lookYaw += ((int) (time / 8f) % 2 == 0 ? 40f : -40f);
			lookPitch *= 0.3f;
		}
		lookYaw = MathHelper.clamp_float(lookYaw, -60, 60) * (1 - running * 0.8f);
		lookPitch *= 1 - running * 0.7f;
		float follow = 1 - (float) Math.exp(-dt * 7);
		headYaw += (lookYaw - headYaw) * follow;
		headPitch += (lookPitch - headPitch) * follow;
		e.rotationYawHead = e.prevRotationYawHead = bodyYaw + headYaw;
		e.rotationPitch = e.prevRotationPitch = headPitch;

		// a happy hop when it arrives next to you
		float hopT = (now - arrivedAt) / 420f;
		float hop = hopT >= 0 && hopT < 1 ? MathHelper.sin(hopT * (float) Math.PI) * 0.3f : 0;
		// a bounce on every step while running
		float bounce = Math.abs(MathHelper.sin(e.limbSwing * 0.6662f)) * 0.08f * running;
		// breathing while standing, a gentle sway while sitting
		float breathe = (1 - running) * MathHelper.sin(time * 2.3f) * 0.015f;
		float lean = running * 9f;
		float sway = sitting * MathHelper.sin(time * 1.4f) * 3.5f;

		GlStateManager.pushMatrix();
		// sitting on the ground: the hips go down to the ground with the legs stretched forward
		GlStateManager.translate(p.x - cam.x, p.y - cam.y + (hop + bounce - sitting * 0.62f) * scale, p.z - cam.z);
		// lean forward while running and sway sideways while sitting, both around the feet
		double yawRad = Math.toRadians(bodyYaw);
		float fx = (float) -Math.sin(yawRad), fz = (float) Math.cos(yawRad);
		GlStateManager.rotate(lean, fz, 0, -fx);
		GlStateManager.rotate(sway, fx, 0, fz);
		GlStateManager.scale(scale, scale * (1 + breathe), scale);
		float[] idle = idleAnimation(now);
		posedState = e;
		posedSitting = sitting > 0.5f;
		poseAnimation = (int) idle[0];
		poseBlend = idle[1] * (1 - running);
		poseTime = idle[2];
		GlStateManager.enableTexture2D();
		GlStateManager.enableAlpha();
		GlStateManager.depthMask(true);
		RenderHelper.enableStandardItemLighting();
		try {
			mc.getRenderManager().renderEntityWithPosYaw(e, 0, 0, 0, bodyYaw, delta);
		} finally {
			posedState = null;
			RenderHelper.disableStandardItemLighting();
			GlStateManager.disableLighting();
			GlStateManager.popMatrix();
		}
	}

	private static final ItemStack[] NO_ARMOR = {null, null, null, null};
	/** 1.8.9 has no netherite: the darkest set there is, chain, stands in. */
	private static final ItemStack[] IRON = armor(Items.iron_helmet, Items.iron_chestplate, Items.iron_leggings, Items.iron_boots);
	private static final ItemStack[] DIAMOND = armor(Items.diamond_helmet, Items.diamond_chestplate, Items.diamond_leggings, Items.diamond_boots);
	private static final ItemStack[] CHAIN = armor(Items.chainmail_helmet, Items.chainmail_chestplate, Items.chainmail_leggings, Items.chainmail_boots);

	private static ItemStack[] armor(Item head, Item chest, Item legs, Item feet) {
		return new ItemStack[]{new ItemStack(head), new ItemStack(chest), new ItemStack(legs), new ItemStack(feet)};
	}

	/** Head, chest, legs, feet for the selected Armor setting. */
	private ItemStack[] armorSet() {
		String a = armor.get();
		return a.equals("Iron") ? IRON : a.equals("Diamond") ? DIAMOND : a.equals("Chain") ? CHAIN : NO_ARMOR;
	}

	private void drawShadow(Vec3d p, Vec3d cam, float r, float hop) {
		float s = r * (1.1f - hop * 0.8f);
		Matrix4f m = new Matrix4f()
			.translate((float) (p.x - cam.x), (float) (p.y - cam.y) + 0.015f, (float) (p.z - cam.z))
			.rotateX((float) Math.toRadians(90));
		int c = 0x55000000;
		GlStateManager.depthMask(false);
		Render2D.shape(m, -s, -s * 0.7f, s * 2, s * 1.4f, s, 0, s * 0.5f, c, c, c, c);
		GlStateManager.depthMask(true);
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

		GlStateManager.enableBlend();
		Mesh.defaultBlendFunc();
		Mesh b = Mesh.begin(Mesh.TRIANGLES);
		for (int i = 0; i < 10; i++) {
			Vector3f a = ring[i], c = ring[(i + 1) % 10];
			triangle(b, model, front, a, c, r);
			triangle(b, model, back, c, a, r);
		}
		b.draw();
		GlStateManager.disableBlend();
	}

	/** Flat-shaded triangle: the logo gradient by x, lit by a fixed light in world space. */
	private static void triangle(Mesh b, Matrix4f model, Vector3f p0, Vector3f p1, Vector3f p2, float r) {
		Vector3f normal = new Vector3f(p1).sub(p0).cross(new Vector3f(p2).sub(p0)).normalize();
		// rotate the normal with the model (no translation) to light it in world space
		model.transformDirection(normal).normalize();
		float light = 0.55f + 0.45f * Math.max(0, Math.abs(normal.dot(LIGHT)));
		vertex(b, model, p0, r, light);
		vertex(b, model, p1, r, light);
		vertex(b, model, p2, r, light);
	}

	private static void vertex(Mesh b, Matrix4f model, Vector3f p, float r, float light) {
		float t = Math.max(0f, Math.min(1f, (p.x + r) / (2 * r)));
		int c = ColorUtil.lerp(StarShape.LEFT, StarShape.RIGHT, t);
		int red = Math.round(((c >> 16) & 0xFF) * light), green = Math.round(((c >> 8) & 0xFF) * light), blue = Math.round((c & 0xFF) * light);
		b.vertex(model, p.x, p.y, p.z).color(0xFF000000 | red << 16 | green << 8 | blue);
	}
}
