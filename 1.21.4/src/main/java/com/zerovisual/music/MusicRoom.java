package com.zerovisual.music;

import com.google.gson.JsonObject;
import com.zerovisual.emote.EmoteSync;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Listen together": through the relay room of the server (the same one the emotes use) every player with sharing on
 * tells which track they play and where in it; following a player keeps the own player on their track.
 * Only the service, the track id and the position go out. No Minecraft classes here.
 */
public final class MusicRoom {
	/** What another player listens to. */
	public static final class Listening {
		public final UUID player;
		public final String svc;
		public final String id;
		private final long pos;
		public final boolean paused;
		private final long at;
		/** For the system player only (nothing to look up): what it is and in which app. */
		public String title = "", artist = "", app = "";

		Listening(UUID player, String svc, String id, long pos, boolean paused) {
			this.player = player;
			this.svc = svc;
			this.id = id;
			this.pos = pos;
			this.paused = paused;
			this.at = System.currentTimeMillis();
		}

		/** Where they are now. */
		public long position() {
			return paused ? pos : pos + System.currentTimeMillis() - at;
		}
	}

	private static final long SEND_GAP_MS = 400;
	private static final long DRIFT_CHECK_MS = 5000;
	private static final long MAX_DRIFT_MS = 3000;

	private static final Map<UUID, Listening> OTHERS = new ConcurrentHashMap<>();
	private static volatile UUID following;
	private static int sentChanges = -1;
	private static int sentGeneration = -1;
	private static boolean sentSomething;
	private static long lastSend, lastDriftCheck;

	private MusicRoom() {
	}

	/** A "music" message from the relay (relay thread). */
	public static void receive(UUID player, JsonObject message) {
		Listening l = message.has("svc") && message.has("id")
			? new Listening(player, message.get("svc").getAsString(), message.get("id").getAsString(),
			message.has("pos") ? message.get("pos").getAsLong() : 0, message.has("paused") && message.get("paused").getAsBoolean())
			: null;
		if (l != null && SystemMedia.SERVICE.equals(l.svc)) {
			l.title = str(message, "title");
			l.artist = str(message, "artist");
			l.app = str(message, "app");
		}
		if (l == null) OTHERS.remove(player);
		else OTHERS.put(player, l);
		if (player.equals(following)) apply(l);
	}

	/** Everybody in the room who listens to something now. */
	public static List<Listening> listening() {
		return new ArrayList<>(OTHERS.values());
	}

	public static Listening of(UUID player) {
		return OTHERS.get(player);
	}

	public static UUID following() {
		return following;
	}

	public static void follow(UUID player) {
		following = player;
		lastDriftCheck = System.currentTimeMillis();
		apply(OTHERS.get(player));
	}

	public static void unfollow() {
		following = null;
	}

	/** A new connection: the room tells again who listens to what. */
	public static void clearOthers() {
		OTHERS.clear();
	}

	/** Left the server: the others are gone. */
	public static void clear() {
		OTHERS.clear();
		following = null;
		sentGeneration = -1;
	}

	/** Every game tick: tells the room about own changes (when sharing) and keeps a followed track in step. */
	public static void tick(boolean share) {
		long now = System.currentTimeMillis();
		int changes = MusicPlayer.changes();
		int generation = EmoteSync.generation();
		boolean dirty = changes != sentChanges || generation != sentGeneration;
		if (share && dirty && !MusicPlayer.isLoading() && now - lastSend >= SEND_GAP_MS) {
			MusicTrack t = MusicPlayer.current();
			JsonObject m = new JsonObject();
			m.addProperty("t", "music");
			if (t != null) {
				m.addProperty("svc", t.svc);
				m.addProperty("id", t.id);
				m.addProperty("pos", MusicPlayer.position());
				m.addProperty("paused", MusicPlayer.isPaused());
				if (SystemMedia.SERVICE.equals(t.svc)) {
					// nothing to look up for the others: they get the names
					m.addProperty("title", cut(t.title, 80));
					m.addProperty("artist", cut(t.artist, 60));
					m.addProperty("app", cut(t.label, 30));
				}
			}
			EmoteSync.sendRaw(m.toString());
			sentChanges = changes;
			sentGeneration = generation;
			sentSomething = t != null;
			lastSend = now;
		} else if (!share && sentSomething) {
			// sharing turned off: the others stop seeing the track
			EmoteSync.sendRaw("{\"t\":\"music\"}");
			sentSomething = false;
			sentChanges = -1;
		}

		UUID leader = following;
		if (leader != null && now - lastDriftCheck >= DRIFT_CHECK_MS) {
			lastDriftCheck = now;
			Listening l = OTHERS.get(leader);
			MusicTrack t = MusicPlayer.current();
			if (l != null && t != null && t.same(l.svc, l.id) && !MusicPlayer.isLoading()
				&& Math.abs(MusicPlayer.position() - l.position()) > MAX_DRIFT_MS) {
				MusicPlayer.seek(l.position(), true);
			}
		}
	}

	/** Puts the own player where the followed player is. */
	private static void apply(final Listening l) {
		// the system player of somebody else cannot be played here
		if (l != null && SystemMedia.SERVICE.equals(l.svc)) return;
		if (l == null) {
			if (MusicPlayer.current() != null) MusicPlayer.stop();
			return;
		}
		MusicTrack current = MusicPlayer.current();
		if (current != null && current.same(l.svc, l.id)) {
			MusicPlayer.setPaused(l.paused);
			if (!MusicPlayer.isLoading() && Math.abs(MusicPlayer.position() - l.position()) > MAX_DRIFT_MS) MusicPlayer.seek(l.position(), true);
			return;
		}
		MusicCatalog.track(l.svc, l.id).thenAccept(track -> {
			Listening latest = OTHERS.get(l.player);
			if (l.player.equals(following) && latest != null && track.same(latest.svc, latest.id)) {
				MusicPlayer.play(track, latest.position(), latest.paused, true);
			}
		});
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
	}

	private static String cut(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max);
	}
}
