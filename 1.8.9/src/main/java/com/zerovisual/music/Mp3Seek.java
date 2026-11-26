package com.zerovisual.music;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jumps into the middle of an MP3 over HTTP: the byte where a time starts (linear in the audio after the ID3 tag,
 * close enough for the constant or near-constant bit rates of Audius and Jamendo), and a stream cut there that
 * javamp3 can decode. No Minecraft classes here.
 */
final class Mp3Seek {
	/** Decoded samples to throw away after a jump: the first frames miss their bit reservoir and sound broken. */
	static final int DROP_SAMPLES = 2 * 1152;
	/** Two frames at most bit rate fit, with room for junk in front. */
	private static final int SEARCH_BYTES = 6 * 1024;
	/** Services sign the address they redirect to for a while; a few minutes of it saves the redirects on a jump. */
	private static final long RESOLVED_MS = 5 * 60_000;

	/** Per stream address: where the audio starts (after the ID3 tag) and the whole file size. */
	private static final Map<String, long[]> LAYOUTS = new ConcurrentHashMap<>();
	private static final Map<String, Resolved> RESOLVED = new ConcurrentHashMap<>();

	private static final class Resolved {
		final URL url;
		final long until;

		Resolved(URL url) {
			this.url = url;
			this.until = System.currentTimeMillis() + RESOLVED_MS;
		}
	}

	private Mp3Seek() {
	}

	/** The byte to ask for to start at {@code ms}, or -1 when the file layout cannot be found out. */
	static long offset(String url, long ms, long durationMs) {
		if (durationMs <= 0) return -1;
		long[] layout = LAYOUTS.get(url);
		if (layout == null) layout = probe(url);
		if (layout == null) return -1;
		long audio = layout[1] - layout[0];
		return layout[0] + (long) (audio * Math.min(1.0, ms / (double) durationMs));
	}

	static void forget(String url) {
		LAYOUTS.remove(url);
		RESOLVED.remove(url);
	}

	/** Where the address last led after its redirects, while that is fresh; else the address itself. */
	static URL address(String url) throws IOException {
		Resolved r = RESOLVED.get(url);
		if (r != null && System.currentTimeMillis() < r.until) return r.url;
		return new URL(url);
	}

	/** Called with the connection after it answered. */
	static void remember(String url, HttpURLConnection c) {
		URL end = c.getURL();
		if (!end.toString().equals(url) && !RESOLVED.containsKey(url)) RESOLVED.put(url, new Resolved(end));
	}

	/**
	 * For a stream read from its start: notes the layout and skips the ID3 tag. javamp3 looks for the first frame
	 * byte by byte and gets lost in a big tag (a cover picture can look like frames), so it gets the audio only.
	 */
	static InputStream learn(String url, InputStream in, long length) throws IOException {
		if (!in.markSupported()) return in;
		in.mark(10);
		byte[] head = new byte[10];
		int n = readFully(in, head);
		long tag = n == 10 ? id3Size(head) : 0;
		if (length > 0) LAYOUTS.put(url, new long[]{tag, length});
		if (tag == 0) {
			in.reset();
			return in;
		}
		long rest = tag - 10;
		while (rest > 0) {
			long skipped = in.skip(rest);
			if (skipped <= 0) {
				if (in.read() < 0) break;
				skipped = 1;
			}
			rest -= skipped;
		}
		return in;
	}

	/** The first 10 bytes alone: the ID3 header and, in Content-Range, the file size. */
	private static long[] probe(String url) {
		try {
			HttpURLConnection c = (HttpURLConnection) address(url).openConnection();
			c.setConnectTimeout(10_000);
			c.setReadTimeout(10_000);
			c.setRequestProperty("User-Agent", "ZeroVisual");
			c.setRequestProperty("Range", "bytes=0-9");
			try {
				if (c.getResponseCode() != 206) return null;
				remember(url, c);
				String range = c.getHeaderField("Content-Range");
				int slash = range == null ? -1 : range.lastIndexOf('/');
				if (slash < 0) return null;
				long total = Long.parseLong(range.substring(slash + 1).trim());
				byte[] head = new byte[10];
				int n;
				try (InputStream in = c.getInputStream()) {
					n = readFully(in, head);
				}
				long[] layout = {n == 10 ? id3Size(head) : 0, total};
				LAYOUTS.put(url, layout);
				return layout;
			} finally {
				c.disconnect();
			}
		} catch (IOException | NumberFormatException e) {
			return null;
		}
	}

	/** Bytes of the ID3v2 tag in front of the audio, 0 without one. */
	private static long id3Size(byte[] h) {
		if (h[0] != 'I' || h[1] != 'D' || h[2] != '3') return 0;
		long size = ((h[6] & 0x7F) << 21) | ((h[7] & 0x7F) << 14) | ((h[8] & 0x7F) << 7) | (h[9] & 0x7F);
		boolean footer = (h[5] & 0x10) != 0;
		return 10 + size + (footer ? 10 : 0);
	}

	/**
	 * Starts the stream at the next MPEG-1 Layer III frame (one whose following frame is where it should be) and
	 * points its bit reservoir at itself, which javamp3 needs to begin there. Null when no frame turns up.
	 */
	static InputStream align(InputStream in) throws IOException {
		byte[] buf = new byte[SEARCH_BYTES];
		int filled = readFully(in, buf);
		for (int i = 0; i + 4 <= filled; i++) {
			int length = frameLength(buf, i);
			if (length <= 0) continue;
			int next = i + length;
			if (next + 4 <= filled && frameLength(buf, next) <= 0) continue;
			int side = i + 4 + ((buf[i + 1] & 1) == 0 ? 2 : 0);
			if (side + 2 > filled) break;
			// main_data_begin, the first 9 bits of the side information
			buf[side] = 0;
			buf[side + 1] &= 0x7F;
			return new SequenceInputStream(new ByteArrayInputStream(buf, i, filled - i), in);
		}
		return null;
	}

	private static final int[] BITRATES = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
	private static final int[] RATES = {44100, 48000, 32000, 0};

	/** Bytes of the MPEG-1 Layer III frame whose header is at {@code i}, or 0 when it is no such header. */
	private static int frameLength(byte[] b, int i) {
		if ((b[i] & 0xFF) != 0xFF || (b[i + 1] & 0xFE) != 0xFA) return 0;
		int bitrate = BITRATES[(b[i + 2] >> 4) & 0x0F];
		int rate = RATES[(b[i + 2] >> 2) & 0x03];
		if (bitrate == 0 || rate == 0) return 0;
		return 144 * bitrate * 1000 / rate + ((b[i + 2] >> 1) & 1);
	}

	private static int readFully(InputStream in, byte[] b) throws IOException {
		int n = 0;
		while (n < b.length) {
			int r = in.read(b, n, b.length - n);
			if (r < 0) break;
			n += r;
		}
		return n;
	}
}
