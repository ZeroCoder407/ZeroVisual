package com.zerovisual.gui;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.hud.NowPlaying;
import com.zerovisual.module.impl.utilities.MusicMenu;
import com.zerovisual.music.MusicCatalog;
import com.zerovisual.music.MusicPlayer;
import com.zerovisual.music.MusicQueue;
import com.zerovisual.music.MusicRoom;
import com.zerovisual.music.MusicTrack;
import com.zerovisual.music.SystemMedia;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.render.DrawContext;
import com.zerovisual.util.Lang;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * The music player screen: search tabs for Audius and Jamendo, the players listening on this server, and the player
 * controls at the bottom. Results stay while the game runs, so reopening shows the same list.
 */
public class MusicScreen extends ZvScreen {
	/** The panel: 440 x 300, smaller in a small window. */
	private int W = 440, H = 300;
	private static final int ROW = 24;
	private static final int FOOTER = 52;

	private enum Tab { SEARCH, TOGETHER }

	/** The list of one service. */
	private static final class Results {
		List<MusicTrack> tracks = Collections.emptyList();
		String query = "";
		String shownQuery = "";
		String status = "";
		boolean loading;
		boolean loaded;
		int request;
	}

	private static final Map<Tab, Results> RESULTS = new HashMap<>();
	private static final Set<String> REQUESTED = new HashSet<>();
	private static Tab tab = Tab.SEARCH;

	private final GuiScreen parent;
	private TextField searchField;
	private String linkToOpen;
	private float scroll;
	private boolean draggingVolume;
	private UUID copied;
	private long copiedAt;

	public MusicScreen(GuiScreen parent) {
		this.parent = parent;
	}

	private static String t(String key, String fallback) {
		return Lang.orElse("zerovisual.music." + key, fallback);
	}

	private static MusicMenu module() {
		return ZeroVisualClient.get().modules().get(MusicMenu.class);
	}

	private static Results results(Tab t) {
		return RESULTS.computeIfAbsent(t, k -> new Results());
	}

	private int px() {
		return (width - W) / 2;
	}

	private int py() {
		return (height - H) / 2;
	}

	// ---- layout ----

	private float listTop() {
		return py() + 78;
	}

	private float listBottom() {
		return py() + H - FOOTER - 14;
	}

	private float footerTop() {
		return py() + H - FOOTER - 8;
	}

	private float[] tabBounds(Tab t) {
		float x = px() + W - 16;
		for (int i = Tab.values().length - 1; i >= 0; i--) {
			Tab each = Tab.values()[i];
			float w = Render2D.textWidth(tabLabel(each)) + 18;
			x -= w;
			if (each == t) return new float[]{x, py() + 12, w, 18};
			x -= 4;
		}
		return new float[4];
	}

	private static String tabLabel(Tab t) {
		return t == Tab.SEARCH ? t("search", "Search") : t("together", "Together");
	}

	private float[] shareChip() {
		return new float[]{px() + W - 16 - 190, py() + 42, 92, 16};
	}

	private float[] gameMusicChip() {
		return new float[]{px() + W - 16 - 94, py() + 42, 94, 16};
	}

	private float[] seekBar() {
		return new float[]{px() + 22, footerTop() + 29, W - 44, 3};
	}

	private float[] controlBounds(int i) {
		float cx = px() + W / 2f + (i - 1) * 26;
		return new float[]{cx - 11, footerTop() + 34, 22, 14};
	}

	private float[] muteBounds() {
		return new float[]{px() + W - 22 - 98, footerTop() + 34, 16, 14};
	}

	private float[] volumeBar() {
		return new float[]{px() + W - 22 - 76, footerTop() + 39, 76, 3};
	}

	private float[] linkBounds() {
		MusicTrack track = MusicPlayer.current();
		String label = track == null ? "" : linkLabel(track);
		return new float[]{px() + 22, footerTop() + 36, Render2D.textWidth(label), 10};
	}

	/** What the YouTube tab needs from the player, or null while it plays fine. */
	private static String youtubeHint(MusicTrack track) {
		if (!com.zerovisual.music.YouTubeLink.SERVICE.equals(track.svc)) return null;
		if (!com.zerovisual.music.YouTubeLink.pageOnline()) return t("youtube_open", "YouTube plays in a browser tab: click to open it");
		if (com.zerovisual.music.YouTubeLink.blocked()) return t("youtube_click", "Click the player tab in the browser once to start the sound");
		return null;
	}

	private float[] hintBounds() {
		return new float[]{px() + 22, footerTop() + 15, W - 44, 11};
	}

	private static String linkLabel(MusicTrack track) {
		// the system player names the app it plays in; there is no page to open
		return track.page.isEmpty() ? track.label : track.label + " ↗";
	}

	// ---- dev screenshots (DevScreenshots) ----

	public static void devPlay(int i) {
		Results r = results(tab);
		if (i < r.tracks.size()) MusicQueue.play(r.tracks, i);
	}

	public static void devSearch(String query) {
		if (net.minecraft.client.Minecraft.getMinecraft().currentScreen instanceof MusicScreen) {
			MusicScreen screen = (MusicScreen) net.minecraft.client.Minecraft.getMinecraft().currentScreen;
			screen.searchField.setText(query);
			screen.search();
		}
	}

	public static void devTab(int i) {
		if (net.minecraft.client.Minecraft.getMinecraft().currentScreen instanceof MusicScreen) {
			((MusicScreen) net.minecraft.client.Minecraft.getMinecraft().currentScreen).switchTab(Tab.values()[i]);
		}
	}

	// ---- setup ----

	@Override
	protected void init() {
		W = Math.max(300, Math.min(440, width - 16));
		H = Math.max(200, Math.min(300, height - 16));
		// drawn by renderBackground as a rounded box with a magnifier, like the other ZeroVisual fields
		searchField = new TextField(px() + 37, py() + 46, W - 32 - 196 - 27, 10);
		searchField.setMaxLength(80);
		searchField.setEditableColor(Theme.TEXT & 0xFFFFFF);
		searchField.setPlaceholder(t("search_hint", "Song or artist, Enter to search"));
		searchField.setText(results(tab).query);
		searchField.visible = tab != Tab.TOGETHER;
		addWidget(searchField);
		searchField.setFocused(tab != Tab.TOGETHER);
		if (tab != Tab.TOGETHER && !results(tab).loaded && !results(tab).loading) search();
	}

	private void switchTab(Tab next) {
		if (next == tab) return;
		if (tab != Tab.TOGETHER) results(tab).query = searchField.getText();
		tab = next;
		scroll = 0;
		searchField.visible = next != Tab.TOGETHER;
		if (next != Tab.TOGETHER) {
			searchField.setText(results(next).query);
			searchField.setFocused(true);
			if (!results(next).loaded && !results(next).loading) search();
		} else {
			searchField.setFocused(false);
		}
	}

	private void search() {
		Tab target = tab;
		Results r = results(target);
		r.query = searchField.getText();
		String query = r.query.trim();
		r.loading = true;
		r.status = "";
		int request = ++r.request;
		MusicCatalog.search(MusicCatalog.ALL, query).whenComplete((list, error) -> client.addScheduledTask(() -> {
			if (request != r.request) return;
			r.loading = false;
			r.loaded = true;
			r.shownQuery = query;
			if (error != null) {
				r.tracks = Collections.emptyList();
				r.status = reason(error);
			} else {
				r.tracks = list;
				r.status = list.isEmpty() ? t("nothing", "Nothing found") : "";
			}
			if (tab == target) scroll = 0;
		}));
	}

	private static String reason(Throwable error) {
		Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		String key = cause instanceof MusicCatalog.CatalogException ? cause.getMessage() : "network";
		if ("not_configured".equals(key)) return t("error_not_configured", "This service is not set up on the relay yet");
		if ("upstream".equals(key)) return t("error_upstream", "The service does not answer, try later");
		return t("error_network", "No connection to the ZeroVisual relay");
	}

	// ---- rendering ----

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.renderBackground(ctx, mouseX, mouseY, delta);
		int x = px(), y = py();
		Render2D.shadow(ctx, x, y + 4, W, H, 12, 18, 0xA0000000);
		Render2D.rect(ctx, x, y, W, H, 12, Theme.BACKGROUND);
		Render2D.outline(ctx, x, y, W, H, 12, 1, 0x14FFFFFF);
		if (tab != Tab.TOGETHER) {
			float fw = W - 32 - 196;
			boolean focused = searchField.isFocused();
			Render2D.rect(ctx, x + 16, y + 41, fw, 18, 6, Theme.SURFACE);
			Render2D.outline(ctx, x + 16, y + 41, fw, 18, 6, 1, focused ? Theme.accent() : 0x18FFFFFF);
			int icon = focused ? Theme.accentLight() : Theme.TEXT_DIM;
			Render2D.outline(ctx, x + 23, y + 45.5f, 7, 7, 3.5f, 1.2f, icon);
			Render2D.rect(ctx, x + 29, y + 52, 3, 1.4f, 0.7f, icon);
		}
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		int x = px(), y = py();
		int accent = Theme.accent();
		Render2D.text(ctx, t("title", "Music"), x + 16, y + 13, Theme.TEXT);
		Render2D.text(ctx, t("subtitle", "Full tracks, free and legal"), x + 16, y + 24, Theme.TEXT_DIM);

		for (Tab each : Tab.values()) {
			float[] b = tabBounds(each);
			boolean on = each == tab;
			boolean hover = inside(b, mouseX, mouseY);
			Render2D.rect(ctx, b[0], b[1], b[2], b[3], 6, on ? ColorUtil.withAlpha(accent, 0.85f) : hover ? 0x22FFFFFF : 0x12FFFFFF);
			Render2D.centeredText(ctx, tabLabel(each), b[0] + b[2] / 2, b[1] + 5, on ? 0xFFFFFFFF : Theme.TEXT_MUTED);
		}

		MusicMenu m = module();
		chip(ctx, shareChip(), t("share", "Together") + ": " + onOff(m.share.get()), m.share.get(), mouseX, mouseY);
		chip(ctx, gameMusicChip(), t("game_music", "Game music") + ": " + onOff(!m.muteGameMusic.get()), !m.muteGameMusic.get(), mouseX, mouseY);

		if (tab == Tab.TOGETHER) renderTogether(ctx, mouseX, mouseY);
		else renderTracks(ctx, mouseX, mouseY);
		renderFooter(ctx, mouseX, mouseY);
	}

	private static String onOff(boolean on) {
		return on ? t("on", "on") : t("off", "off");
	}

	private void chip(DrawContext ctx, float[] b, String label, boolean on, int mouseX, int mouseY) {
		boolean hover = inside(b, mouseX, mouseY);
		Render2D.rect(ctx, b[0], b[1], b[2], b[3], 5, hover ? 0x22FFFFFF : 0x12FFFFFF);
		if (on) Render2D.outline(ctx, b[0], b[1], b[2], b[3], 5, 1, ColorUtil.withAlpha(Theme.accent(), 0.7f));
		Render2D.centeredText(ctx, NowPlaying.fit(label, b[2] - 8), b[0] + b[2] / 2, b[1] + 4, on ? Theme.TEXT : Theme.TEXT_MUTED);
	}

	private void renderTracks(DrawContext ctx, int mouseX, int mouseY) {
		Results r = results(tab);
		int x = px();
		String heading = r.loading ? t("loading", "Searching...")
			: r.shownQuery.isEmpty() ? t("popular", "Popular now") : t("results", "Results") + ": " + r.shownQuery;
		Render2D.text(ctx, NowPlaying.fit(heading, W - 40), x + 16, py() + 65, Theme.TEXT_MUTED);
		if (!r.status.isEmpty()) {
			Render2D.centeredText(ctx, r.status, x + W / 2f, listTop() + 30, r.tracks.isEmpty() && r.loaded && !r.status.equals(t("nothing", "Nothing found")) ? 0xFFF43F5E : Theme.TEXT_DIM);
		}
		clampScroll(r.tracks.size());
		float top = listTop(), bottom = listBottom();
		ctx.enableScissor(x + 8, (int) top, x + W - 8, (int) bottom);
		MusicTrack playing = MusicPlayer.current();
		for (int i = 0; i < r.tracks.size(); i++) {
			float ry = top + i * ROW - scroll;
			if (ry + ROW < top || ry > bottom) continue;
			MusicTrack track = r.tracks.get(i);
			boolean current = playing != null && playing.same(track.svc, track.id);
			boolean hover = mouseY >= top && mouseY < bottom && mouseX >= x + 12 && mouseX < x + W - 12 && mouseY >= ry && mouseY < ry + ROW;
			if (current || hover) Render2D.rect(ctx, x + 12, ry + 1, W - 24, ROW - 2, 6, current ? ColorUtil.withAlpha(Theme.accent(), 0.18f) : 0x10FFFFFF);
			String number = current ? (MusicPlayer.isPaused() ? "❚❚" : "♪") : String.valueOf(i + 1);
			Render2D.centeredText(ctx, number, x + 26, ry + 8, current ? Theme.accentLight() : Theme.TEXT_DIM);
			String duration = MusicTrack.time(track.duration * 1000L);
			float textW = W - 24 - 30 - Render2D.textWidth(duration) - 16;
			Render2D.text(ctx, NowPlaying.fit(track.title, textW), x + 40, ry + 3, current ? Theme.accentLight() : Theme.TEXT);
			// where it comes from, after the artist: every service has its own catalog and license
			Render2D.text(ctx, NowPlaying.fit(track.artist.isEmpty() ? track.label : track.artist + "  ·  " + track.label, textW), x + 40, ry + 13, Theme.TEXT_DIM);
			Render2D.text(ctx, duration, x + W - 22 - Render2D.textWidth(duration), ry + 8, Theme.TEXT_DIM);
		}
		ctx.disableScissor();
	}

	private void renderTogether(DrawContext ctx, int mouseX, int mouseY) {
		int x = px();
		List<MusicRoom.Listening> list = together();
		UUID following = MusicRoom.following();
		String heading = following != null ? t("following", "You listen together with") + " " + name(following) : t("together_heading", "Players on this server with ZeroVisual");
		Render2D.text(ctx, NowPlaying.fit(heading, W - 40), x + 16, py() + 65, following != null ? Theme.accentLight() : Theme.TEXT_MUTED);
		String hint = client.thePlayer == null ? t("together_no_world", "Join a server to listen together")
			: !module().share.get() ? t("together_off", "Turn on \"Together\" to see the others")
			: list.isEmpty() ? t("together_empty", "Nobody listens to anything right now") : "";
		if (!hint.isEmpty()) Render2D.centeredText(ctx, hint, x + W / 2f, listTop() + 30, Theme.TEXT_DIM);
		clampScroll(list.size());
		float top = listTop(), bottom = listBottom();
		ctx.enableScissor(x + 8, (int) top, x + W - 8, (int) bottom);
		for (int i = 0; i < list.size(); i++) {
			float ry = top + i * ROW - scroll;
			if (ry + ROW < top || ry > bottom) continue;
			MusicRoom.Listening l = list.get(i);
			boolean followed = l.player.equals(following);
			if (followed) Render2D.rect(ctx, x + 12, ry + 1, W - 24, ROW - 2, 6, ColorUtil.withAlpha(Theme.accent(), 0.18f));
			MusicTrack track = trackOf(l);
			boolean system = SystemMedia.SERVICE.equals(l.svc);
			String what = track == null ? "…" : track.title + (track.artist.isEmpty() ? "" : " — " + track.artist) + (system ? "  ·  " + track.label : "");
			float[] b = joinButton(i);
			Render2D.text(ctx, NowPlaying.fit(name(l.player), b[0] - x - 30), x + 22, ry + 3, Theme.TEXT);
			Render2D.text(ctx, NowPlaying.fit((l.paused ? "❚❚ " : "♪ ") + what, b[0] - x - 30), x + 22, ry + 13, Theme.TEXT_DIM);
			boolean hover = inside(b, mouseX, mouseY) && mouseY >= top && mouseY < bottom;
			Render2D.rect(ctx, b[0], b[1], b[2], b[3], 5, followed ? ColorUtil.withAlpha(Theme.accent(), 0.85f) : hover ? 0x30FFFFFF : 0x18FFFFFF);
			// an app of somebody else cannot be played here: the name goes to the clipboard, to find it in one's own
			String button = !system ? (followed ? t("leave", "Stop") : t("join", "Listen"))
				: l.player.equals(copied) && System.currentTimeMillis() - copiedAt < 2000 ? t("copied", "Copied") : t("copy", "Copy");
			Render2D.centeredText(ctx, button, b[0] + b[2] / 2, b[1] + 4, Theme.TEXT);
		}
		ctx.disableScissor();
	}

	private float[] joinButton(int i) {
		return new float[]{px() + W - 22 - 70, listTop() + i * ROW - scroll + 5, 70, 14};
	}

	private List<MusicRoom.Listening> together() {
		if (client.thePlayer == null || !module().share.get()) return Collections.emptyList();
		List<MusicRoom.Listening> list = new ArrayList<>(MusicRoom.listening());
		list.sort((a, b) -> name(a.player).compareToIgnoreCase(name(b.player)));
		return list;
	}

	private String name(UUID player) {
		NetworkPlayerInfo entry = client.getNetHandler() == null ? null : client.getNetHandler().getPlayerInfo(player);
		return entry != null ? entry.getGameProfile().getName() : player.toString().substring(0, 8);
	}

	/** The track another player listens to, fetched once when it is not known yet. */
	private static MusicTrack trackOf(MusicRoom.Listening l) {
		if (SystemMedia.SERVICE.equals(l.svc)) return new MusicTrack(l.svc, l.id, l.app, l.title, l.artist, 0, "", "");
		MusicTrack known = MusicCatalog.known(l.svc, l.id);
		if (known == null && REQUESTED.add(l.svc + ":" + l.id)) MusicCatalog.track(l.svc, l.id);
		return known;
	}

	private void renderFooter(DrawContext ctx, int mouseX, int mouseY) {
		float x = px() + 12, y = footerTop(), w = W - 24;
		Render2D.rect(ctx, x, y, w, FOOTER, 8, Theme.SURFACE);
		MusicTrack track = MusicPlayer.current();
		int accent = Theme.accent();
		if (track == null) {
			Render2D.text(ctx, t("idle", "Pick a track, or play music in any app"), x + 10, y + 8, Theme.TEXT_DIM);
		} else {
			long length = track.duration * 1000L;
			long position = Math.min(length, MusicPlayer.position());
			String time = MusicPlayer.isLoading() ? t("buffering", "loading...") : MusicTrack.time(position) + " / " + MusicTrack.time(length);
			float textW = w - 30 - Render2D.textWidth(time);
			Render2D.text(ctx, NowPlaying.fit(track.title, textW), x + 10, y + 7, Theme.TEXT);
			String tabHint = youtubeHint(track);
			if (tabHint != null) Render2D.text(ctx, NowPlaying.fit(tabHint, textW), x + 10, y + 17, inside(hintBounds(), mouseX, mouseY) ? Theme.TEXT : Theme.accentLight());
			else Render2D.text(ctx, NowPlaying.fit(track.artist, textW), x + 10, y + 17, Theme.TEXT_DIM);
			Render2D.text(ctx, time, x + w - 10 - Render2D.textWidth(time), y + 7, Theme.TEXT_MUTED);
			float[] bar = seekBar();
			float p = length > 0 ? position / (float) length : 0;
			Render2D.rect(ctx, bar[0], bar[1], bar[2], bar[3], 1.5f, Theme.TRACK);
			if (p > 0) Render2D.gradientH(ctx, bar[0], bar[1], bar[2] * p, bar[3], 1.5f, accent, Theme.accentLight());
			if (MusicPlayer.canSeek() && inside(new float[]{bar[0], bar[1] - 4, bar[2], bar[3] + 8}, mouseX, mouseY)) {
				Render2D.circle(ctx, bar[0] + bar[2] * p, bar[1] + 1.5f, 3.5f, Theme.accentLight());
			}
			float[] link = linkBounds();
			Render2D.text(ctx, linkLabel(track), link[0], link[1], !track.page.isEmpty() && inside(link, mouseX, mouseY) ? Theme.accentLight() : Theme.TEXT_DIM);
		}

		String[] icons = {"◀◀", MusicPlayer.isPaused() || track == null ? "▶" : "❚❚", "▶▶"};
		for (int i = 0; i < 3; i++) {
			float[] b = controlBounds(i);
			boolean hover = inside(b, mouseX, mouseY);
			if (i == 1) Render2D.rect(ctx, b[0], b[1], b[2], b[3], 7, hover ? Theme.accentLight() : accent);
			else if (hover) Render2D.rect(ctx, b[0], b[1], b[2], b[3], 7, 0x22FFFFFF);
			Render2D.centeredText(ctx, icons[i], b[0] + b[2] / 2, b[1] + 3, i == 1 ? 0xFFFFFFFF : Theme.TEXT_MUTED);
		}

		MusicMenu m = module();
		// the volume of the system player is the app's own
		float alpha = Render2D.getAlpha();
		if (MusicPlayer.isSystem()) Render2D.setAlpha(alpha * 0.35f);
		float[] mute = muteBounds();
		Render2D.centeredText(ctx, m.muted.get() ? "×" : "♪", mute[0] + mute[2] / 2, mute[1] + 3,
			m.muted.get() ? 0xFFF43F5E : inside(mute, mouseX, mouseY) ? Theme.TEXT : Theme.TEXT_MUTED);
		float[] vol = volumeBar();
		float v = (float) m.volume.getProgress();
		Render2D.rect(ctx, vol[0], vol[1], vol[2], vol[3], 1.5f, Theme.TRACK);
		Render2D.rect(ctx, vol[0], vol[1], vol[2] * v, vol[3], 1.5f, m.muted.get() ? Theme.TEXT_DIM : accent);
		Render2D.circle(ctx, vol[0] + vol[2] * v, vol[1] + 1.5f, 3.5f, m.muted.get() ? Theme.TEXT_DIM : Theme.accentLight());
		Render2D.setAlpha(alpha);
	}

	private void clampScroll(int rows) {
		float max = Math.max(0, rows * ROW - (listBottom() - listTop()));
		scroll = Math.max(0, Math.min(scroll, max));
	}

	private static boolean inside(float[] b, double mx, double my) {
		return mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (super.mouseClicked(mx, my, button)) return true;
		if (button != 0) return false;
		MusicMenu m = module();
		for (Tab each : Tab.values()) {
			if (inside(tabBounds(each), mx, my)) {
				switchTab(each);
				return true;
			}
		}
		if (inside(shareChip(), mx, my)) {
			m.share.toggle();
			if (!m.share.get()) MusicRoom.unfollow();
			return true;
		}
		if (inside(gameMusicChip(), mx, my)) {
			m.muteGameMusic.toggle();
			return true;
		}
		if (my >= listTop() && my < listBottom() && clickList(mx, my)) return true;

		MusicTrack track = MusicPlayer.current();
		float[] bar = seekBar();
		if (track != null && MusicPlayer.canSeek() && inside(new float[]{bar[0], bar[1] - 4, bar[2], bar[3] + 8}, mx, my)) {
			MusicRoom.unfollow();
			MusicPlayer.seek((long) ((mx - bar[0]) / bar[2] * track.duration * 1000L));
			return true;
		}
		if (track != null && youtubeHint(track) != null && inside(hintBounds(), mx, my)) {
			com.zerovisual.music.YouTubeLink.openTab();
			return true;
		}
		if (track != null && !track.page.isEmpty() && inside(linkBounds(), mx, my)) {
			linkToOpen = track.page;
			client.displayGuiScreen(new GuiConfirmOpenLink(this, track.page, 0, true));
			return true;
		}
		for (int i = 0; i < 3; i++) {
			if (inside(controlBounds(i), mx, my)) {
				control(i);
				return true;
			}
		}
		if (MusicPlayer.isSystem()) return false;
		if (inside(muteBounds(), mx, my)) {
			m.muted.toggle();
			return true;
		}
		float[] vol = volumeBar();
		if (inside(new float[]{vol[0] - 4, vol[1] - 5, vol[2] + 8, vol[3] + 10}, mx, my)) {
			draggingVolume = true;
			setVolume(mx);
			return true;
		}
		return false;
	}

	private boolean clickList(double mx, double my) {
		int i = (int) ((my - listTop() + scroll) / ROW);
		if (tab == Tab.TOGETHER) {
			List<MusicRoom.Listening> list = together();
			if (i < 0 || i >= list.size() || !inside(joinButton(i), mx, my)) return false;
			MusicRoom.Listening picked = list.get(i);
			UUID player = picked.player;
			if (SystemMedia.SERVICE.equals(picked.svc)) {
				GuiScreen.setClipboardString(picked.artist.isEmpty() ? picked.title : picked.artist + " — " + picked.title);
				copied = player;
				copiedAt = System.currentTimeMillis();
			} else if (player.equals(MusicRoom.following())) {
				MusicRoom.unfollow();
			} else {
				MusicQueue.clear();
				MusicRoom.follow(player);
			}
			return true;
		}
		Results r = results(tab);
		if (i < 0 || i >= r.tracks.size() || mx < px() + 12 || mx >= px() + W - 12) return false;
		MusicRoom.unfollow();
		MusicTrack picked = r.tracks.get(i);
		MusicTrack playing = MusicPlayer.current();
		if (playing != null && playing.same(picked.svc, picked.id)) MusicPlayer.togglePause();
		else MusicQueue.play(r.tracks, i);
		return true;
	}

	private void control(int i) {
		if (i == 1) {
			if (MusicPlayer.current() != null) MusicPlayer.togglePause();
			return;
		}
		MusicRoom.unfollow();
		if (i == 0) MusicQueue.previous();
		else MusicQueue.next();
	}

	private void setVolume(double mx) {
		float[] vol = volumeBar();
		module().volume.setProgress(Math.max(0, Math.min(1, (mx - vol[0]) / vol[2])));
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (draggingVolume) {
			setVolume(mx);
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		draggingVolume = false;
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		scroll -= (float) vertical * ROW * 1.5f;
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		boolean typing = searchField.isFocused() && searchField.visible;
		if (typing && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)) {
			search();
			return true;
		}
		if (!typing && keyCode == Keyboard.KEY_SPACE && MusicPlayer.current() != null) {
			MusicPlayer.togglePause();
			return true;
		}
		if (!typing && MusicMenu.isMusicKey(keyCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		if (tab != Tab.TOGETHER) results(tab).query = searchField.getText();
		ZeroVisualClient.get().config().save();
		client.displayGuiScreen(parent);
	}

	/** The answer of the link question. */
	@Override
	public void confirmClicked(boolean yes, int id) {
		if (yes && linkToOpen != null) {
			try {
				org.lwjgl.Sys.openURL(linkToOpen);
			} catch (RuntimeException ignored) {
				// no browser to open it in
			}
		}
		client.displayGuiScreen(this);
	}
}
