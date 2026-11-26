package com.zerovisual.emote;

/** A dance as a table of joint angles taken from a video, one line per frame, played in a loop. */
final class DanceTrack implements Emote.Animation {
	private final float rate;
	private final float[][] frames;

	/** @param table frames separated by ';', values by ',', in hundredths (see tools/dance_bake.py for the order) */
	DanceTrack(float rate, String table) {
		this.rate = rate;
		String[] rows = table.split(";");
		frames = new float[rows.length][];
		for (int i = 0; i < rows.length; i++) {
			String[] cells = rows[i].split(",");
			frames[i] = new float[cells.length];
			for (int j = 0; j < cells.length; j++) frames[i][j] = Integer.parseInt(cells[j]) / 100f;
		}
	}

	float duration() {
		return frames.length / rate;
	}

	@Override
	public void sample(float t, EmotePose p) {
		float at = t * rate;
		int i = (int) at % frames.length;
		float[] a = frames[i], b = frames[(i + 1) % frames.length];
		float k = at - (float) Math.floor(at);
		int n = 0;
		p.hipX = mix(a, b, n++, k);
		p.hipY = mix(a, b, n++, k);
		p.hipRx = mix(a, b, n++, k);
		p.hipRy = mix(a, b, n++, k);
		p.hipRz = mix(a, b, n++, k);
		p.waistRx = mix(a, b, n++, k);
		p.waistRy = mix(a, b, n++, k);
		p.waistRz = mix(a, b, n++, k);
		p.headRx = mix(a, b, n++, k);
		p.headRy = mix(a, b, n++, k);
		p.headRz = mix(a, b, n++, k);
		p.rArmRx = mix(a, b, n++, k);
		p.rArmRy = mix(a, b, n++, k);
		p.rArmRz = mix(a, b, n++, k);
		p.rElbow = mix(a, b, n++, k);
		p.lArmRx = mix(a, b, n++, k);
		p.lArmRy = mix(a, b, n++, k);
		p.lArmRz = mix(a, b, n++, k);
		p.lElbow = mix(a, b, n++, k);
		p.rLegRx = mix(a, b, n++, k);
		p.rLegRy = mix(a, b, n++, k);
		p.rLegRz = mix(a, b, n++, k);
		p.rKnee = mix(a, b, n++, k);
		p.lLegRx = mix(a, b, n++, k);
		p.lLegRy = mix(a, b, n++, k);
		p.lLegRz = mix(a, b, n++, k);
		p.lKnee = mix(a, b, n, k);
	}

	/** Angles were unwrapped over the whole dance: at the end of the loop they come back by whole turns. */
	private static float mix(float[] a, float[] b, int n, float k) {
		float d = b[n] - a[n];
		d -= (float) (Math.rint(d / (2 * Math.PI)) * 2 * Math.PI);
		return a[n] + d * k;
	}
}
