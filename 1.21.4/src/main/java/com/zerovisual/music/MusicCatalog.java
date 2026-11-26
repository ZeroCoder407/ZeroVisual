package com.zerovisual.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerovisual.emote.EmoteSync;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Searches Audius and Jamendo through the relay (it keeps the service keys and caches the answers). Every call runs
 * in the background; the future fails with a {@link CatalogException} whose message is a reason key:
 * "network", "not_configured" or "upstream". No Minecraft classes here.
 */
public final class MusicCatalog {
	/** Every service at once, mixed by the relay. */
	public static final String ALL = "all";

	private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "ZeroVisual music catalog");
		t.setDaemon(true);
		return t;
	});
	private static final Map<String, MusicTrack> TRACKS = new ConcurrentHashMap<>();
	private static volatile String region = "US";

	private MusicCatalog() {
	}

	public static final class CatalogException extends RuntimeException {
		public CatalogException(String reason) {
			super(reason);
		}
	}

	/** An empty query gives what is popular now; {@code svc} is a service or {@link #ALL}. */
	public static CompletableFuture<List<MusicTrack>> search(String svc, String query) {
		String q = query == null ? "" : query.trim();
		return CompletableFuture.supplyAsync(() -> {
			List<MusicTrack> tracks = request("/music/search?svc=" + svc + "&region=" + region + "&q=" + encode(q));
			for (MusicTrack t : tracks) TRACKS.put(t.svc + ":" + t.id, t);
			return tracks;
		}, POOL);
	}

	/** The country (two letters) for YouTube charts and search. */
	public static void setRegion(String country) {
		if (country != null && country.matches("[A-Za-z]{2}")) region = country.toUpperCase(java.util.Locale.ROOT);
	}

	/** One track by id, for a friend's "listen together"; remembered once known. */
	public static CompletableFuture<MusicTrack> track(String svc, String id) {
		MusicTrack known = TRACKS.get(svc + ":" + id);
		if (known != null) return CompletableFuture.completedFuture(known);
		return CompletableFuture.supplyAsync(() -> {
			List<MusicTrack> tracks = request("/music/track?svc=" + svc + "&id=" + encode(id));
			if (tracks.isEmpty()) throw new CatalogException("upstream");
			TRACKS.put(svc + ":" + id, tracks.get(0));
			return tracks.get(0);
		}, POOL);
	}

	/** A track already seen in a search or fetched, or null. */
	public static MusicTrack known(String svc, String id) {
		return TRACKS.get(svc + ":" + id);
	}

	private static List<MusicTrack> request(String path) {
		String base = httpBase();
		if (base == null) throw new CatalogException("network");
		JsonObject body;
		int status;
		try {
			HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
			c.setConnectTimeout(10_000);
			c.setReadTimeout(15_000);
			c.setRequestProperty("User-Agent", "ZeroVisual");
			c.setRequestProperty("Accept", "application/json");
			status = c.getResponseCode();
			InputStream in = status < 400 ? c.getInputStream() : c.getErrorStream();
			if (in == null) throw new CatalogException(status == 502 ? "upstream" : "network");
			JsonElement parsed = parse(readAll(in));
			body = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
		} catch (IOException | RuntimeException e) {
			if (e instanceof CatalogException) throw (CatalogException) e;
			throw new CatalogException("network");
		}
		if (body.has("error")) {
			throw new CatalogException("not_configured".equals(body.get("error").getAsString()) ? "not_configured" : "upstream");
		}
		if (!body.has("tracks")) return Collections.emptyList();
		List<MusicTrack> tracks = new ArrayList<>();
		for (JsonElement e : body.getAsJsonArray("tracks")) {
			if (e.isJsonObject()) tracks.add(MusicTrack.fromJson(e.getAsJsonObject()));
		}
		return tracks;
	}

	@SuppressWarnings("deprecation")
	private static JsonElement parse(String text) {
		// the old instance form: the Gson of Minecraft 1.8.9 has no static parseString
		return new JsonParser().parse(text);
	}

	/** The relay over HTTP(S), from the WebSocket address the emotes use; null when the relay is turned off. */
	static String httpBase() {
		String ws = EmoteSync.RELAY_URL;
		if (ws.isEmpty()) return null;
		if (ws.startsWith("wss://")) return "https://" + ws.substring(6);
		if (ws.startsWith("ws://")) return "http://" + ws.substring(5);
		return ws;
	}

	private static String readAll(InputStream in) throws IOException {
		try (InputStream input = in) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int n;
			while ((n = input.read(buffer)) > 0) {
				out.write(buffer, 0, n);
				if (out.size() > 1 << 20) throw new IOException("too big");
			}
			return new String(out.toByteArray(), "UTF-8");
		}
	}

	private static String encode(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}
}
