package com.zerovisual.music;

import fr.delthas.javamp3.Sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Plays one track at a time: streams the MP3 straight from the service, decodes it (javamp3) and writes it to the
 * sound card through Java Sound, apart from the game's own sound engine. Volume is applied to the samples, so the
 * game's master volume and the mute button act at once. YouTube tracks are handed to {@link YouTubeLink} instead:
 * they play in the official YouTube player in a browser tab. With nothing of its own playing, everything here speaks
 * of the {@link SystemMedia system player} instead (what plays in Windows). No Minecraft classes here.
 */
public final class MusicPlayer {
	/** Told on the player thread; hop to the game thread before touching the game. */
	public interface Listener {
		void onFinished(MusicTrack track);

		void onFailed(MusicTrack track, String reason);
	}

	private static final int RETRIES = 3;
	private static final long END_SLACK_MS = 4000;

	private static volatile Session session;
	/** The current track is a YouTube one, played by the browser tab. */
	private static volatile boolean onYouTube;
	private static volatile Listener listener;
	private static volatile float volume = 0.5f;
	private static volatile float master = 1f;
	private static volatile boolean muted;
	/** Goes up on every play, pause, resume, seek and stop, so the sharing code knows when to tell the others. */
	private static volatile int changes;

	private MusicPlayer() {
	}

	public static void setListener(Listener l) {
		listener = l;
	}

	/** Starts {@code track} at {@code fromMs}, replacing whatever played. */
	public static void play(MusicTrack track, long fromMs, boolean paused) {
		play(track, fromMs, paused, false);
	}

	/**
	 * {@code live}: the time is where somebody else is now (listening together), so the time spent loading is skipped
	 * as well and the music comes in where they are, not behind them.
	 */
	public static synchronized void play(MusicTrack track, long fromMs, boolean paused, boolean live) {
		// the music of another app would play over this one
		if (SystemMedia.track() != null && !SystemMedia.isPaused()) SystemMedia.setPaused(true);
		Session old = session;
		if (old != null) old.close();
		if (YouTubeLink.SERVICE.equals(track.svc)) {
			session = null;
			onYouTube = true;
			YouTubeLink.load(track, Math.max(0, fromMs), paused, live);
			changes++;
			return;
		}
		if (onYouTube) YouTubeLink.stop();
		onYouTube = false;
		Session s = new Session(track, Math.max(0, fromMs), paused, live);
		session = s;
		changes++;
		s.start();
	}

	public static synchronized void stop() {
		Session old = session;
		session = null;
		if (old != null) old.close();
		if (onYouTube) YouTubeLink.stop();
		onYouTube = false;
		changes++;
	}

	public static void setPaused(boolean paused) {
		if (isSystem()) {
			if (SystemMedia.isPaused() != paused) {
				SystemMedia.setPaused(paused);
				changes++;
			}
			return;
		}
		if (onYouTube) {
			if (YouTubeLink.isPaused() != paused) {
				YouTubeLink.setPaused(paused);
				changes++;
			}
			return;
		}
		Session s = session;
		if (s != null && s.paused != paused) {
			s.paused = paused;
			changes++;
		}
	}

	public static void togglePause() {
		if (isSystem()) {
			setPaused(!SystemMedia.isPaused());
			return;
		}
		if (onYouTube) {
			setPaused(!YouTubeLink.isPaused());
			return;
		}
		Session s = session;
		if (s != null) setPaused(!s.paused);
	}

	/** Jumps to a time: the stream starts again from there (decoding up to it takes a moment). */
	public static void seek(long ms) {
		seek(ms, false);
	}

	/** {@code live} as in {@link #play(MusicTrack, long, boolean, boolean)}. */
	public static synchronized void seek(long ms, boolean live) {
		if (isSystem()) {
			if (SystemMedia.canSeek()) {
				SystemMedia.seek(ms);
				changes++;
			}
			return;
		}
		if (onYouTube) {
			MusicTrack t = YouTubeLink.track();
			if (t == null) return;
			YouTubeLink.seek(Math.max(0, Math.min(ms, t.duration * 1000L - 1000)), live);
			changes++;
			return;
		}
		Session s = session;
		if (s == null) return;
		long target = Math.max(0, Math.min(ms, s.track.duration * 1000L - 1000));
		play(s.track, target, s.paused, live);
	}

	/** Nothing of ZeroVisual's own plays, but something in Windows does. */
	public static boolean isSystem() {
		return session == null && !onYouTube && SystemMedia.track() != null;
	}

	/** Whether a jump in the track is possible (the system player's app may not allow it). */
	public static boolean canSeek() {
		return !isSystem() || SystemMedia.canSeek();
	}

	public static MusicTrack current() {
		if (isSystem()) return SystemMedia.track();
		if (onYouTube) return YouTubeLink.track();
		Session s = session;
		return s == null ? null : s.track;
	}

	public static boolean isPaused() {
		if (isSystem()) return SystemMedia.isPaused();
		if (onYouTube) return YouTubeLink.isPaused();
		Session s = session;
		return s != null && s.paused;
	}

	/** Still connecting or skipping to the start time. */
	public static boolean isLoading() {
		if (onYouTube) return YouTubeLink.isLoading();
		Session s = session;
		return s != null && s.loading;
	}

	/** Milliseconds into the current track. */
	public static long position() {
		if (isSystem()) return SystemMedia.position();
		if (onYouTube) return YouTubeLink.position();
		Session s = session;
		return s == null ? 0 : s.position();
	}

	public static int changes() {
		return changes;
	}

	/** 0..1, the player's own volume; heard as its square, which sounds even across the slider. */
	public static void setVolume(float v) {
		volume = Math.max(0, Math.min(1, v));
	}

	/** 0..1, the game's master volume. */
	public static void setMaster(float v) {
		master = Math.max(0, Math.min(1, v));
	}

	public static void setMuted(boolean m) {
		muted = m;
	}

	public static boolean isMuted() {
		return muted;
	}

	static float gain() {
		return muted ? 0 : volume * volume * master;
	}

	/** The YouTube tab paused or resumed by itself. */
	static void changed() {
		changes++;
	}

	/** The YouTube tab finished the track. */
	static void finished(MusicTrack track) {
		Listener l = listener;
		if (l != null) l.onFinished(track);
	}

	/** The YouTube tab cannot play the track. */
	static void failed(MusicTrack track, String reason) {
		Listener l = listener;
		if (l != null) l.onFailed(track, reason);
	}

	private static final class Session extends Thread {
		final MusicTrack track;
		volatile boolean paused;
		volatile boolean loading = true;
		private volatile boolean closed;
		/** Where the current stream started, and the frames the sound card has played since. */
		private volatile long baseMs;
		private volatile SourceDataLine line;
		private volatile HttpURLConnection connection;
		private volatile long lineStartFrames;
		private volatile float frameRate = 44100;

		private final boolean live;
		private volatile long requestedAt = System.currentTimeMillis();

		Session(MusicTrack track, long fromMs, boolean paused, boolean live) {
			super("ZeroVisual music");
			this.live = live;
			setDaemon(true);
			this.track = track;
			this.baseMs = fromMs;
			this.paused = paused;
		}

		long position() {
			SourceDataLine l = line;
			if (l == null || loading) return baseMs;
			long played = l.getLongFramePosition() - lineStartFrames;
			return baseMs + (long) (played * 1000 / frameRate);
		}

		void close() {
			closed = true;
			interrupt();
			// a blocked read ends at once instead of after the timeout
			HttpURLConnection c = connection;
			if (c != null) c.disconnect();
		}

		@Override
		public void run() {
			int failures = 0;
			String reason = "network";
			while (!closed) {
				Outcome outcome;
				try {
					outcome = stream();
				} catch (javax.sound.sampled.LineUnavailableException | IllegalArgumentException e) {
					reason = "audio";
					outcome = Outcome.FAILED;
				}
				if (closed) break;
				if (outcome == Outcome.FINISHED) {
					finishLine(true);
					if (!closed && session == this) {
						Listener l = listener;
						if (l != null) l.onFinished(track);
					}
					return;
				}
				// a dropped connection (often after a long pause): go on from where the music stopped
				long at = position();
				finishLine(false);
				if (at >= track.duration * 1000L - END_SLACK_MS && reason.equals("network")) {
					Listener l = listener;
					if (l != null && session == this) l.onFinished(track);
					return;
				}
				if (reason.equals("audio") || ++failures > RETRIES) {
					Listener l = listener;
					if (l != null && session == this) l.onFailed(track, reason);
					return;
				}
				baseMs = at;
				requestedAt = System.currentTimeMillis();
				loading = true;
				try {
					Thread.sleep(500L * failures);
				} catch (InterruptedException e) {
					break;
				}
			}
			finishLine(false);
		}

		private enum Outcome { FINISHED, FAILED }

		private Outcome stream() throws javax.sound.sampled.LineUnavailableException {
			HttpURLConnection c = null;
			try {
				// a later start asks the server for the bytes from there on (see Mp3Seek); without that, from the start
				long offset = baseMs > 0 ? Mp3Seek.offset(track.stream, baseMs, track.duration * 1000L) : -1;
				c = open(offset);
				if (c == null) return Outcome.FAILED;
				InputStream raw = new BufferedInputStream(c.getInputStream(), 1 << 16);
				boolean ranged = offset > 0 && c.getResponseCode() == 206;
				if (ranged) {
					InputStream aligned = Mp3Seek.align(raw);
					if (aligned == null) {
						// no frame found where expected: start over and decode up to the time instead
						raw.close();
						c.disconnect();
						Mp3Seek.forget(track.stream);
						c = open(-1);
						if (c == null) return Outcome.FAILED;
						raw = Mp3Seek.learn(track.stream, new BufferedInputStream(c.getInputStream(), 1 << 16), c.getContentLengthLong());
						ranged = false;
					} else {
						raw = aligned;
					}
				} else if (c.getResponseCode() == 200) {
					raw = Mp3Seek.learn(track.stream, raw, c.getContentLengthLong());
				}
				try (InputStream input = raw; Sound sound = new Sound(input)) {
					AudioFormat format = sound.getAudioFormat();
					int frameSize = format.getFrameSize();
					frameRate = format.getFrameRate();
					byte[] buffer = new byte[4096 - 4096 % frameSize];

					// decode up to the start time and throw it away (much faster than real time); after a jump only
					// the first frames, whose bit reservoir was cut off
					long skip = ranged ? (long) Mp3Seek.DROP_SAMPLES * frameSize : (long) (baseMs * frameRate / 1000) * frameSize;
					for (int round = 0; round < 2; round++) {
						while (skip > 0 && !closed) {
							int n = sound.read(buffer, 0, (int) Math.min(buffer.length, skip));
							if (n <= 0) return Outcome.FINISHED;
							skip -= n;
						}
						// listening together: the others went on while this loaded, so skip that too (one more round
						// covers the moment the skipping itself took)
						if (!live || paused) break;
						long late = System.currentTimeMillis() - requestedAt;
						requestedAt = System.currentTimeMillis();
						baseMs += late;
						skip = (long) (late * frameRate / 1000) * frameSize;
					}

					SourceDataLine l = AudioSystem.getSourceDataLine(format);
					l.open(format, (int) (frameRate / 4) * frameSize);
					lineStartFrames = l.getLongFramePosition();
					line = l;
					loading = false;
					if (!paused) l.start();

					while (!closed) {
						if (paused) {
							l.stop();
							while (paused && !closed) Thread.sleep(40);
							if (closed) break;
							l.start();
						}
						int n = sound.read(buffer, 0, buffer.length);
						if (n <= 0) return Outcome.FINISHED;
						n -= n % frameSize;
						applyGain(buffer, n, gain());
						int off = 0;
						while (off < n && !closed) off += l.write(buffer, off, n - off);
					}
					return Outcome.FAILED;
				}
			} catch (IOException | IndexOutOfBoundsException e) {
				// javamp3 throws IndexOutOfBounds when the stream ends in the middle of a frame
				return position() >= track.duration * 1000L - END_SLACK_MS ? Outcome.FINISHED : Outcome.FAILED;
			} catch (InterruptedException e) {
				return Outcome.FAILED;
			} finally {
				if (c != null) c.disconnect();
			}
		}

		/** The stream from {@code offset} (a byte), or from the start when negative; null when the server refuses. */
		private HttpURLConnection open(long offset) throws IOException {
			for (int attempt = 0; attempt < 2; attempt++) {
				HttpURLConnection c = (HttpURLConnection) Mp3Seek.address(track.stream).openConnection();
				c.setConnectTimeout(10_000);
				c.setReadTimeout(20_000);
				c.setRequestProperty("User-Agent", "ZeroVisual");
				c.setInstanceFollowRedirects(true);
				if (offset > 0) c.setRequestProperty("Range", "bytes=" + offset + "-");
				connection = c;
				if (closed) {
					c.disconnect();
					return null;
				}
				int status = c.getResponseCode();
				if (status < 400) {
					Mp3Seek.remember(track.stream, c);
					return c;
				}
				c.disconnect();
				// the remembered address may have run out: once more through the service
				Mp3Seek.forget(track.stream);
			}
			return null;
		}

		private void finishLine(boolean drain) {
			SourceDataLine l = line;
			line = null;
			if (l == null) return;
			try {
				if (drain && !closed) l.drain();
				else l.flush();
				l.stop();
			} finally {
				l.close();
			}
		}

		/** 16-bit little-endian samples scaled in place. */
		private static void applyGain(byte[] b, int length, float gain) {
			if (gain >= 0.999f) return;
			for (int i = 0; i + 1 < length; i += 2) {
				int sample = (short) ((b[i] & 0xFF) | (b[i + 1] << 8));
				sample = (int) (sample * gain);
				b[i] = (byte) sample;
				b[i + 1] = (byte) (sample >> 8);
			}
		}
	}
}
