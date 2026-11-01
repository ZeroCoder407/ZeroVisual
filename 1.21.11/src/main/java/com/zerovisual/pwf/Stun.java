package com.zerovisual.pwf;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Minimal STUN client (RFC 5389 Binding request): asks a public STUN server which address and port
 * our UDP socket has on the internet. The servers only answer with that address, no traffic goes through them.
 */
public final class Stun {
	private static final String[][] SERVERS = {
		{"stun.l.google.com", "19302"},
		{"stun1.l.google.com", "19302"},
		{"stun.cloudflare.com", "3478"},
	};
	private static final int MAGIC_COOKIE = 0x2112A442;
	private static volatile InetSocketAddress keepaliveServer;

	private Stun() {
	}

	/** What two STUN servers saw: the public address and how the router picks ports. */
	public record Mapping(InetSocketAddress address, boolean symmetric, int portDelta) {
	}

	/**
	 * Asks two different servers. When both see the same port, the router keeps one public port per socket
	 * ("cone" NAT, easy to punch). Different ports mean a new port per destination ("symmetric" NAT): the peer
	 * then also tries the next ports, many routers count them up one by one.
	 */
	public static Mapping mapping(DatagramSocket socket) {
		InetSocketAddress first = null, second = null;
		int firstIndex = -1;
		for (int i = 0; i < SERVERS.length && second == null; i++) {
			try {
				InetSocketAddress server = new InetSocketAddress(InetAddress.getByName(SERVERS[i][0]), Integer.parseInt(SERVERS[i][1]));
				InetSocketAddress seen = query(socket, server);
				if (seen == null) continue;
				if (first == null) {
					first = seen;
					firstIndex = i;
				} else if (!SERVERS[i][0].equals(SERVERS[firstIndex][0])) {
					second = seen;
				}
			} catch (IOException ignored) {
			}
		}
		if (first == null) return null;
		if (second == null) return new Mapping(first, false, 0);
		int delta = second.getPort() - first.getPort();
		// the address of the second answer is the newest port: the peer starts predicting from there
		return new Mapping(delta == 0 ? first : second, delta != 0, delta);
	}

	/** Public address of the socket, or null when no STUN server answered. Call before anything else reads the socket. */
	public static InetSocketAddress publicAddress(DatagramSocket socket) {
		int oldTimeout;
		try {
			oldTimeout = socket.getSoTimeout();
		} catch (IOException e) {
			return null;
		}
		try {
			for (String[] server : SERVERS) {
				try {
					InetSocketAddress result = query(socket, new InetSocketAddress(InetAddress.getByName(server[0]), Integer.parseInt(server[1])));
					if (result != null) return result;
				} catch (IOException ignored) {
					// next server
				}
			}
			return null;
		} finally {
			try {
				socket.setSoTimeout(oldTimeout);
			} catch (IOException ignored) {
			}
		}
	}

	/**
	 * Sends one Binding request without waiting for the answer. Routers forget a silent UDP port after 30-60 seconds,
	 * so the host keeps it alive this way while the codes travel through the messenger: otherwise the port in the
	 * invite is already gone when the friend's probes arrive. The answer is ignored by whoever reads the socket.
	 */
	public static void keepalive(DatagramSocket socket) {
		try {
			InetSocketAddress server = keepaliveServer;
			if (server == null) {
				server = new InetSocketAddress(InetAddress.getByName(SERVERS[0][0]), Integer.parseInt(SERVERS[0][1]));
				keepaliveServer = server;
			}
			byte[] transaction = new byte[12];
			new SecureRandom().nextBytes(transaction);
			ByteBuffer request = ByteBuffer.allocate(20).putShort((short) 0x0001).putShort((short) 0).putInt(MAGIC_COOKIE).put(transaction);
			socket.send(new DatagramPacket(request.array(), 20, server));
		} catch (IOException ignored) {
		}
	}

	private static InetSocketAddress query(DatagramSocket socket, InetSocketAddress server) throws IOException {
		byte[] transaction = new byte[12];
		new SecureRandom().nextBytes(transaction);
		ByteBuffer request = ByteBuffer.allocate(20);
		request.putShort((short) 0x0001); // Binding request
		request.putShort((short) 0);      // no attributes
		request.putInt(MAGIC_COOKIE);
		request.put(transaction);
		byte[] buffer = new byte[512];
		for (int attempt = 0; attempt < 2; attempt++) {
			socket.send(new DatagramPacket(request.array(), 20, server));
			socket.setSoTimeout(1200);
			long deadline = System.currentTimeMillis() + 1200;
			while (System.currentTimeMillis() < deadline) {
				DatagramPacket response = new DatagramPacket(buffer, buffer.length);
				try {
					socket.receive(response);
				} catch (SocketTimeoutException e) {
					break;
				}
				InetSocketAddress mapped = parse(buffer, response.getLength(), transaction);
				if (mapped != null) return mapped;
			}
		}
		return null;
	}

	private static InetSocketAddress parse(byte[] data, int length, byte[] transaction) {
		if (length < 20) return null;
		ByteBuffer buf = ByteBuffer.wrap(data, 0, length);
		if (buf.getShort() != 0x0101) return null; // Binding success response
		int attrLength = buf.getShort() & 0xFFFF;
		if (buf.getInt() != MAGIC_COOKIE) return null;
		byte[] id = new byte[12];
		buf.get(id);
		if (!Arrays.equals(id, transaction)) return null;
		int end = Math.min(length, 20 + attrLength);
		InetSocketAddress plain = null;
		while (buf.position() + 4 <= end) {
			int type = buf.getShort() & 0xFFFF;
			int len = buf.getShort() & 0xFFFF;
			int start = buf.position();
			if (start + len > end) break;
			if ((type == 0x0020 || type == 0x0001) && len >= 8) {
				buf.get(); // reserved
				int family = buf.get();
				int port = buf.getShort() & 0xFFFF;
				byte[] addr = new byte[family == 0x02 ? 16 : 4];
				buf.get(addr);
				if (type == 0x0020) {
					// XOR-MAPPED-ADDRESS
					port ^= MAGIC_COOKIE >>> 16;
					byte[] key = ByteBuffer.allocate(16).putInt(MAGIC_COOKIE).put(transaction).array();
					for (int i = 0; i < addr.length; i++) addr[i] ^= key[i];
				}
				try {
					InetSocketAddress found = new InetSocketAddress(InetAddress.getByAddress(addr), port);
					if (type == 0x0020) return found;
					plain = found;
				} catch (IOException ignored) {
				}
			}
			buf.position(start + ((len + 3) & ~3));
		}
		return plain;
	}
}
