package com.zerovisual.music;

import com.google.gson.JsonObject;

/** One track of Audius or Jamendo as the relay describes it (see relay/src/music.js). No Minecraft classes here. */
public final class MusicTrack {
	public final String svc;
	public final String id;
	public final String title;
	public final String artist;
	/** Seconds. */
	public final int duration;
	/** Where the audio (MP3) comes from, straight from the service. */
	public final String stream;
	/** The page of the track on the service, for the attribution. */
	public final String page;
	/** The service as people know it ("Audius", "Internet Archive", "Openverse · Wikimedia"). */
	public final String label;

	public MusicTrack(String svc, String id, String label, String title, String artist, int duration, String stream, String page) {
		this.svc = svc;
		this.label = label.isEmpty() ? svc : label;
		this.id = id;
		this.title = title;
		this.artist = artist;
		this.duration = duration;
		this.stream = stream;
		this.page = page;
	}

	static MusicTrack fromJson(JsonObject o) {
		return new MusicTrack(str(o, "svc"), str(o, "id"), str(o, "label"), str(o, "title"), str(o, "artist"),
			o.has("duration") ? o.get("duration").getAsInt() : 0, str(o, "stream"), str(o, "page"));
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
	}

	public boolean same(String svc, String id) {
		return this.svc.equals(svc) && this.id.equals(id);
	}

	/** "m:ss" of a time in milliseconds. */
	public static String time(long ms) {
		long s = Math.max(0, ms / 1000);
		return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
	}
}
