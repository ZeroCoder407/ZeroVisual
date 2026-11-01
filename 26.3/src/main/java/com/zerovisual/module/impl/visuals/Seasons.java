package com.zerovisual.module.impl.visuals;

import com.zerovisual.fx.SeasonFx;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.WorldContext;
import com.zerovisual.render.WorldRender;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

import java.time.LocalDate;

/** Falling leaves, snow, petals or fluff around you. Auto follows the real season. */
public class Seasons extends Module {
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

	public void render(WorldContext context) {
		ClientLevel level = mc.level;
		if (mc.player == null || level == null || level.dimension() != Level.OVERWORLD) return;
		long now = System.nanoTime();
		float dt = lastFrame == 0 || mc.isPaused() ? 0 : Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;

		Vec3 cam = context.camera().position();
		Quaternionf rotation = context.camera().rotation();
		fx.update(kind(), count.getInt(), radius.getFloat(), wind.getFloat(), stay.getFloat(), dt, cam.x, cam.y, cam.z, (x, z) -> ground(level, x, z));

		float shade = 0.25f + 0.75f * level.getRawBrightness(BlockPos.containing(cam), level.getSkyDarken()) / 15f;
		float s = size.getFloat();
		WorldRender.quads(q -> fx.draw(q, cam.x, cam.y, cam.z, rotation, s, shade));
	}

	private SeasonFx.Kind kind() {
		String season = mode.get();
		if (season.equals("Auto")) {
			int month = LocalDate.now().getMonthValue();
			season = month == 12 || month <= 2 ? "Winter" : month <= 5 ? "Spring" : month <= 8 ? "Summer" : "Autumn";
		}
		return switch (season) {
			case "Winter" -> SeasonFx.Kind.WINTER;
			case "Spring" -> SeasonFx.Kind.SPRING;
			case "Summer" -> SeasonFx.Kind.SUMMER;
			default -> SeasonFx.Kind.AUTUMN;
		};
	}

	private static double ground(ClientLevel level, int x, int z) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		BlockPos below = new BlockPos(x, top - 1, z);
		VoxelShape shape = level.getBlockState(below).getCollisionShape(level, below);
		return shape.isEmpty() ? top : top - 1 + shape.max(Direction.Axis.Y);
	}
}
