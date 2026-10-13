package com.zerovisual.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.util.concurrent.CompletableFuture;

/**
 * Procedural space background as a cube map: nebula clouds and a milky way band from 3D noise sampled
 * on the view direction, so the six faces join without seams. Generated once in the background.
 */
public final class SpaceTextures {
	public static final int SIZE = 256;

	/** Cube faces: normal, u axis, v axis (direction = n + u * (2s - 1) + v * (2t - 1)). */
	public static final float[][][] FACES = {
		{{1, 0, 0}, {0, 0, -1}, {0, -1, 0}},
		{{-1, 0, 0}, {0, 0, 1}, {0, -1, 0}},
		{{0, 1, 0}, {1, 0, 0}, {0, 0, 1}},
		{{0, -1, 0}, {1, 0, 0}, {0, 0, -1}},
		{{0, 0, 1}, {1, 0, 0}, {0, -1, 0}},
		{{0, 0, -1}, {-1, 0, 0}, {0, -1, 0}},
	};

	/** Colors of one style: background, two nebula colors, milky way. */
	public record Palette(String name, int base, int nebulaA, int nebulaB, int band, float density) {
	}

	public static final Palette GALAXY = new Palette("galaxy", 0x04050C, 0x6D3BD8, 0x2F6BFF, 0xC9B8FF, 1f);
	public static final Palette DEEP = new Palette("deep", 0x020205, 0x7A3A1E, 0x23305E, 0xD9C2A0, 0.55f);

	private final Palette palette;
	private Identifier[] ids;
	private CompletableFuture<NativeImage[]> pending;

	public SpaceTextures(Palette palette) {
		this.palette = palette;
	}

	/** Texture ids once generated and uploaded, otherwise null (and generation is started). Render thread only. */
	public Identifier[] get() {
		if (ids != null) return ids;
		if (pending == null) pending = CompletableFuture.supplyAsync(this::generate);
		if (!pending.isDone()) return null;
		NativeImage[] images = pending.join();
		Identifier[] result = new Identifier[6];
		for (int f = 0; f < 6; f++) {
			NativeImageBackedTexture texture = new NativeImageBackedTexture(images[f]);
			texture.setFilter(true, false);
			result[f] = Identifier.of("zerovisual", "sky/" + palette.name + "_" + f);
			MinecraftClient.getInstance().getTextureManager().registerTexture(result[f], texture);
		}
		ids = result;
		return ids;
	}

	private NativeImage[] generate() {
		NativeImage[] images = new NativeImage[6];
		Vector3f bandNormal = new Vector3f(0.35f, 0.8f, 0.48f).normalize();
		Vector3f d = new Vector3f();
		for (int f = 0; f < 6; f++) {
			float[][] face = FACES[f];
			NativeImage image = new NativeImage(SIZE, SIZE, false);
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					float s = (x + 0.5f) / SIZE * 2 - 1, t = (y + 0.5f) / SIZE * 2 - 1;
					d.set(face[0][0] + face[1][0] * s + face[2][0] * t,
						face[0][1] + face[1][1] * s + face[2][1] * t,
						face[0][2] + face[1][2] * s + face[2][2] * t).normalize();
					image.setColorArgb(x, y, color(d, bandNormal));
				}
			}
			images[f] = image;
		}
		return images;
	}

	private int color(Vector3f d, Vector3f bandNormal) {
		float clouds = fbm(d.x * 2.2f, d.y * 2.2f, d.z * 2.2f, 5);
		float mix = fbm(d.x * 3.7f + 11, d.y * 3.7f + 5, d.z * 3.7f + 2, 4);
		float dust = fbm(d.x * 7f + 3, d.y * 7f + 9, d.z * 7f + 1, 3);
		float nebula = smoothstep(0.42f, 0.85f, clouds) * palette.density;

		float across = d.dot(bandNormal);
		float band = (float) Math.exp(-across * across / 0.035f) * (0.35f + 0.65f * fbm(d.x * 6, d.y * 6, d.z * 6, 4));
		// dark dust lanes cut through the band
		band *= 1f - 0.7f * smoothstep(0.5f, 0.75f, dust);

		float r = channel(palette.base, 16), g = channel(palette.base, 8), b = channel(palette.base, 0);
		float na = nebula * (1 - mix), nb = nebula * mix;
		r += channel(palette.nebulaA, 16) * na * 0.55f + channel(palette.nebulaB, 16) * nb * 0.55f + channel(palette.band, 16) * band * 0.45f;
		g += channel(palette.nebulaA, 8) * na * 0.55f + channel(palette.nebulaB, 8) * nb * 0.55f + channel(palette.band, 8) * band * 0.45f;
		b += channel(palette.nebulaA, 0) * na * 0.55f + channel(palette.nebulaB, 0) * nb * 0.55f + channel(palette.band, 0) * band * 0.45f;
		return 0xFF000000 | to255(r) << 16 | to255(g) << 8 | to255(b);
	}

	private static float channel(int rgb, int shift) {
		return ((rgb >> shift) & 0xFF) / 255f;
	}

	private static int to255(float v) {
		return Math.max(0, Math.min(255, Math.round(v * 255)));
	}

	private static float smoothstep(float a, float b, float x) {
		float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
		return t * t * (3 - 2 * t);
	}

	// ---- value noise ----

	private static float fbm(float x, float y, float z, int octaves) {
		float sum = 0, amp = 0.5f, norm = 0;
		for (int i = 0; i < octaves; i++) {
			sum += noise(x, y, z) * amp;
			norm += amp;
			x *= 2.03f;
			y *= 2.03f;
			z *= 2.03f;
			amp *= 0.5f;
		}
		return sum / norm;
	}

	private static float noise(float x, float y, float z) {
		int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
		float fx = x - ix, fy = y - iy, fz = z - iz;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		fz = fz * fz * (3 - 2 * fz);
		float x00 = lerp(hash(ix, iy, iz), hash(ix + 1, iy, iz), fx);
		float x10 = lerp(hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz), fx);
		float x01 = lerp(hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1), fx);
		float x11 = lerp(hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1), fx);
		return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
	}

	private static float hash(int x, int y, int z) {
		int h = x * 374761393 + y * 668265263 + z * 1274126177;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (float) 0xFFFFFF;
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
