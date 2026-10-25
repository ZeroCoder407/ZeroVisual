package com.zerovisual.render;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.utilities.MainMenu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Dark menu background with logo stars falling like snow. Replaces the vanilla panorama. */
public final class MenuBackground {
	private static final Identifier STAR = Identifier.of(ZeroVisualClient.MOD_ID, "textures/gui/star.png");
	private static final int TEXTURE_SIZE = 64;

	private static final List<Flake> FLAKES = new ArrayList<>();
	private static final Random RANDOM = new Random();
	private static long lastFrame;

	private MenuBackground() {
	}

	public static boolean isEnabled() {
		ZeroVisualClient client = ZeroVisualClient.get();
		return client != null && client.modules().get(MainMenu.class).background.get();
	}

	public static void render(DrawContext ctx, int width, int height, float alpha) {
		MainMenu settings = ZeroVisualClient.get().modules().get(MainMenu.class);

		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;
		float time = (now / 1_000_000L) / 1000f;

		int a = Math.round(255 * alpha);
		ctx.fillGradient(0, 0, width, height, (a << 24) | 0x07070C, (a << 24) | 0x0E0A1A);

		// Two slowly drifting glows, blue on the left and purple on the right
		float prev = Render2D.getAlpha();
		Render2D.setAlpha(alpha);
		float drift = (float) Math.sin(time * 0.25) * width * 0.04f;
		Render2D.shadow(ctx, width * 0.12f + drift, height * 0.55f, width * 0.22f, height * 0.25f, 60, 90, 0x2E3B82F6);
		Render2D.shadow(ctx, width * 0.66f - drift, height * 0.25f, width * 0.22f, height * 0.25f, 60, 90, 0x2EA855F7);
		Render2D.setAlpha(prev);

		updateFlakes(settings, width, height, dt, time);
		for (Flake f : FLAKES) {
			int color = (Math.round(255 * f.alpha * alpha) << 24) | 0xFFFFFF;
			ctx.getMatrices().push();
			ctx.getMatrices().translate(f.x, f.y, 0);
			ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f.rotation));
			float scale = f.size / TEXTURE_SIZE;
			ctx.getMatrices().scale(scale, scale, 1);
			ctx.drawTexture(RenderLayer::getGuiTextured, STAR, -TEXTURE_SIZE / 2, -TEXTURE_SIZE / 2, 0, 0,
				TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, color);
			ctx.getMatrices().pop();
		}
	}

	private static void updateFlakes(MainMenu settings, int width, int height, float dt, float time) {
		int target = settings.stars.getInt();
		while (FLAKES.size() < target) FLAKES.add(new Flake(width, height, true));
		while (FLAKES.size() > target) FLAKES.removeLast();

		float speed = settings.speed.getFloat();
		for (Flake f : FLAKES) {
			f.y += f.fallSpeed * speed * dt;
			f.x += (float) Math.sin(time * f.swayFrequency + f.phase) * f.sway * dt;
			f.rotation += f.spin * speed * dt;
			if (f.y - f.size > height || f.x < -f.size * 2 || f.x > width + f.size * 2) f.respawn(width, height, false);
		}
	}

	private static class Flake {
		float x, y, size, fallSpeed, sway, swayFrequency, phase, rotation, spin, alpha;

		Flake(int width, int height, boolean anywhere) {
			respawn(width, height, anywhere);
		}

		void respawn(int width, int height, boolean anywhere) {
			// depth 0 = far (small, slow, dim), 1 = near
			float depth = RANDOM.nextFloat();
			size = 4 + depth * 11;
			fallSpeed = 10 + depth * 38;
			alpha = 0.18f + depth * 0.62f;
			sway = 6 + RANDOM.nextFloat() * 14;
			swayFrequency = 0.4f + RANDOM.nextFloat() * 0.8f;
			phase = RANDOM.nextFloat() * 6.28f;
			rotation = RANDOM.nextFloat() * 360;
			spin = (RANDOM.nextFloat() - 0.5f) * 60;
			x = RANDOM.nextFloat() * width;
			y = anywhere ? RANDOM.nextFloat() * height : -size - RANDOM.nextFloat() * 40;
		}
	}
}
