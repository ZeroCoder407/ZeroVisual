package com.zerovisual.module.impl.visuals;

import com.zerovisual.fx.SeasonFx;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.WorldRender;
import com.zerovisual.render.WorldRenderContext;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.util.Vec3d;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import org.joml.Quaternionf;

import java.time.LocalDate;

/** Falling leaves, snow, petals or fluff around you. Auto follows the real season. */
public class Seasons extends Module implements com.zerovisual.ZeroVisualClient.WorldEffect {
	public final ModeSetting mode = add(new ModeSetting("Mode", "Auto", "Auto", "Autumn", "Winter", "Spring", "Summer"));
	public final NumberSetting count = add(new NumberSetting("Count", 350, 50, 800, 10));
	public final NumberSetting radius = add(new NumberSetting("Radius", 12, 6, 28, 1));
	public final NumberSetting size = add(new NumberSetting("Size", 0.11, 0.04, 0.2, 0.005));
	public final NumberSetting wind = add(new NumberSetting("Wind", 0.8, 0, 3, 0.1));
	public final NumberSetting stay = add(new NumberSetting("Stay on ground", 12, 0, 30, 1));

	private final SeasonFx fx = new SeasonFx();
	private long lastFrame;

	public Seasons() {
		super("Seasons", "Falling leaves, snow, petals or fluff that settle on the ground", Category.VISUALS);
	}

	@Override
	protected void onDisable() {
		fx.clear();
	}

	@Override
	public void render(WorldRenderContext context) {
		final World world = mc.theWorld;
		if (mc.thePlayer == null || world == null || world.provider.getDimensionId() != 0) return;
		long now = System.nanoTime();
		float dt = lastFrame == 0 || mc.isGamePaused() ? 0 : Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;

		final Vec3d cam = context.camera().getPos();
		final Quaternionf rotation = context.camera().getRotation();
		fx.update(kind(), count.getInt(), radius.getFloat(), wind.getFloat(), stay.getFloat(), dt, cam.x, cam.y, cam.z, (x, z) -> ground(world, x, z));

		float shade = 0.25f + 0.75f * world.getLightFromNeighbors(new BlockPos(cam.x, cam.y + 1, cam.z)) / 15f * skyFactor(world);
		final float s = size.getFloat();
		final float light = Math.max(0.25f, shade);
		WorldRender.begin();
		WorldRender.quads(q -> fx.draw(q, cam.x, cam.y, cam.z, rotation, s, light));
		WorldRender.end();
	}

	/** Day light falls at night: the sky darkening of 1.8.9 goes from 0 (day) to 11. */
	private static float skyFactor(World world) {
		return 1 - world.getSkylightSubtracted() / 15f;
	}

	private SeasonFx.Kind kind() {
		String season = mode.get();
		if (season.equals("Auto")) {
			int month = LocalDate.now().getMonthValue();
			season = month == 12 || month <= 2 ? "Winter" : month <= 5 ? "Spring" : month <= 8 ? "Summer" : "Autumn";
		}
		if (season.equals("Winter")) return SeasonFx.Kind.WINTER;
		if (season.equals("Spring")) return SeasonFx.Kind.SPRING;
		if (season.equals("Summer")) return SeasonFx.Kind.SUMMER;
		return SeasonFx.Kind.AUTUMN;
	}

	/** The top of the highest solid block of a column, its own shape counted (slabs, snow layers). */
	private static double ground(World world, int x, int z) {
		BlockPos top = world.getPrecipitationHeight(new BlockPos(x, 0, z));
		BlockPos below = top.down();
		IBlockState state = world.getBlockState(below);
		AxisAlignedBB box = state.getBlock().getCollisionBoundingBox(world, below, state);
		return box == null ? top.getY() : box.maxY;
	}
}
