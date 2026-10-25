package com.zerovisual.pwf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;

/**
 * One Play With Friends connection.
 * <p>
 * Host: the world is open to LAN; the session makes an invite code, takes the friend's answer code and, once the
 * direct link is up, forwards it to the local LAN port of the world.
 * <p>
 * Friend: the session reads the invite, makes the answer code and, once connected, opens a local port that the
 * game joins like any server.
 * <p>
 * No Minecraft classes here: the same code runs in every game version.
 */
public final class PwfSession implements PeerLink.Listener {
	public enum State {
		PREPARING, WAITING, CONNECTED, FAILED, CLOSED
	}

	private final boolean host;
	private final int lanPort;
	private volatile State state = State.PREPARING;
	private volatile String message = "";
	private volatile String code;
	private volatile PeerLink link;
	private volatile IntConsumer onLocalPort;
	private long session;
	private volatile InviteCode.NetInfo localNet, remoteNet;
	private byte[] secret;
	private final long createdAt = System.currentTimeMillis();

	private PwfSession(boolean host, int lanPort) {
		this.host = host;
		this.lanPort = lanPort;
	}

	/** Host side: the world is already published on {@code lanPort}. Gathering the addresses runs in the background. */
	public static PwfSession host(int lanPort) {
		PwfSession s = new PwfSession(true, lanPort);
		SecureRandom random = new SecureRandom();
		s.session = random.nextLong();
		s.secret = new byte[16];
		random.nextBytes(s.secret);
		CompletableFuture.runAsync(() -> {
			try {
				PeerLink link = PeerLink.open();
				s.link = link;
				s.code = new InviteCode(false, s.session, s.secret, link.candidates(), link.netInfo()).encode();
				s.localNet = link.netInfo();
				s.message = link.hasPublicAddress() ? "" : "no_public_address";
				s.state = State.WAITING;
				link.start(s.session, s.secret, List.of(), s);
			} catch (IOException e) {
				s.fail("network");
			}
		});
		return s;
	}

	/**
	 * Friend side: parses the invite; null when the code is not valid.
	 * {@code onLocalPort} receives the local port to join once the link is up (on a background thread).
	 */
	public static PwfSession join(String inviteText, IntConsumer onLocalPort) {
		InviteCode invite = InviteCode.decode(inviteText, false);
		if (invite == null) return null;
		PwfSession s = new PwfSession(false, 0);
		s.session = invite.session();
		s.secret = invite.secret();
		s.onLocalPort = onLocalPort;
		CompletableFuture.runAsync(() -> {
			try {
				PeerLink link = PeerLink.open();
				s.link = link;
				s.code = new InviteCode(true, s.session, null, link.candidates(), link.netInfo()).encode();
				s.localNet = link.netInfo();
				s.remoteNet = invite.net();
				predict(link, invite);
				s.message = link.hasPublicAddress() ? "" : "no_public_address";
				s.state = State.WAITING;
				// probing starts right away: it also opens our router for the host's probes
				link.start(s.session, s.secret, invite.addresses(), s);
			} catch (IOException e) {
				s.fail("network");
			}
		});
		return s;
	}

	/** Host side: the answer code from the friend. Returns false when it is not an answer to this invite. */
	public boolean acceptAnswer(String answerText) {
		InviteCode answer = InviteCode.decode(answerText, true);
		PeerLink l = link;
		if (answer == null || answer.session() != session || l == null) return false;
		remoteNet = answer.net();
		predict(l, answer);
		l.addRemote(answer.addresses());
		return true;
	}

	private static void predict(PeerLink link, InviteCode code) {
		InviteCode.NetInfo net = code.net();
		if (net.symmetric() && net.publicIndex() >= 0) link.predictPorts(code.addresses().get(net.publicIndex()), net.portDelta());
	}

	/** Our network, null while preparing. */
	public InviteCode.NetInfo localNet() {
		return localNet;
	}

	/** The network of the other player, null until their code arrived. */
	public InviteCode.NetInfo remoteNet() {
		return remoteNet;
	}

	public boolean isHost() {
		return host;
	}

	public State state() {
		return state;
	}

	/** Extra detail for the screen: the failure reason, or a hint key. */
	public String message() {
		return message;
	}

	/** The code to send to the other player (invite or answer), null while preparing. */
	public String code() {
		return code;
	}

	public long ageMillis() {
		return System.currentTimeMillis() - createdAt;
	}

	public String remoteAddress() {
		PeerLink l = link;
		return l != null && l.remote() != null ? l.remote().getAddress().getHostAddress() : "";
	}

	public void close() {
		PeerLink l = link;
		if (l != null) l.close();
		if (state != State.FAILED) state = State.CLOSED;
	}

	private void fail(String reason) {
		message = reason;
		state = State.FAILED;
	}

	@Override
	public void onConnected(PeerLink link) {
		state = State.CONNECTED;
		Thread t = new Thread(() -> {
			try {
				if (host) {
					Socket local = new Socket(InetAddress.getLoopbackAddress(), lanPort);
					local.setTcpNoDelay(true);
					bridge(link, local);
				} else {
					try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
						server.setSoTimeout(30_000);
						IntConsumer callback = onLocalPort;
						if (callback != null) callback.accept(server.getLocalPort());
						Socket local = server.accept();
						local.setTcpNoDelay(true);
						bridge(link, local);
					}
				}
			} catch (IOException e) {
				link.close();
			}
		}, "ZeroVisual PWF bridge");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public void onClosed(PeerLink link, String reason) {
		if (state == State.CONNECTED || state == State.CLOSED) {
			message = reason;
			state = State.CLOSED;
		} else {
			fail(reason);
		}
	}

	/** Copies both ways until one side ends. */
	private static void bridge(PeerLink link, Socket local) throws IOException {
		InputStream fromLocal = local.getInputStream();
		OutputStream toLocal = local.getOutputStream();
		Thread up = new Thread(() -> {
			copy(fromLocal, link.output());
			link.close();
		}, "ZeroVisual PWF up");
		up.setDaemon(true);
		up.start();
		copy(link.input(), toLocal);
		try {
			local.close();
		} catch (IOException ignored) {
		}
		link.close();
	}

	private static void copy(InputStream in, OutputStream out) {
		byte[] buf = new byte[16384];
		try {
			int n;
			while ((n = in.read(buf)) >= 0) {
				out.write(buf, 0, n);
				out.flush();
			}
		} catch (IOException ignored) {
			// one side closed
		}
	}

	/** Local address the friend's game connects to. */
	public static InetSocketAddress localJoinAddress(int port) {
		return new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
	}
}
