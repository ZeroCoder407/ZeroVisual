package com.zerovisual.pwf;

import java.util.ArrayList;
import java.util.List;

/** The running Play With Friends sessions, so they survive closing the screen. */
public final class PwfManager {
	private static final List<PwfSession> hosted = new ArrayList<>();
	private static PwfSession joining;

	private PwfManager() {
	}

	/** The invite shown on the host screen: the newest one, or a new one when there is none yet. */
	public static synchronized PwfSession currentHost(int lanPort) {
		if (hosted.isEmpty()) hosted.add(PwfSession.host(lanPort));
		return hosted.get(hosted.size() - 1);
	}

	/** Another friend: a new invite (each friend gets their own). */
	public static synchronized PwfSession newHost(int lanPort) {
		PwfSession s = PwfSession.host(lanPort);
		hosted.add(s);
		return s;
	}

	public static synchronized int connectedFriends() {
		int n = 0;
		for (PwfSession s : hosted) if (s.state() == PwfSession.State.CONNECTED) n++;
		return n;
	}

	public static synchronized void setJoining(PwfSession session) {
		if (joining != null && joining != session) joining.close();
		joining = session;
	}

	public static synchronized PwfSession joining() {
		return joining;
	}

	/** Leaving the world: the friends are disconnected. */
	public static synchronized void closeHosted() {
		for (PwfSession s : hosted) s.close();
		hosted.clear();
	}
}
