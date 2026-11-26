package com.zerovisual.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerovisual.emote.EmoteSync;

import java.security.SecureRandom;

/**
 * YouTube tracks: the game does not touch their audio. They play in the official YouTube player on the relay's
 * player page in a browser tab, which this class steers through the relay (/link/&lt;secret&gt;, see relay/src/link.js)
 * and which reports back what it plays. {@link MusicPlayer} hands YouTube tracks here. No Minecraft classes here.
 */
public final class YouTubeLink {
	public static final String SERVICE = "youtube";

	/** Opens an address in the system browser (the game knows how). */
	public interface Opener {
		void open(String url);
	}

	private static final long RETRY_MS = 3000;
	/** A started track that comes in later than this (loading, ads) is moved on by the wait when listening together. */
	private static final long LATE_MS = 1500;
	/** After a command from here, the page's reports wait this long before they may change the pause state. */
	private static final long COMMAND_GRACE_MS = 1500;

	private static final String SECRET = secret();
	private static volatile Opener opener;
	private static volatile LinkSocket socket;
	private static volatile boolean connecting;
	private static volatile long nextTry;
	private static volatile boolean pageOnline;

	// what the game wants
	private static volatile MusicTrack track;
	private static volatile boolean paused;
	private static volatile long startMs;
	private static volatile long loadAt;
	private static volatile long commandAt;
	private static volatile boolean live;
	private static volatile boolean compensated;
	private static volatile boolean told;
	private static volatile int sentVolume = -1;

	// what the page reports
	private static volatile String stateId;
	private static volatile String state = "";
	private static volatile long statePos;
	private static volatile long stateAt;

	private YouTubeLink() {
	}

	public static void setOpener(Opener o) {
		opener = o;
	}

	/** The player page of this game: the secret is in the fragment, which browsers never send to a server. */
	public static String playerUrl() {
		String base = MusicCatalog.httpBase();
		return base == null ? null : base + "/player#t=" + SECRET;
	}

	/** Opens the player page in the browser. */
	public static void openTab() {
		Opener o = opener;
		String url = playerUrl();
		if (o != null && url != null) o.open(url);
	}

	/** Whether the player page is open and connected. */
	public static boolean pageOnline() {
		return pageOnline;
	}

	/** The browser held the start back until the page is clicked once. */
	public static boolean blocked() {
		MusicTrack t = track;
		return t != null && t.id.equals(stateId) && "blocked".equals(state);
	}

	// ---- driven by MusicPlayer ----

	static void load(MusicTrack t, long fromMs, boolean pause, boolean isLive) {
		boolean first = track == null && socket == null;
		track = t;
		paused = pause;
		startMs = fromMs;
		loadAt = commandAt = System.currentTimeMillis();
		live = isLive;
		compensated = !isLive || pause;
		told = false;
		stateId = null;
		state = "";
		ensureConnected();
		sendLoad();
		// the first YouTube track of the session opens the player page; later ones use the open tab
		if (first && !pageOnline) openTab();
	}

	static void setPaused(boolean pause) {
		if (track == null || paused == pause) return;
		statePos = position();
		stateAt = System.currentTimeMillis();
		paused = pause;
		commandAt = stateAt;
		send(pause ? "{\"t\":\"pause\"}" : "{\"t\":\"play\"}");
	}

	static void seek(long ms, boolean isLive) {
		if (track == null) return;
		startMs = statePos = ms;
		stateAt = commandAt = System.currentTimeMillis();
		if (isLive && !paused) {
			loadAt = stateAt;
			compensated = false;
		}
		send("{\"t\":\"seek\",\"pos\":" + ms + "}");
	}

	static void stop() {
		if (track != null) send("{\"t\":\"stop\"}");
		track = null;
	}

	static MusicTrack track() {
		return track;
	}

	static boolean isPaused() {
		return paused;
	}

	static boolean isLoading() {
		MusicTrack t = track;
		if (t == null) return false;
		if (!pageOnline || !t.id.equals(stateId)) return true;
		return "buffering".equals(state) || "blocked".equals(state) || state.isEmpty();
	}

	static long position() {
		MusicTrack t = track;
		if (t == null) return 0;
		if (!t.id.equals(stateId)) return startMs;
		if ("playing".equals(state) && !paused) return statePos + System.currentTimeMillis() - stateAt;
		return statePos;
	}

	/** Every game tick: keeps the link up while YouTube is in use and passes the volume on. */
	public static void tick() {
		if (track == null && socket == null) return;
		ensureConnected();
		int volume = Math.round(MusicPlayer.gain() * 100);
		if (pageOnline && volume != sentVolume) {
			sentVolume = volume;
			send("{\"t\":\"volume\",\"v\":" + volume + "}");
		}
	}

	// ---- the connection ----

	private static void sendLoad() {
		MusicTrack t = track;
		if (t != null) send("{\"t\":\"load\",\"id\":\"" + t.id + "\",\"pos\":" + startMs + ",\"paused\":" + paused + "}");
	}

	private static void send(String text) {
		LinkSocket s = socket;
		if (s != null) s.send(text);
	}

	private static void ensureConnected() {
		if (socket != null || connecting || System.currentTimeMillis() < nextTry || EmoteSync.RELAY_URL.isEmpty()) return;
		connecting = true;
		LinkSocket.open(EmoteSync.RELAY_URL + "/link/" + SECRET + "?role=game", new LinkSocket.Handler() {
			@Override
			public void text(String text) {
				receive(text);
			}

			@Override
			public void closed() {
				socket = null;
				pageOnline = false;
				nextTry = System.currentTimeMillis() + RETRY_MS;
			}
		}).whenComplete((s, error) -> {
			connecting = false;
			if (error != null) {
				nextTry = System.currentTimeMillis() + RETRY_MS;
				return;
			}
			socket = s;
			// the page may have been waiting already; it gets the track from where it is now
			if (track != null) {
				startMs = position();
				stateId = null;
				sendLoad();
			}
		});
	}

	private static void receive(String text) {
		JsonObject m = parse(text);
		String type = m.has("t") ? m.get("t").getAsString() : "";
		if ("peer".equals(type)) {
			pageOnline = m.get("on").getAsBoolean();
			if (pageOnline) {
				// a page opened (or reopened): it gets the track from where it is now
				sentVolume = -1;
				if (track != null) {
					startMs = position();
					loadAt = commandAt = System.currentTimeMillis();
					stateId = null;
					sendLoad();
				}
			}
			return;
		}
		if (!"state".equals(type)) return;
		MusicTrack t = track;
		if (t == null || !m.has("id") || !t.id.equals(m.get("id").getAsString())) return;
		long now = System.currentTimeMillis();
		String s = m.get("s").getAsString();
		stateId = t.id;
		state = s;
		statePos = m.has("pos") ? m.get("pos").getAsLong() : 0;
		stateAt = now;
		if ("playing".equals(s) && !compensated) {
			// listening together: the others went on while the video loaded
			compensated = true;
			long late = now - loadAt;
			if (late > LATE_MS) send("{\"t\":\"seek\",\"pos\":" + (startMs + late) + "}");
		}
		// paused or resumed in the tab itself
		if (now - commandAt > COMMAND_GRACE_MS && ("paused".equals(s) || "playing".equals(s)) && paused != "paused".equals(s)) {
			paused = "paused".equals(s);
			MusicPlayer.changed();
		}
		if (!told && "ended".equals(s)) {
			told = true;
			MusicPlayer.finished(t);
		} else if (!told && "error".equals(s)) {
			told = true;
			MusicPlayer.failed(t, "youtube");
		}
	}

	@SuppressWarnings("deprecation")
	private static JsonObject parse(String text) {
		// the old instance form: the Gson of Minecraft 1.8.9 has no static parseString
		JsonElement e = new JsonParser().parse(text);
		return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
	}

	private static String secret() {
		// tests: a fixed link, so the player page can be opened by hand
		String fixed = System.getProperty("zerovisual.linkSecret", "");
		if (fixed.matches("[0-9a-f]{32}")) return fixed;
		byte[] bytes = new byte[16];
		new SecureRandom().nextBytes(bytes);
		StringBuilder b = new StringBuilder();
		for (byte x : bytes) b.append(String.format("%02x", x & 0xFF));
		return b.toString();
	}
}
