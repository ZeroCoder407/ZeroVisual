package com.zerovisual.pwf;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Dev tooling only (DevScreenshots): a pretend friend inside the host's own game. It answers the invite, waits for
 * the direct link and sends a Minecraft server list ping through it; the reply proves the whole chain works.
 */
public final class PwfSelfTest {
	private PwfSelfTest() {
	}

	public static void run(PwfSession host, int lanPort, Consumer<String> log) {
		Thread t = new Thread(() -> {
			try {
				long start = System.currentTimeMillis();
				while (host.code() == null && System.currentTimeMillis() - start < 10_000) Thread.sleep(50);
				int[] port = {0};
				PwfSession friend = PwfSession.join(host.code(), p -> port[0] = p);
				if (friend == null) {
					log.accept("PWFTEST invite could not be read");
					return;
				}
				while (friend.code() == null) Thread.sleep(50);
				log.accept("PWFTEST answer accepted=" + host.acceptAnswer(friend.code()));
				while (port[0] == 0 && System.currentTimeMillis() - start < 30_000) Thread.sleep(50);
				if (port[0] == 0) {
					log.accept("PWFTEST no connection: " + friend.state() + " " + friend.message());
					return;
				}
				log.accept("PWFTEST connected in " + (System.currentTimeMillis() - start) + " ms, pinging the world");
				String status = ping(port[0]);
				log.accept("PWFTEST ping reply: " + (status.length() > 160 ? status.substring(0, 160) + "..." : status));
				// a second friend connection: try to log in and see whether the world checks the license
				PwfSession host2 = PwfManager.newHost(lanPort);
				log.accept("PWFTEST login: " + loginCheck(host2, protocol(status)));
			} catch (Exception e) {
				log.accept("PWFTEST failed: " + e);
			}
		}, "ZeroVisual PWF self test");
		t.setDaemon(true);
		t.start();
	}

	/** Server list ping (handshake + status request), returns the start of the JSON status. */
	private static String ping(int port) throws IOException {
		try (Socket s = new Socket(InetAddress.getLoopbackAddress(), port)) {
			s.setSoTimeout(10_000);
			OutputStream out = s.getOutputStream();
			ByteArrayOutputStream handshake = new ByteArrayOutputStream();
			DataOutputStream h = new DataOutputStream(handshake);
			varInt(h, 0x00);
			varInt(h, 769);
			byte[] host = "127.0.0.1".getBytes(StandardCharsets.UTF_8);
			varInt(h, host.length);
			h.write(host);
			h.writeShort(port);
			varInt(h, 1);
			packet(out, handshake.toByteArray());
			packet(out, new byte[]{0x00});
			DataInputStream in = new DataInputStream(s.getInputStream());
			readVarInt(in);
			readVarInt(in); // packet id
			int len = readVarInt(in);
			byte[] json = new byte[len];
			in.readFully(json);
			return new String(json, StandardCharsets.UTF_8);
		}
	}

	private static int protocol(String status) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"protocol\":([0-9]+)").matcher(status);
		return m.find() ? Integer.parseInt(m.group(1)) : 769;
	}

	/** Connects through a new invite and sends Login Start: the first answer tells whether the license is checked. */
	private static String loginCheck(PwfSession host, int protocol) throws Exception {
		long start = System.currentTimeMillis();
		while (host.code() == null && System.currentTimeMillis() - start < 10_000) Thread.sleep(50);
		int[] port = {0};
		PwfSession friend = PwfSession.join(host.code(), p -> port[0] = p);
		while (friend.code() == null) Thread.sleep(50);
		host.acceptAnswer(friend.code());
		while (port[0] == 0 && System.currentTimeMillis() - start < 30_000) Thread.sleep(50);
		if (port[0] == 0) return "no connection";
		try (Socket s = new Socket(InetAddress.getLoopbackAddress(), port[0])) {
			s.setSoTimeout(10_000);
			OutputStream out = s.getOutputStream();
			ByteArrayOutputStream handshake = new ByteArrayOutputStream();
			DataOutputStream h = new DataOutputStream(handshake);
			varInt(h, 0x00);
			varInt(h, protocol);
			byte[] addr = "127.0.0.1".getBytes(StandardCharsets.UTF_8);
			varInt(h, addr.length);
			h.write(addr);
			h.writeShort(port[0]);
			varInt(h, 2);
			packet(out, handshake.toByteArray());
			ByteArrayOutputStream login = new ByteArrayOutputStream();
			DataOutputStream l = new DataOutputStream(login);
			varInt(l, 0x00);
			byte[] name = "PwfFriend".getBytes(StandardCharsets.UTF_8);
			varInt(l, name.length);
			l.write(name);
			java.util.UUID id = java.util.UUID.nameUUIDFromBytes("OfflinePlayer:PwfFriend".getBytes(StandardCharsets.UTF_8));
			l.writeLong(id.getMostSignificantBits());
			l.writeLong(id.getLeastSignificantBits());
			packet(out, login.toByteArray());
			DataInputStream in = new DataInputStream(s.getInputStream());
			readVarInt(in);
			int packetId = readVarInt(in);
			return switch (packetId) {
				case 0x01 -> "encryption request = the license is checked";
				case 0x02, 0x03 -> "logged in without a license check (packet " + packetId + ")";
				case 0x00 -> "disconnected by the world";
				default -> "packet " + packetId;
			};
		}
	}

	private static void packet(OutputStream out, byte[] body) throws IOException {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		varInt(new DataOutputStream(b), body.length);
		b.write(body);
		out.write(b.toByteArray());
		out.flush();
	}

	private static void varInt(DataOutputStream out, int value) throws IOException {
		while ((value & ~0x7F) != 0) {
			out.writeByte((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.writeByte(value);
	}

	private static int readVarInt(InputStream in) throws IOException {
		int value = 0, shift = 0, b;
		do {
			b = in.read();
			if (b < 0) throw new IOException("end of stream");
			value |= (b & 0x7F) << shift;
			shift += 7;
		} while ((b & 0x80) != 0 && shift < 35);
		return value;
	}
}
