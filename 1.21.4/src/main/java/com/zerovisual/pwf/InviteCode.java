package com.zerovisual.pwf;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The codes the players send each other (in a messenger): the session, a secret that proves the friend got the
 * invite from you, the addresses where each side can be reached and what kind of network it is on.
 * Invite: host to friend. Answer: friend back to host.
 */
public record InviteCode(boolean answer, long session, byte[] secret, List<InetSocketAddress> addresses, NetInfo net) {
	private static final String INVITE_PREFIX = "ZV-INV-";
	private static final String ANSWER_PREFIX = "ZV-ANS-";
	private static final int VERSION = 2;

	/**
	 * The network of one side.
	 * @param publicIndex index of the address seen by STUN in {@code addresses}, -1 when unknown
	 * @param symmetric   the router uses a new port per destination (hard to punch)
	 * @param portDelta   how its ports count up between destinations (for predicting them)
	 * @param upnp        a port is forwarded on the router (reachable from anywhere)
	 * @param cgnat       the provider hides the router behind a shared address
	 * @param vpn         the internet goes through a VPN: the address belongs to the VPN server, punching rarely works
	 */
	public record NetInfo(int publicIndex, boolean symmetric, int portDelta, boolean upnp, boolean cgnat, boolean vpn) {
		public static final NetInfo UNKNOWN = new NetInfo(-1, false, 0, false, false, false);
	}

	public String encode() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			out.writeByte(VERSION);
			out.writeLong(session);
			// the answer does not repeat the secret: the friend proves knowing it in every probe
			if (!answer) out.write(secret);
			out.writeByte(addresses.size());
			for (InetSocketAddress a : addresses) {
				byte[] ip = a.getAddress().getAddress();
				out.writeByte(ip.length);
				out.write(ip);
				out.writeShort(a.getPort());
			}
			out.writeByte(net.publicIndex());
			out.writeByte((net.symmetric() ? 1 : 0) | (net.upnp() ? 2 : 0) | (net.cgnat() ? 4 : 0) | (net.vpn() ? 8 : 0));
			out.writeShort(Math.max(-32768, Math.min(32767, net.portDelta())));
			return (answer ? ANSWER_PREFIX : INVITE_PREFIX) + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Parses a pasted code; null when it is not a valid code of the expected kind. */
	public static InviteCode decode(String text, boolean answer) {
		if (text == null) return null;
		String code = text.replaceAll("\\s", "");
		String prefix = answer ? ANSWER_PREFIX : INVITE_PREFIX;
		if (!code.startsWith(prefix)) return null;
		try {
			DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(Base64.getUrlDecoder().decode(code.substring(prefix.length()))));
			if (in.readUnsignedByte() != VERSION) return null;
			long session = in.readLong();
			byte[] secret = new byte[16];
			if (!answer) in.readFully(secret);
			int count = in.readUnsignedByte();
			List<InetSocketAddress> addresses = new ArrayList<>();
			for (int i = 0; i < count && i < 32; i++) {
				int len = in.readUnsignedByte();
				if (len != 4 && len != 16) return null;
				byte[] ip = new byte[len];
				in.readFully(ip);
				addresses.add(new InetSocketAddress(InetAddress.getByAddress(ip), in.readUnsignedShort()));
			}
			int publicIndex = in.readByte();
			int flags = in.readUnsignedByte();
			int delta = in.readShort();
			NetInfo net = new NetInfo(publicIndex < addresses.size() ? publicIndex : -1, (flags & 1) != 0, delta, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0);
			return new InviteCode(answer, session, answer ? null : secret, addresses, net);
		} catch (IOException | IllegalArgumentException e) {
			return null;
		}
	}
}
