package com.zerovisual.emote;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Lets players with ZeroVisual see each other's emotes: vanilla servers do not pass mod data between players, so a
 * small relay (see relay/ in the repository) does. Only the player UUID, the emote id and a hash of the server
 * address are sent, nothing else, and {@link #setRoom} with null ends it. No Minecraft classes here.
 * The same relay and messages as the newer versions, so players on 1.8.9 and on them see each other on one server.
 */
public final class EmoteSync {
	/** The relay (see relay/); -Dzerovisual.relay points a test build at a local one, empty keeps emotes local. */
	public static final String RELAY_URL = System.getProperty("zerovisual.relay", "wss://zerovisual-relay.zerovisual.workers.dev");
	private static final long RETRY_MS = 15_000;

	private static volatile String room;
	private static volatile UUID self;
	private static volatile WsClient socket;
	private static volatile boolean connecting;
	private static volatile long nextTry;
	/** Goes up on every new connection: the room forgot what this player shared before. */
	private static volatile int generation;

	private EmoteSync() {
	}

	/** Called every tick with where the player is; {@code place} null means nowhere to share emotes. */
	public static void setRoom(String place, UUID player) {
		String wanted = place == null || RELAY_URL.isEmpty() ? null : hash(place);
		self = player;
		if (wanted == null ? room != null : !wanted.equals(room)) {
			room = wanted;
			com.zerovisual.music.MusicRoom.clear();
			WsClient old = socket;
			socket = null;
			if (old != null) old.close();
			nextTry = 0;
		}
		if (room != null && socket == null && !connecting && System.currentTimeMillis() >= nextTry) connect();
	}

	public static void sendPlay(String id) {
		send("{\"t\":\"play\",\"id\":\"" + id + "\"}");
	}

	public static void sendStop() {
		send("{\"t\":\"stop\"}");
	}

	/** Goes up with every new connection to the room. */
	public static int generation() {
		return generation;
	}

	/** A message of another feature through the same room (see MusicRoom). */
	public static void sendRaw(String text) {
		send(text);
	}

	private static void send(final String text) {
		final WsClient s = socket;
		if (s == null) return;
		Thread thread = new Thread(() -> {
			try {
				s.sendText(text);
			} catch (IOException e) {
				lost(s);
			}
		}, "ZeroVisual emote send");
		thread.setDaemon(true);
		thread.start();
	}

	private static void connect() {
		connecting = true;
		// the room sends everybody's current track again as soon as it accepts the connection
		com.zerovisual.music.MusicRoom.clearOthers();
		final String target = room;
		final UUID player = self;
		Thread thread = new Thread(() -> {
			WsClient ws;
			try {
				ws = WsClient.open(URI.create(RELAY_URL + "/room/" + target + "?uuid=" + player));
			} catch (IOException | RuntimeException e) {
				connecting = false;
				nextTry = System.currentTimeMillis() + RETRY_MS;
				return;
			}
			connecting = false;
			if (!target.equals(room)) {
				ws.close();
				return;
			}
			socket = ws;
			generation++;
			listen(ws);
		}, "ZeroVisual emote relay");
		thread.setDaemon(true);
		thread.start();
	}

	/** Reads messages until the connection ends. */
	private static void listen(WsClient ws) {
		try {
			String text;
			while ((text = ws.read()) != null) {
				try {
					receive(text);
				} catch (RuntimeException ignored) {
					// a malformed message changes nothing
				}
			}
		} catch (IOException ignored) {
			// the connection dropped: lost() below
		}
		lost(ws);
	}

	private static void lost(WsClient ws) {
		if (socket == ws) socket = null;
		ws.close();
		nextTry = System.currentTimeMillis() + RETRY_MS;
	}

	private static void receive(String text) {
		JsonObject message = new JsonParser().parse(text).getAsJsonObject();
		UUID player = UUID.fromString(message.get("uuid").getAsString());
		if (player.equals(self)) return;
		String type = message.get("t").getAsString();
		if ("play".equals(type)) {
			Emote emote = Emotes.byId(message.get("id").getAsString());
			if (emote != null) EmoteManager.play(player, emote);
		} else if ("stop".equals(type)) {
			EmoteManager.stop(player);
		} else if ("music".equals(type)) {
			com.zerovisual.music.MusicRoom.receive(player, message);
		}
	}

	private static String hash(String place) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(place.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 16; i++) hex.append(String.format("%02x", digest[i] & 0xFF));
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
