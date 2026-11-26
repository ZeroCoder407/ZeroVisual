package com.zerovisual.emote;

import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * A minimal WebSocket client for text messages (RFC 6455), because Java 8 has none. Enough for the emote relay:
 * one connection, small text frames, ping answered, everything else closes it. Blocking: {@link #open} and
 * {@link #read} run on the thread of the caller.
 */
public final class WsClient {
	private static final SecureRandom RANDOM = new SecureRandom();

	private final Socket socket;
	private final DataInputStream in;
	private final OutputStream out;

	private WsClient(Socket socket) throws IOException {
		this.socket = socket;
		this.in = new DataInputStream(socket.getInputStream());
		this.out = socket.getOutputStream();
	}

	/** Connects and upgrades; a {@code wss} URI goes over TLS. */
	public static WsClient open(URI uri) throws IOException {
		boolean tls = "wss".equalsIgnoreCase(uri.getScheme());
		int port = uri.getPort() > 0 ? uri.getPort() : tls ? 443 : 80;
		Socket raw = new Socket();
		raw.connect(new InetSocketAddress(uri.getHost(), port), 10_000);
		Socket socket = tls ? ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, uri.getHost(), port, true) : raw;
		socket.setSoTimeout(0);
		WsClient client = new WsClient(socket);
		try {
			client.handshake(uri);
		} catch (IOException e) {
			client.close();
			throw e;
		}
		return client;
	}

	private void handshake(URI uri) throws IOException {
		byte[] nonce = new byte[16];
		RANDOM.nextBytes(nonce);
		String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
			+ (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
		String request = "GET " + path + " HTTP/1.1\r\n"
			+ "Host: " + uri.getHost() + "\r\n"
			+ "Upgrade: websocket\r\n"
			+ "Connection: Upgrade\r\n"
			+ "Sec-WebSocket-Key: " + Base64.getEncoder().encodeToString(nonce) + "\r\n"
			+ "Sec-WebSocket-Version: 13\r\n"
			+ "User-Agent: ZeroVisual\r\n\r\n";
		out.write(request.getBytes(StandardCharsets.US_ASCII));
		out.flush();
		String status = readLine();
		if (!status.startsWith("HTTP/1.1 101")) throw new IOException("relay answered " + status);
		// the rest of the headers
		while (!readLine().isEmpty()) {
			// nothing to check: the relay is ours
		}
	}

	private String readLine() throws IOException {
		StringBuilder line = new StringBuilder();
		int c;
		while ((c = in.read()) >= 0) {
			if (c == '\n') break;
			if (c != '\r') line.append((char) c);
			if (line.length() > 8192) throw new IOException("header too long");
		}
		if (c < 0) throw new IOException("closed during the handshake");
		return line.toString();
	}

	/** The next text message, null when the connection closes. */
	public String read() throws IOException {
		ByteArrayOutputStream message = new ByteArrayOutputStream();
		while (true) {
			int b0 = in.read();
			if (b0 < 0) return null;
			int b1 = in.readUnsignedByte();
			int opcode = b0 & 0x0F;
			boolean fin = (b0 & 0x80) != 0;
			long length = b1 & 0x7F;
			if (length == 126) length = in.readUnsignedShort();
			else if (length == 127) length = in.readLong();
			if (length > 1 << 20) throw new IOException("frame too large");
			byte[] mask = null;
			if ((b1 & 0x80) != 0) {
				mask = new byte[4];
				in.readFully(mask);
			}
			byte[] payload = new byte[(int) length];
			in.readFully(payload);
			if (mask != null) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
			if (opcode == 0x8) return null;
			if (opcode == 0x9) {
				send(0xA, payload);
				continue;
			}
			if (opcode == 0xA) continue;
			message.write(payload);
			if (fin) return new String(message.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	public void sendText(String text) throws IOException {
		send(0x1, text.getBytes(StandardCharsets.UTF_8));
	}

	/** Frames from a client are always masked. */
	private synchronized void send(int opcode, byte[] payload) throws IOException {
		ByteArrayOutputStream frame = new ByteArrayOutputStream();
		frame.write(0x80 | opcode);
		if (payload.length < 126) {
			frame.write(0x80 | payload.length);
		} else if (payload.length < 65536) {
			frame.write(0x80 | 126);
			frame.write(payload.length >> 8);
			frame.write(payload.length);
		} else {
			frame.write(0x80 | 127);
			for (int i = 7; i >= 0; i--) frame.write((int) ((long) payload.length >> (8 * i)));
		}
		byte[] mask = new byte[4];
		RANDOM.nextBytes(mask);
		frame.write(mask);
		for (int i = 0; i < payload.length; i++) frame.write(payload[i] ^ mask[i & 3]);
		out.write(frame.toByteArray());
		out.flush();
	}

	public void close() {
		try {
			socket.close();
		} catch (IOException ignored) {
			// already gone
		}
	}
}
