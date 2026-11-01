package com.zerovisual.pwf;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A direct connection between two players over UDP.
 * <p>
 * Both sides send probes to every address of the other side at the same time ("hole punching"): the outgoing
 * probes open the routers for the incoming ones. The first valid probe that arrives fixes the remote address.
 * On top of that a small reliable, ordered byte stream (sequence numbers, acknowledgements, retransmission and
 * a congestion window) carries the Minecraft connection, which then runs through a local TCP socket as usual.
 */
public final class PeerLink implements AutoCloseable {
	/** Reasons passed to {@link Listener#onClosed}: no_direct, network, peer_closed, lost, closed. */
	public interface Listener {
		void onConnected(PeerLink link);

		void onClosed(PeerLink link, String reason);
	}

	private static final byte TYPE_PROBE = 1, TYPE_PROBE_ACK = 2, TYPE_DATA = 3, TYPE_ACK = 4, TYPE_KEEPALIVE = 5, TYPE_CLOSE = 6;
	private static final int HEADER = 11;
	private static final int MAX_PAYLOAD = 1100;
	private static final long PUNCH_TIMEOUT_MS = 120_000;
	private static final long IDLE_TIMEOUT_MS = 25_000;
	private static final long KEEPALIVE_MS = 10_000;
	/** Interface names of VPN clients (TUN/TAP adapters, WireGuard, Xray/V2Ray front ends). */
	private static final java.util.regex.Pattern VPN_NAME = java.util.regex.Pattern.compile(
		"(?i).*(tun|tap|wintun|wireguard|wg\\d|vpn|happ|v2ray|xray|hiddify|outline|amnezia|nekoray|sing-box|clash|proton|nord).*");
	/** Test hook: share of outgoing packets to drop (-Dzerovisual.pwf.drop=0.05), 0 in normal use. */
	private static final double TEST_DROP = Double.parseDouble(System.getProperty("zerovisual.pwf.drop", "0"));

	private final DatagramSocket socket;
	private final List<InetSocketAddress> localAddresses;
	private final InetSocketAddress publicAddress;
	private final InetSocketAddress forwardedAddress;
	private final InviteCode.NetInfo netInfo;
	private final Upnp upnp;
	/** Remote address to predict ports from (its router is symmetric), and the port step. */
	private volatile InetSocketAddress predictBase;
	private volatile int predictDelta;

	private volatile long session;
	private volatile byte[] secret;
	private volatile Listener listener;
	private final List<InetSocketAddress> remoteCandidates = Collections.synchronizedList(new ArrayList<>());
	private volatile InetSocketAddress remote;
	private volatile boolean closed;
	private volatile long lastReceived = System.currentTimeMillis();
	private volatile long lastSent;
	private long punchStarted;
	/** For the log: probes sent, and packets of this session that arrived (with a wrong proof too). */
	private volatile int probesSent, packetsSeen;

	// ---- reliable stream: sender ----
	private static final class Segment {
		final int seq;
		final byte[] data;
		long sentAt;
		int retransmits;

		Segment(int seq, byte[] data) {
			this.seq = seq;
			this.data = data;
		}
	}

	private final Object sendLock = new Object();
	private final TreeMap<Integer, Segment> inflight = new TreeMap<>();
	private int nextSeq;
	private double cwnd = 32, ssthresh = 512;
	private double srtt = -1, rttvar;
	private long rto = 500;
	private long lastLossAt;
	private int dupAcks, lastCum = -1;

	// ---- reliable stream: receiver ----
	private final Object recvLock = new Object();
	private int expected;
	private final TreeMap<Integer, byte[]> outOfOrder = new TreeMap<>();
	private final LinkedBlockingQueue<byte[]> delivered = new LinkedBlockingQueue<>();
	private static final byte[] EOF = new byte[0];

	private PeerLink(DatagramSocket socket, List<InetSocketAddress> localAddresses, InetSocketAddress publicAddress,
					 InetSocketAddress forwardedAddress, InviteCode.NetInfo netInfo, Upnp upnp) {
		this.socket = socket;
		this.localAddresses = localAddresses;
		this.publicAddress = publicAddress;
		this.forwardedAddress = forwardedAddress;
		this.netInfo = netInfo;
		this.upnp = upnp;
	}

	/** Opens a UDP socket and finds the addresses where the other player may reach it (blocking, a few seconds at most). */
	public static PeerLink open() throws IOException {
		DatagramSocket socket = new DatagramSocket(new InetSocketAddress(0));
		int port = socket.getLocalPort();
		// the router search runs while STUN asks the servers
		java.util.concurrent.CompletableFuture<Upnp> router = java.util.concurrent.CompletableFuture.supplyAsync(Upnp::discover);
		Stun.Mapping mapping = Stun.mapping(socket);
		InetSocketAddress pub = mapping != null ? mapping.address() : null;
		Upnp upnp = router.join();
		InetSocketAddress forwarded = null;
		boolean cgnat = false;
		if (upnp != null) {
			String external = upnp.externalIp();
			// the router's own internet address is private or differs from what STUN sees: provider NAT
			cgnat = external == null || Upnp.isPrivate(external) || (pub != null && !pub.getAddress().getHostAddress().equals(external));
			if (!cgnat && upnp.forward(port)) forwarded = new InetSocketAddress(InetAddress.getByName(external), port);
		}
		String vpn = vpnName();
		List<InetSocketAddress> local = new ArrayList<>();
		try {
			for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || isVpn(ni)) continue;
				for (InetAddress a : Collections.list(ni.getInetAddresses())) {
					if (a.isLoopbackAddress() || a.isLinkLocalAddress()) continue;
					// IPv6 global addresses often work directly, without any NAT
					if (a instanceof Inet4Address || (a instanceof Inet6Address && !a.isSiteLocalAddress())) {
						local.add(new InetSocketAddress(a, port));
					}
				}
			}
		} catch (SocketException ignored) {
		}
		List<InetSocketAddress> order = new ArrayList<>();
		if (forwarded != null) order.add(forwarded);
		if (pub != null && !order.contains(pub)) order.add(pub);
		InviteCode.NetInfo info = new InviteCode.NetInfo(pub != null ? order.indexOf(pub) : -1,
			mapping != null && mapping.symmetric(), mapping != null ? mapping.portDelta() : 0, forwarded != null, cgnat, vpn != null);
		log("local port " + port + ", public " + pub + (mapping != null && mapping.symmetric() ? " (symmetric, step " + mapping.portDelta() + ")" : "")
			+ ", upnp " + (upnp != null) + (forwarded != null ? " forwarded " + forwarded : "") + ", cgnat " + cgnat
			+ (vpn != null ? ", VPN interface \"" + vpn + "\"" : "") + ", lan " + local);
		return new PeerLink(socket, local, pub, forwarded, info, upnp);
	}

	/** The interface the internet traffic leaves through, when it looks like a VPN; null otherwise. */
	private static String vpnName() {
		// connect() on UDP sends nothing, it only asks the system which source address the route uses
		try (DatagramSocket probe = new DatagramSocket()) {
			probe.connect(InetAddress.getByName("8.8.8.8"), 53);
			NetworkInterface ni = NetworkInterface.getByInetAddress(probe.getLocalAddress());
			return ni != null && isVpn(ni) ? ni.getDisplayName() : null;
		} catch (IOException e) {
			return null;
		}
	}

	private static boolean isVpn(NetworkInterface ni) {
		try {
			if (ni.isPointToPoint()) return true;
		} catch (SocketException ignored) {
		}
		return VPN_NAME.matcher(ni.getName()).matches() || (ni.getDisplayName() != null && VPN_NAME.matcher(ni.getDisplayName()).matches());
	}

	static void log(String text) {
		System.out.println("[ZeroVisual PWF] " + text);
	}

	/** What kind of network we are on (goes into the code, and on the screen). */
	public InviteCode.NetInfo netInfo() {
		return netInfo;
	}

	/** The other side has a symmetric router: also probe the ports it will probably use next. */
	public void predictPorts(InetSocketAddress base, int delta) {
		predictBase = base;
		predictDelta = delta == 0 || Math.abs(delta) > 64 ? 1 : delta;
	}

	/** Addresses to put in the code: the public one first, then the local network ones (friends on the same Wi-Fi). */
	public List<InetSocketAddress> candidates() {
		List<InetSocketAddress> list = new ArrayList<>();
		if (forwardedAddress != null) list.add(forwardedAddress);
		if (publicAddress != null && !list.contains(publicAddress)) list.add(publicAddress);
		for (InetSocketAddress a : localAddresses) if (!list.contains(a) && list.size() < 8) list.add(a);
		return list;
	}

	public boolean hasPublicAddress() {
		return publicAddress != null;
	}

	public InetSocketAddress remote() {
		return remote;
	}

	public boolean isConnected() {
		return remote != null && !closed;
	}

	/** Starts listening and probing. The remote candidates may be empty for now and added later ({@link #addRemote}). */
	public void start(long session, byte[] secret, List<InetSocketAddress> remoteCandidates, Listener listener) {
		this.session = session;
		this.secret = secret;
		this.listener = listener;
		this.remoteCandidates.addAll(remoteCandidates);
		punchStarted = System.currentTimeMillis();
		if (!remoteCandidates.isEmpty()) log("probing " + remoteCandidates + (predictBase != null ? " + 48 ports after " + predictBase : ""));
		Thread receiver = new Thread(this::receiveLoop, "ZeroVisual PWF receive");
		receiver.setDaemon(true);
		receiver.start();
		Thread timer = new Thread(this::timerLoop, "ZeroVisual PWF timer");
		timer.setDaemon(true);
		timer.start();
	}

	/** The answer code arrived: probe these addresses too. */
	public void addRemote(List<InetSocketAddress> candidates) {
		for (InetSocketAddress a : candidates) if (!remoteCandidates.contains(a)) remoteCandidates.add(a);
		punchStarted = System.currentTimeMillis();
		log("probing " + remoteCandidates + (predictBase != null ? " + 48 ports after " + predictBase : ""));
	}

	// ---- packets ----

	private ByteBuffer header(byte type, int extra) {
		ByteBuffer b = ByteBuffer.allocate(HEADER + extra);
		b.put((byte) 'Z').put((byte) 'V').put(type).putLong(session);
		return b;
	}

	private void send(ByteBuffer packet, InetSocketAddress to) {
		if (closed && packet.get(2) != TYPE_CLOSE) return;
		if (TEST_DROP > 0 && Math.random() < TEST_DROP) return;
		try {
			socket.send(new DatagramPacket(packet.array(), packet.position(), to));
			lastSent = System.currentTimeMillis();
		} catch (IOException ignored) {
			// unreachable candidate addresses fail here, that is fine
		}
	}

	private byte[] mac(byte type, long nonce) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			mac.update(ByteBuffer.allocate(17).putLong(session).put(type).putLong(nonce).array());
			byte[] full = mac.doFinal();
			byte[] shortMac = new byte[16];
			System.arraycopy(full, 0, shortMac, 0, 16);
			return shortMac;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	private void sendProbe(byte type, long nonce, InetSocketAddress to) {
		ByteBuffer b = header(type, 8 + 16);
		b.putLong(nonce).put(mac(type, nonce));
		send(b, to);
		probesSent++;
	}

	private void receiveLoop() {
		byte[] buf = new byte[2048];
		while (!closed) {
			DatagramPacket p = new DatagramPacket(buf, buf.length);
			try {
				socket.setSoTimeout(1000);
				socket.receive(p);
			} catch (java.net.SocketTimeoutException e) {
				continue;
			} catch (IOException e) {
				if (!closed) shutdown("network", false);
				return;
			}
			try {
				handle(ByteBuffer.wrap(p.getData(), 0, p.getLength()), (InetSocketAddress) p.getSocketAddress());
			} catch (RuntimeException ignored) {
				// malformed packet
			}
		}
	}

	private void handle(ByteBuffer b, InetSocketAddress from) {
		if (b.remaining() < HEADER || b.get() != 'Z' || b.get() != 'V') return;
		byte type = b.get();
		if (b.getLong() != session) return;
		if (type == TYPE_PROBE || type == TYPE_PROBE_ACK) {
			if (packetsSeen++ == 0) log("first packet from " + from);
			long nonce = b.getLong();
			byte[] got = new byte[16];
			b.get(got);
			if (!MessageDigest.isEqual(got, mac(type, nonce))) return;
			lastReceived = System.currentTimeMillis();
			if (type == TYPE_PROBE) sendProbe(TYPE_PROBE_ACK, nonce, from);
			if (remote == null) {
				remote = from;
				log("connected to " + from + " after " + (System.currentTimeMillis() - punchStarted) + " ms");
				Listener l = listener;
				if (l != null) l.onConnected(this);
			}
			return;
		}
		// everything else only from the confirmed peer
		if (remote == null || !remote.equals(from)) return;
		lastReceived = System.currentTimeMillis();
		switch (type) {
			case TYPE_DATA -> onData(b.getInt(), b);
			case TYPE_ACK -> onAck(b.getInt(), b.getLong());
			case TYPE_CLOSE -> shutdown("peer_closed", false);
			default -> {
			}
		}
	}

	private void timerLoop() {
		long lastProbe = 0, lastKeepalive = System.currentTimeMillis();
		while (!closed) {
			long now = System.currentTimeMillis();
			if (remote == null) {
				// the host waits for the answer code as long as needed; the clock runs once there are addresses to probe
				if (remoteCandidates.isEmpty()) {
					punchStarted = now;
					if (now - lastKeepalive >= KEEPALIVE_MS) {
						lastKeepalive = now;
						Stun.keepalive(socket);
					}
				}
				if (now - punchStarted > PUNCH_TIMEOUT_MS) {
					log("no direct connection: " + probesSent + " probes sent, " + packetsSeen + " packets of the friend arrived");
					shutdown("no_direct", true);
					return;
				}
				if (now - lastProbe >= 150) {
					lastProbe = now;
					long nonce = System.nanoTime();
					synchronized (remoteCandidates) {
						for (InetSocketAddress a : remoteCandidates) sendProbe(TYPE_PROBE, nonce, a);
					}
					InetSocketAddress base = predictBase;
					if (base != null) {
						for (int k = 1; k <= 48; k++) {
							int p = base.getPort() + k * predictDelta;
							if (p > 0 && p < 65536) sendProbe(TYPE_PROBE, nonce, new InetSocketAddress(base.getAddress(), p));
						}
					}
				}
			} else {
				if (now - lastReceived > IDLE_TIMEOUT_MS) {
					shutdown("lost", false);
					return;
				}
				if (now - lastSent > 2000) send(header(TYPE_KEEPALIVE, 0), remote);
				retransmit(now);
			}
			try {
				Thread.sleep(15);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	// ---- reliable stream ----

	private void sendSegment(Segment s) {
		ByteBuffer b = header(TYPE_DATA, 4 + s.data.length);
		b.putInt(s.seq).put(s.data);
		s.sentAt = System.currentTimeMillis();
		send(b, remote);
	}

	/** Blocks while the congestion window is full. */
	private void write(byte[] data, int off, int len) throws IOException {
		while (len > 0) {
			int n = Math.min(len, MAX_PAYLOAD);
			byte[] chunk = new byte[n];
			System.arraycopy(data, off, chunk, 0, n);
			Segment s;
			synchronized (sendLock) {
				while (!closed && inflight.size() >= (int) cwnd) {
					try {
						sendLock.wait(50);
					} catch (InterruptedException e) {
						throw new IOException("interrupted");
					}
				}
				if (closed) throw new IOException("connection closed");
				s = new Segment(nextSeq++, chunk);
				inflight.put(s.seq, s);
			}
			sendSegment(s);
			off += n;
			len -= n;
		}
	}

	private void onAck(int cum, long bitmap) {
		long now = System.currentTimeMillis();
		synchronized (sendLock) {
			int acked = 0;
			Iterator<Map.Entry<Integer, Segment>> it = inflight.headMap(cum).entrySet().iterator();
			while (it.hasNext()) {
				Segment s = it.next().getValue();
				if (s.retransmits == 0) sampleRtt(now - s.sentAt);
				it.remove();
				acked++;
			}
			for (int i = 0; i < 64; i++) {
				if ((bitmap & (1L << i)) != 0 && inflight.remove(cum + 1 + i) != null) acked++;
			}
			for (int i = 0; i < acked; i++) cwnd += cwnd < ssthresh ? 1 : 1 / cwnd;
			cwnd = Math.min(cwnd, 1024);
			// three acks for the same missing packet while later ones arrive: resend it right away
			if (cum == lastCum && bitmap != 0) {
				if (++dupAcks == 3) {
					Segment missing = inflight.get(cum);
					if (missing != null) {
						onLoss(now);
						missing.retransmits++;
						sendSegment(missing);
					}
				}
			} else {
				dupAcks = 0;
				lastCum = cum;
			}
			sendLock.notifyAll();
		}
	}

	private void sampleRtt(long rtt) {
		if (srtt < 0) {
			srtt = rtt;
			rttvar = rtt / 2.0;
		} else {
			rttvar = 0.75 * rttvar + 0.25 * Math.abs(srtt - rtt);
			srtt = 0.875 * srtt + 0.125 * rtt;
		}
		rto = Math.max(120, Math.min(3000, (long) (srtt + 4 * rttvar)));
	}

	private void onLoss(long now) {
		// at most one window reduction per round trip
		if (now - lastLossAt < Math.max(srtt, 50)) return;
		lastLossAt = now;
		ssthresh = Math.max(cwnd / 2, 16);
		cwnd = ssthresh;
	}

	private void retransmit(long now) {
		List<Segment> due = new ArrayList<>();
		synchronized (sendLock) {
			for (Segment s : inflight.values()) {
				if (now - s.sentAt >= rto * (1L << Math.min(s.retransmits, 3))) due.add(s);
			}
			if (!due.isEmpty()) onLoss(now);
			for (Segment s : due) {
				if (++s.retransmits > 20) {
					shutdown("lost", false);
					return;
				}
			}
		}
		for (Segment s : due) sendSegment(s);
	}

	private void onData(int seq, ByteBuffer b) {
		byte[] data = new byte[b.remaining()];
		b.get(data);
		int cum;
		long bitmap = 0;
		synchronized (recvLock) {
			if (seq == expected) {
				delivered.add(data);
				expected++;
				byte[] next;
				while ((next = outOfOrder.remove(expected)) != null) {
					delivered.add(next);
					expected++;
				}
			} else if (seq > expected && seq - expected < 4096 && delivered.size() < 8192) {
				outOfOrder.put(seq, data);
			}
			cum = expected;
			for (Integer s : outOfOrder.keySet()) {
				int bit = s - cum - 1;
				if (bit >= 0 && bit < 64) bitmap |= 1L << bit;
			}
		}
		ByteBuffer ack = header(TYPE_ACK, 12);
		ack.putInt(cum).putLong(bitmap);
		send(ack, remote);
	}

	private final InputStream input = new InputStream() {
		private byte[] current = EOF;
		private int pos;

		@Override
		public int read() throws IOException {
			byte[] one = new byte[1];
			return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			while (pos >= current.length) {
				if (current == EOF && pos > 0) return -1;
				try {
					byte[] next = delivered.poll(500, TimeUnit.MILLISECONDS);
					if (next == null) {
						if (closed) return -1;
						continue;
					}
					if (next == EOF) {
						current = EOF;
						pos = 1;
						return -1;
					}
					current = next;
					pos = 0;
				} catch (InterruptedException e) {
					throw new IOException("interrupted");
				}
			}
			int n = Math.min(len, current.length - pos);
			System.arraycopy(current, pos, b, off, n);
			pos += n;
			return n;
		}
	};

	private final OutputStream output = new OutputStream() {
		@Override
		public void write(int b) throws IOException {
			PeerLink.this.write(new byte[]{(byte) b}, 0, 1);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			PeerLink.this.write(b, off, len);
		}
	};

	public InputStream input() {
		return input;
	}

	public OutputStream output() {
		return output;
	}

	private void shutdown(String reason, boolean failedToConnect) {
		if (closed) return;
		if (remote != null) {
			// tell the other side, a few times in case one gets lost
			for (int i = 0; i < 3; i++) send(header(TYPE_CLOSE, 0), remote);
		}
		closed = true;
		delivered.add(EOF);
		synchronized (sendLock) {
			sendLock.notifyAll();
		}
		socket.close();
		if (upnp != null) java.util.concurrent.CompletableFuture.runAsync(upnp::remove);
		Listener l = listener;
		if (l != null) l.onClosed(this, reason);
	}

	@Override
	public void close() {
		shutdown("closed", false);
	}
}
