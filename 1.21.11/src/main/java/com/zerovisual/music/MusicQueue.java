package com.zerovisual.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The list a track was picked from: next and previous go through it, the end of a track plays the next one. */
public final class MusicQueue {
	private static List<MusicTrack> tracks = Collections.emptyList();
	private static int index = -1;

	private MusicQueue() {
	}

	/** Plays {@code list.get(i)} and remembers the list for next and previous. */
	public static synchronized void play(List<MusicTrack> list, int i) {
		if (i < 0 || i >= list.size()) return;
		tracks = new ArrayList<>(list);
		index = i;
		MusicPlayer.play(tracks.get(i), 0, false);
	}

	public static synchronized boolean next() {
		if (MusicPlayer.isSystem()) {
			SystemMedia.next();
			return true;
		}
		if (index < 0 || index + 1 >= tracks.size()) return false;
		index++;
		MusicPlayer.play(tracks.get(index), 0, false);
		return true;
	}

	/** Back to the start of the track after a few seconds of it, else the track before. */
	public static synchronized void previous() {
		if (MusicPlayer.isSystem()) {
			SystemMedia.previous();
			return;
		}
		if (MusicPlayer.current() != null && MusicPlayer.position() > 4000) {
			MusicPlayer.seek(0);
			return;
		}
		if (index > 0) {
			index--;
			MusicPlayer.play(tracks.get(index), 0, false);
		} else if (MusicPlayer.current() != null) {
			MusicPlayer.seek(0);
		}
	}

	public static synchronized boolean hasNext() {
		return index >= 0 && index + 1 < tracks.size();
	}

	public static synchronized void clear() {
		tracks = Collections.emptyList();
		index = -1;
	}
}
