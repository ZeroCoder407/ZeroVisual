package com.zerovisual.emote;

import java.util.List;

/** The emotes of the wheel, in clockwise order from the top. Ids go over the network: never rename them. */
public final class Emotes {
	private static final float PI = (float) Math.PI;

	public static final List<Emote> ALL = List.of(
		new Emote("wave", "Wave", 2.4f, false, Emotes::wave),
		new Emote("clap", "Clap", 0.6f, true, Emotes::clap),
		new Emote("sit", "Sit", 4f, true, Emotes::sit),
		new Emote("lie", "Lie down", 4f, true, Emotes::lie),
		new Emote("rat", "Rat dance", DanceTracks.RAT.duration(), true, DanceTracks.RAT),
		new Emote("rampage", "Rampage", DanceTracks.RAMPAGE.duration(), true, DanceTracks.RAMPAGE),
		new Emote("funk", "Funked up", DanceTracks.FUNK.duration(), true, DanceTracks.FUNK),
		new Emote("squat", "Prisyadka", 4.8f, true, squatDance()),
		new Emote("flex", "Flex", 3f, false, Emotes::flex),
		new Emote("moonwalk", "Moonwalk", 4f, false, Emotes::moonwalk)
	);

	private Emotes() {
	}

	public static Emote byId(String id) {
		for (Emote e : ALL) if (e.id().equals(id)) return e;
		return null;
	}

	private static float sin(float x) {
		return (float) Math.sin(x);
	}

	private static float cos(float x) {
		return (float) Math.cos(x);
	}

	/** 0 before a, 1 after b, smooth in between. */
	private static float ramp(float t, float a, float b) {
		float k = Math.max(0, Math.min(1, (t - a) / (b - a)));
		return k * k * (3 - 2 * k);
	}

	private static void wave(float t, EmotePose p) {
		float up = ramp(t, 0, 0.3f) * (1 - ramp(t, 2.05f, 2.35f));
		float swing = sin(t * 10f);
		p.rArmRz = (2.55f + swing * 0.28f) * up;
		p.rArmRy = -0.2f * up;
		p.rElbow = (-0.55f + swing * 0.3f) * up;
		p.lArmRz = -0.08f;
		p.waistRz = -0.06f * up;
		p.headRz = 0.1f * up;
		p.headRy = -0.2f * up;
	}

	private static void clap(float t, EmotePose p) {
		float k = t / 0.6f * 2 * PI;
		float open = Math.max(0, sin(k)) * 0.55f;
		p.rArmRx = p.lArmRx = -1.2f;
		p.rArmRy = -0.55f + open;
		p.lArmRy = 0.55f - open;
		p.rElbow = p.lElbow = -0.75f;
		p.hipY = Math.abs(sin(k / 2)) * 0.5f;
		p.headRx = 0.08f + sin(k) * 0.04f;
	}

	private static void sit(float t, EmotePose p) {
		float k = t / 4 * 2 * PI;
		// on the floor, leaning back on straight arms; hips turned back, so the legs get the same angle to stay flat
		float back = 0.24f;
		p.hipRx = -back;
		p.hipY = 10f;
		p.hipZ = -1f;
		p.waistRx = -0.04f;
		// both legs straight along the floor, one knee just off straight (about 4 degrees)
		p.rLegRx = -PI / 2 + back;
		p.lLegRx = -PI / 2 + back;
		p.rLegRz = 0.05f;
		p.lLegRz = -0.05f;
		p.rKnee = 0.07f;
		p.rArmRx = 0.6f;
		p.lArmRx = 0.6f;
		p.rArmRz = 0.24f;
		p.lArmRz = -0.24f;
		p.rElbow = -0.08f;
		p.lElbow = -0.08f;
		p.headRx = 0.2f + sin(k) * 0.03f;
		p.headRy = sin(k) * 0.14f;
	}

	private static void lie(float t, EmotePose p) {
		float k = t / 4 * 2 * PI;
		p.hipRx = -PI / 2;
		p.hipY = 10f;
		p.hipZ = 2f;
		p.waistRx = -0.06f + sin(k) * 0.015f;
		p.headRx = 0.5f;
		p.headRy = sin(k) * 0.08f;
		// hands behind the head
		p.rArmRx = -2.75f;
		p.lArmRx = -2.75f;
		p.rArmRz = 0.42f;
		p.lArmRz = -0.42f;
		p.rElbow = -2.1f;
		p.lElbow = -2.1f;
		// one knee up, the other leg thrown over it, the foot swings
		p.rLegRx = -1.0f;
		p.rKnee = 1.95f;
		p.lLegRx = -1.3f;
		p.lLegRy = 0.42f;
		p.lLegRz = 0.1f;
		p.lKnee = 1.0f + sin(k * 2) * 0.14f;
	}

	private static Keys.Pose pose() {
		return new Keys.Pose();
	}

	/** Deep on the bent leg, the other one thrown forward. {@code side} 1 kicks the right leg. */
	private static Keys.Pose kick(int side) {
		Keys.Pose p = pose().waist(-0.1f, -0.12f * side, 0).head(0.05f, 0.08f * side, 0).hip(0, 0.14f * side, 0);
		if (side > 0) return p.legR(-1.42f, 0, 0.26f, 0.06f).legL(-1.95f, 0, -0.2f, 2.45f);
		return p.legL(-1.42f, 0, -0.26f, 0.06f).legR(-1.95f, 0, 0.2f, 2.45f);
	}

	private static Keys.Pose handsOnHips(Keys.Pose p) {
		return p.armR(0.25f, -1.2f, 0.78f, -1.6f).armL(0.25f, 1.2f, -0.78f, -1.6f);
	}

	private static Keys.Pose armsOut(Keys.Pose p) {
		return p.armR(-0.2f, 0, 1.48f, -0.18f).armL(-0.2f, 0, -1.48f, -0.18f);
	}

	private static Keys squatDance() {
		Keys keys = new Keys(4.8f);
		for (int i = 0; i < 8; i++) {
			int side = i % 2 == 0 ? 1 : -1;
			keys.at(i * 0.4f, handsOnHips(kick(side)));
		}
		for (int i = 8; i < 12; i++) {
			int side = i % 2 == 0 ? 1 : -1;
			keys.at(i * 0.4f, armsOut(kick(side)));
		}
		return keys;
	}

	private static void flex(float t, EmotePose p) {
		float up = ramp(t, 0, 0.3f) * (1 - ramp(t, 2.6f, 2.95f));
		float pump = Math.max(0, sin(t * 8)) * 0.12f;
		p.rArmRz = 1.62f * up;
		p.lArmRz = -1.62f * up;
		p.rArmRy = 1.45f * up;
		p.lArmRy = -1.45f * up;
		p.rElbow = (-1.95f - pump * 2) * up;
		p.lElbow = (-1.95f - pump * 2) * up;
		p.waistRx = -0.14f * up;
		p.headRy = sin(t * 2) * 0.28f * up;
		p.headRx = -0.12f * up;
		p.rLegRz = 0.14f * up;
		p.lLegRz = -0.14f * up;
	}

	private static void moonwalk(float t, EmotePose p) {
		float glide = ramp(t, 0.15f, 3.7f);
		float k = t / 0.9f * 2 * PI;
		float a = sin(k), r = Math.max(0, a), l = Math.max(0, -a);
		// the model slides backwards on the spot: nothing really moves, the game position stays the same
		p.hipZ = glide * 40f;
		p.hipY = 0.5f + Math.abs(a) * 0.4f;
		p.waistRx = 0.1f;
		p.rArmRx = -a * 0.42f;
		p.lArmRx = a * 0.42f;
		p.rElbow = -0.5f;
		p.lElbow = -0.5f;
		// the raised heel slips back, the flat foot drags
		p.rLegRx = -0.05f + l * 0.45f - r * 0.2f;
		p.lLegRx = -0.05f + r * 0.45f - l * 0.2f;
		p.rKnee = 0.08f + r * 0.95f;
		p.lKnee = 0.08f + l * 0.95f;
		p.headRy = sin(t * 2.2f) * 0.1f;
	}
}
