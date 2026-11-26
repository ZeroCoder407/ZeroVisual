package com.zerovisual.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The "system player": what plays anywhere in Windows (Yandex Music, VK or YouTube Music in a browser, Spotify, ...),
 * read from the media overlay Windows shows next to the volume, with its buttons. A small PowerShell script
 * (assets/zerovisual/media/system_media.ps1) talks to Windows' own media API; nothing leaves the computer.
 * No Minecraft classes here.
 */
public final class SystemMedia {
	public static final String SERVICE = "system";
	private static final String SCRIPT = "/assets/zerovisual/media/system_media.ps1";
	private static final long RESTART_MS = 15_000;
	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

	private static volatile boolean enabled;
	private static volatile Process process;
	private static volatile Writer commands;
	private static volatile long nextStart;

	private static volatile MusicTrack track;
	private static volatile String status = "";
	private static volatile long position;
	private static volatile long positionAt;
	private static volatile boolean canSeek;
	private static volatile byte[] cover;
	private static volatile int coverVersion;

	private SystemMedia() {
	}

	/** Every game tick: starts or stops the script with the setting. */
	public static void tick(boolean on) {
		enabled = on && WINDOWS;
		if (!enabled) {
			stopScript();
			return;
		}
		if (process == null && System.currentTimeMillis() >= nextStart) startScript();
	}

	/** What plays in Windows now, or null. */
	public static MusicTrack track() {
		return enabled ? track : null;
	}

	public static boolean isPaused() {
		return !"playing".equals(status);
	}

	public static long position() {
		MusicTrack t = track;
		if (t == null) return 0;
		long p = "playing".equals(status) ? position + System.currentTimeMillis() - positionAt : position;
		return t.duration > 0 ? Math.min(p, t.duration * 1000L) : p;
	}

	public static boolean canSeek() {
		return canSeek;
	}

	/** The cover image (PNG or JPEG bytes) of the current track, or null; the version goes up with each new one. */
	public static byte[] cover() {
		return cover;
	}

	public static int coverVersion() {
		return coverVersion;
	}

	public static void setPaused(boolean paused) {
		send(paused ? "pause" : "play");
		status = paused ? "paused" : "playing";
		position = position();
		positionAt = System.currentTimeMillis();
	}

	public static void next() {
		send("next");
	}

	public static void previous() {
		send("previous");
	}

	public static void seek(long ms) {
		send("seek " + Math.max(0, ms));
		position = Math.max(0, ms);
		positionAt = System.currentTimeMillis();
	}

	// ---- the script ----

	private static void send(String command) {
		Writer w = commands;
		if (w == null) return;
		try {
			w.write(command + "\n");
			w.flush();
		} catch (IOException e) {
			stopScript();
		}
	}

	private static synchronized void startScript() {
		if (process != null) return;
		nextStart = System.currentTimeMillis() + RESTART_MS;
		try {
			File script = extractScript();
			ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
				"-WindowStyle", "Hidden", "-File", script.getAbsolutePath());
			builder.redirectErrorStream(false);
			final Process p = builder.start();
			process = p;
			commands = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8);
			Thread reader = new Thread(() -> read(p), "ZeroVisual system media");
			reader.setDaemon(true);
			reader.start();
			Thread errors = new Thread(() -> drain(p.getErrorStream()), "ZeroVisual system media errors");
			errors.setDaemon(true);
			errors.start();
		} catch (IOException | RuntimeException e) {
			process = null;
			commands = null;
		}
	}

	private static synchronized void stopScript() {
		Process p = process;
		process = null;
		commands = null;
		track = null;
		cover = null;
		if (p != null) {
			try {
				OutputStream in = p.getOutputStream();
				in.write("quit\n".getBytes(StandardCharsets.UTF_8));
				in.flush();
			} catch (IOException ignored) {
				// it is gone already
			}
			p.destroy();
		}
	}

	private static File extractScript() throws IOException {
		File dir = new File(System.getProperty("java.io.tmpdir"), "zerovisual");
		if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no temp folder");
		File file = new File(dir, "system_media.ps1");
		try (InputStream in = SystemMedia.class.getResourceAsStream(SCRIPT); OutputStream out = new FileOutputStream(file)) {
			if (in == null) throw new IOException("script missing");
			// a UTF-8 mark, so Windows PowerShell 5 reads the file as UTF-8
			out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
			byte[] buffer = new byte[8192];
			int n;
			while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
		}
		return file;
	}

	private static void read(Process p) {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = in.readLine()) != null) {
				try {
					receive(line);
				} catch (RuntimeException ignored) {
					// a line it could not describe
				}
			}
		} catch (IOException ignored) {
			// the script ended
		}
		synchronized (SystemMedia.class) {
			if (process == p) {
				process = null;
				commands = null;
				track = null;
			}
		}
	}

	private static void drain(InputStream errors) {
		byte[] buffer = new byte[1024];
		try {
			while (errors.read(buffer) >= 0) {
				// read and dropped, so a full pipe never stops the script
			}
		} catch (IOException ignored) {
			// the script ended
		}
	}

	@SuppressWarnings("deprecation")
	private static void receive(String line) {
		JsonElement parsed = new JsonParser().parse(line);
		if (!parsed.isJsonObject()) return;
		JsonObject m = parsed.getAsJsonObject();
		String type = m.get("t").getAsString();
		if ("none".equals(type)) {
			if (track != null) MusicPlayer.changed();
			track = null;
			cover = null;
			return;
		}
		if ("cover".equals(type)) {
			String data = m.has("data") ? m.get("data").getAsString() : "";
			try {
				cover = data.isEmpty() ? null : java.util.Base64.getDecoder().decode(data);
			} catch (IllegalArgumentException e) {
				cover = null;
			}
			coverVersion++;
			return;
		}
		if (!"media".equals(type)) return;
		String title = str(m, "title");
		String artist = str(m, "artist");
		// a page that names nothing (a bare audio file in a tab) gives its address as the title
		if (title.isEmpty() || title.matches("(?i)^(https?://)?[\\w.-]+\\.[a-z]{2,}/\\S*$")) {
			if (track != null) MusicPlayer.changed();
			track = null;
			return;
		}
		String app = appName(str(m, "app"));
		int seconds = (int) (m.get("dur").getAsLong() / 1000);
		String id = Integer.toHexString((app + "|" + artist + "|" + title).hashCode());
		MusicTrack old = track;
		String oldStatus = status;
		if (old == null || !old.id.equals(id) || old.duration != seconds) {
			track = new MusicTrack(SERVICE, id, app, title, artist, seconds, "", "");
		}
		status = str(m, "status");
		position = m.get("pos").getAsLong();
		positionAt = System.currentTimeMillis();
		canSeek = m.has("seek") && m.get("seek").getAsBoolean() && seconds > 0;
		if (old == null || !old.id.equals(id) || !status.equals(oldStatus)) MusicPlayer.changed();
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString().trim() : "";
	}

	/** "Yandex.Music_...!App", "chrome.exe" and the like, as people call them. */
	static String appName(String id) {
		String s = id.toLowerCase(Locale.ROOT);
		if (s.contains("yandex.music") || s.contains("yandexmusic")) return "Яндекс Музыка";
		if (s.contains("spotify")) return "Spotify";
		if (s.contains("applemusic") || s.contains("itunes")) return "Apple Music";
		if (s.contains("vk")) return "VK";
		if (s.contains("yandex")) return "Яндекс Браузер";
		if (s.contains("chrome")) return "Chrome";
		if (s.contains("msedge")) return "Edge";
		if (s.contains("firefox")) return "Firefox";
		if (s.contains("opera")) return "Opera";
		if (s.contains("zune") || s.contains("media")) return "Windows";
		int bang = id.indexOf('!');
		String name = bang > 0 ? id.substring(0, bang) : id;
		name = name.replaceAll("(?i)\\.exe$", "").replaceAll("_[a-z0-9]{8,}$", "");
		return name.isEmpty() ? "Windows" : name;
	}
}
