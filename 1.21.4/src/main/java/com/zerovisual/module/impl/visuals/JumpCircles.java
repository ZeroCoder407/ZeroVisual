package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.fx.WaveSheet;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.WorldRender;
import com.zerovisual.render.Render2D;
import com.zerovisual.render.StarShape;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Effect on the ground where you jump:
 * Circle - expanding ring; Star - the logo star grows and spins, slowing down, and fades with a small shockwave;
 * Shatter - the star appears, then breaks into its five rays which fly apart;
 * Glyphs - two rings of glowing hieroglyphs turning in opposite directions;
 * Block wave - a round ridge runs outwards across the tops of the blocks, like a wave through jelly.
 */
public class JumpCircles extends Module {
	private static final float APPEAR_SECONDS = 0.25f;
	private static final float SHATTER_SECONDS = 0.6f;
	/** Base opacity of the star, slightly see-through so it blends with the ground. */
	private static final float STAR_ALPHA = 0.9f;

	public final ModeSetting mode = add(new ModeSetting("Mode", "Circle", "Circle", "Star", "Shatter", "Glyphs", "Block wave"));
	public final NumberSetting radius = add(new NumberSetting("Radius", 1.2, 0.5, 3, 0.1));
	public final NumberSetting duration = add(new NumberSetting("Duration", 1.2, 0.4, 3, 0.1));
	/** Shatter: how long the star stays whole before it breaks. */
	public final NumberSetting breakAfter = add(new NumberSetting("Break after", 0.8, 0.2, 3, 0.1));

	private static final String GLYPHS = "\u9b54\u6cd5\u795e\u5149\u529b\u5929\u706b\u96f7\u98a8\u661f\u6708\u5251\u9f8d\u9b42\u7a7a\u6c17";
	private static final int WAVE_REACH = 7;
	/** Width of the ridge in blocks. */
	private static final float WAVE_WIDTH = 2.8f;

	private final List<Circle> circles = new ArrayList<>();
	private boolean wasOnGround = true;

	public JumpCircles() {
		super("JumpCircles", "Circles or the logo star on the ground when you jump", Category.COSMETICS);
	}

	@Override
	protected void onDisable() {
		circles.clear();
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		boolean onGround = mc.player.isOnGround();
		if (wasOnGround && !onGround && mc.player.getVelocity().y > 0.1) spawn(mc.player.getPos());
		wasOnGround = onGround;
	}

	public void spawn(Vec3d pos) {
		// a random start angle so stars of consecutive jumps do not look identical
		circles.add(new Circle(pos, System.currentTimeMillis(), (float) (Math.random() * 72), mode.is("Block wave") ? groundAround(pos) : Set.of()));
	}

	/** The top block of every column near the jump, so the wave only touches what can be seen. */
	private Set<BlockPos> groundAround(Vec3d pos) {
		Set<BlockPos> ground = new HashSet<>();
		BlockPos center = BlockPos.ofFloored(pos);
		for (int x = -WAVE_REACH; x <= WAVE_REACH; x++) {
			for (int z = -WAVE_REACH; z <= WAVE_REACH; z++) {
				if (x * x + z * z > WAVE_REACH * WAVE_REACH) continue;
				for (int y = 1; y >= -3; y--) {
					BlockPos at = center.add(x, y, z);
					if (mc.world.getBlockState(at).isOpaqueFullCube() && !mc.world.getBlockState(at.up()).isOpaqueFullCube()) {
						ground.add(at);
						break;
					}
				}
			}
		}
		return ground;
	}

	/** Total lifetime of one effect in milliseconds for the current mode. */
	private float lifetime() {
		return mode.is("Shatter") ? (APPEAR_SECONDS + breakAfter.getFloat() + SHATTER_SECONDS) * 1000f : duration.getFloat() * 1000f;
	}

	public void render(WorldRenderContext context) {
		if (circles.isEmpty()) return;
		Vec3d cam = context.camera().getPos();
		long now = System.currentTimeMillis();
		float life = lifetime();

		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();

		Iterator<Circle> it = circles.iterator();
		while (it.hasNext()) {
			Circle circle = it.next();
			float elapsed = now - circle.start;
			if (elapsed >= life) {
				it.remove();
				continue;
			}
			Matrix4f m = new Matrix4f()
				.translate((float) (circle.pos.x - cam.x), (float) (circle.pos.y - cam.y) + 0.02f, (float) (circle.pos.z - cam.z))
				.rotateX((float) Math.toRadians(90));
			switch (mode.get()) {
				case "Star" -> renderStar(m, circle, elapsed / life);
				case "Shatter" -> renderShatter(m, circle, elapsed / 1000f);
				case "Glyphs" -> renderGlyphs(m, circle, elapsed / life);
				case "Block wave" -> renderWave(circle, cam, elapsed / life);
				default -> renderCircle(m, elapsed / life);
			}
		}

		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
	}

	private void renderGlyphs(Matrix4f m, Circle circle, float t) {
		float grow = easeOut(Math.min(1f, t / 0.25f));
		float alpha = t < 0.72f ? 1f : 1f - (t - 0.72f) / 0.28f;
		float r = radius.getFloat() * 1.35f * grow;
		int accent = ColorUtil.withAlpha(Theme.accent(), alpha), light = ColorUtil.withAlpha(Theme.accentLight(), alpha);
		WorldRender.ring(m, r, 0.035f, accent, light, 0.16f);
		WorldRender.ring(m, r * 0.62f, 0.025f, light, accent, 0.1f);
		glyphRing(m, r * 0.81f, circle.angle + t * 200f, 12, 0, 0.022f * radius.getFloat(), light);
		glyphRing(m, r * 0.43f, circle.angle - t * 280f, 8, 7, 0.016f * radius.getFloat(), accent);
		WorldRender.flush();
	}

	private void glyphRing(Matrix4f m, float r, float degrees, int count, int first, float scale, int color) {
		for (int i = 0; i < count; i++) {
			String glyph = GLYPHS.substring((first + i) % GLYPHS.length(), (first + i) % GLYPHS.length() + 1);
			Matrix4f at = new Matrix4f(m).rotateZ((float) Math.toRadians(degrees + 360f * i / count))
				.translate(0, -r, 0).scale(scale);
			WorldRender.text(at, net.minecraft.text.Text.literal(glyph), -4, -4, color, 0, false, false);
		}
	}

	/** A round ridge runs out from the jump across the tops of the blocks, rising and falling softly as it passes. */
	private void renderWave(Circle circle, Vec3d cam, float t) {
		float front = t * (WAVE_REACH + WAVE_WIDTH);
		WaveSheet.Heights heights = (x, z) -> swell(circle, x, z, front);
		boolean[] open = new boolean[4];
		for (BlockPos at : circle.ground) {
			open[WaveSheet.NORTH] = !mc.world.getBlockState(at.north()).isOpaqueFullCube();
			open[WaveSheet.EAST] = !mc.world.getBlockState(at.east()).isOpaqueFullCube();
			open[WaveSheet.SOUTH] = !mc.world.getBlockState(at.south()).isOpaqueFullCube();
			open[WaveSheet.WEST] = !mc.world.getBlockState(at.west()).isOpaqueFullCube();
			WorldRender.bentTop(mc.world.getBlockState(at), at, (float) (at.getX() - cam.x), (float) (at.getY() + 1 - cam.y) + 0.002f, (float) (at.getZ() - cam.z), open, heights);
		}
		// the sheet needs the depth buffer that the flat effects of this module leave off
		RenderSystem.depthMask(true);
		WorldRender.flush();
		RenderSystem.depthMask(false);
	}

	/** Height of the ridge at a point: a smooth hump across the ring, weaker the further it has travelled. */
	private float swell(Circle circle, float x, float z, float front) {
		float dx = x - (float) circle.pos.x, dz = z - (float) circle.pos.z;
		float distance = (float) Math.sqrt(dx * dx + dz * dz);
		float across = (front - distance) / WAVE_WIDTH;
		if (across <= 0 || across >= 1) return 0;
		float hump = (float) Math.sin(across * Math.PI);
		float fade = Math.max(0, 1f - distance / (WAVE_REACH - 0.8f));
		return hump * hump * fade * 0.85f * radius.getFloat();
	}

	private void renderCircle(Matrix4f m, float t) {
		int accent = Theme.accent();
		int light = Theme.accentLight();
		float r = 0.15f + radius.getFloat() * easeOut(t);
		float fade = 1f - t;
		// glow + ring with a gradient across it
		Render2D.shape(m, -r, -r, r * 2, r * 2, r, 0, r * 0.25f,
			ColorUtil.withAlpha(accent, 0.25f * fade), ColorUtil.withAlpha(light, 0.25f * fade),
			ColorUtil.withAlpha(light, 0.25f * fade), ColorUtil.withAlpha(accent, 0.25f * fade));
		Render2D.shape(m, -r, -r, r * 2, r * 2, r, 0.06f, 0,
			ColorUtil.withAlpha(accent, fade), ColorUtil.withAlpha(light, fade),
			ColorUtil.withAlpha(light, fade), ColorUtil.withAlpha(accent, fade));
	}

	/** Grows from nothing while spinning fast, the spin slows down, then it fades out. */
	private void renderStar(Matrix4f m, Circle circle, float t) {
		float size = radius.getFloat() * easeOut(Math.min(1f, t / 0.45f));
		// two and a half turns, most of them at the start
		float angle = circle.angle + 900f * easeOut(t);
		float alpha = t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f;
		Matrix4f star = new Matrix4f(m).rotateZ((float) Math.toRadians(angle));
		drawStar(star, size, alpha);
		// a small shockwave while the star fades out
		if (t > 0.68f) {
			float w = (t - 0.68f) / 0.32f;
			float r = radius.getFloat() * (0.35f + 0.95f * easeOut(w));
			float fade = (1f - w) * 0.85f;
			int a = ColorUtil.withAlpha(StarShape.LEFT, fade), b = ColorUtil.withAlpha(StarShape.RIGHT, fade);
			Render2D.shape(m, -r, -r, r * 2, r * 2, r, 0.05f * (1f - w) + 0.015f, r * 0.12f, a, b, b, a);
		}
	}

	/** Appears, stays whole for "Break after" seconds, then its five rays fly apart and fade. */
	private void renderShatter(Matrix4f m, Circle circle, float seconds) {
		float r = radius.getFloat();
		float appear = Math.min(1f, seconds / APPEAR_SECONDS);
		float breakAt = APPEAR_SECONDS + breakAfter.getFloat();
		// a short spin while it appears, then it stands still until it breaks
		Matrix4f star = new Matrix4f(m).rotateZ((float) Math.toRadians(circle.angle + 180f * easeOut(appear)));
		if (seconds < breakAt) {
			drawStar(star, r * easeOut(appear), 1f);
			return;
		}
		float p = Math.min(1f, (seconds - breakAt) / SHATTER_SECONDS);
		float distance = r * 0.9f * easeOut(p);
		float alpha = 1f - p * p;
		for (int i = 0; i < 5; i++) {
			double dir = StarShape.rayAngle(i);
			Matrix4f piece = new Matrix4f(star)
				.translate((float) Math.cos(dir) * distance, (float) Math.sin(dir) * distance, 0)
				// each ray tumbles a little, alternating direction
				.rotateZ((float) Math.toRadians((i % 2 == 0 ? 1 : -1) * 120f * p))
				.scale(1f - 0.35f * p);
			StarShape.drawRay(piece, r, i, alpha * STAR_ALPHA);
		}
	}

	private static void drawStar(Matrix4f m, float r, float alpha) {
		StarShape.draw(m, r, alpha * STAR_ALPHA);
	}

	private static float easeOut(float t) {
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	private record Circle(Vec3d pos, long start, float angle, Set<BlockPos> ground) {
	}
}
