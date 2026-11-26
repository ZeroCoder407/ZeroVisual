package com.zerovisual.module.impl.hud;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.music.MusicPlayer;
import com.zerovisual.music.MusicTrack;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import net.minecraft.client.gui.DrawContext;

/** The track MusicMenu plays: title, artist and a progress line. */
public class NowPlaying extends HudModule {
	private static final float W = 170, H = 36;

	public NowPlaying() {
		super("NowPlaying", "The track the music player plays");
		setEnabled(true);
	}

	@Override
	public boolean isDraggable() {
		return true;
	}

	@Override
	protected float[] defaultPosition(float screenW, float screenH, float w, float h) {
		// above the hotbar, hearts and armor: the sides belong to ModuleList, Potions and the minimap
		return new float[]{(screenW - w) / 2, screenH - h - 62};
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		MusicTrack track = MusicPlayer.current();
		if (track == null) return;
		float[] pos = place(ctx, W, H);
		float x = pos[0], y = pos[1];
		Theme.card(ctx, x, y, W, H);
		int accent = Theme.accent();

		String icon = MusicPlayer.isLoading() ? "…" : MusicPlayer.isPaused() ? "❚❚" : "♪";
		Render2D.text(ctx, icon, x + 8, y + 6, accent);
		float tx = x + 20, tw = W - 28;
		Render2D.text(ctx, fit(track.title, tw), tx, y + 6, Theme.TEXT);
		Render2D.text(ctx, fit(track.artist, tw), tx, y + 16, Theme.TEXT_DIM);

		long length = Math.max(1, track.duration * 1000L);
		float p = Math.min(1, MusicPlayer.position() / (float) length);
		Render2D.rect(ctx, x + 8, y + H - 7, W - 16, 2, 1, Theme.TRACK);
		if (p > 0) Render2D.gradientH(ctx, x + 8, y + H - 7, (W - 16) * p, 2, 1, accent, ColorUtil.brighten(accent, 1.35f));
	}

	/** The text cut to a width, with an ellipsis. */
	public static String fit(String text, float width) {
		if (Render2D.textWidth(text) <= width) return text;
		String dots = "…";
		int end = text.length();
		while (end > 0 && Render2D.textWidth(text.substring(0, end) + dots) > width) end--;
		return text.substring(0, end) + dots;
	}
}
