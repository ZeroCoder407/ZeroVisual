package com.zerovisual.emote;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Lets players with ZeroVisual see each other's emotes: vanilla servers do not pass mod data between players, so a
 * small relay (see relay/ in the repository) does. Only the player UUID, the emote id and a hash of the server
 * address are sent, nothing else, and {@link #setRoom} with null ends it. No Minecraft classes here.
 */
public final class EmoteSync {
	/** The relay (see relay/); -Dzerovisual.relay points a test build at a local one, empty keeps emotes local. */
	private static final String RELAY_URL = System.getProperty("zerovisual.relay", "wss://zerovisual-relay.zerovisual.workers.dev");
	private static final long RETRY_MS = 15_000;

	private static volatile String room;
	private static volatile UUID self;
	private static volatile WebSocket socket;
	private static volatile boolean connecting;
	private static volatile long nextTry;

	private EmoteSync() {
	}

	/** Called every tick with where the player is; {@code place} null means nowhere to share emotes. */
	public static void setRoom(String place, UUID player) {
		String wanted = place == null || RELAY_URL.isEmpty() ? null : hash(place);
		self = player;
		if (wanted == null ? room != null : !wanted.equals(room)) {
			room = wanted;
			WebSocket old = socket;
			socket = null;
			if (old != null) old.abort();
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

	private static void send(String text) {
		WebSocket s = socket;
		if (s != null) s.sendText(text, true).exceptionally(e -> null);
	}

	private static void connect() {
		connecting = true;
		String target = room;
		UUID player = self;
		URI uri = URI.create(RELAY_URL + "/room/" + target + "?uuid=" + player);
		HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri, new Listener(target)).whenComplete((ws, error) -> {
			connecting = false;
			if (error != null) {
				nextTry = System.currentTimeMillis() + RETRY_MS;
				return;
			}
			if (target.equals(room)) socket = ws;
			else ws.abort();
		});
	}

	private static void lost(WebSocket ws) {
		if (socket == ws) socket = null;
		nextTry = System.currentTimeMillis() + RETRY_MS;
	}

	private static void receive(String text) {
		JsonObject message = JsonParser.parseString(text).getAsJsonObject();
		UUID player = UUID.fromString(message.get("uuid").getAsString());
		if (player.equals(self)) return;
		if ("play".equals(message.get("t").getAsString())) {
			Emote emote = Emotes.byId(message.get("id").getAsString());
			if (emote != null) EmoteManager.play(player, emote);
		} else {
			EmoteManager.stop(player);
		}
	}

	private static String hash(String place) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(place.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 16);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private record Listener(String target) implements WebSocket.Listener {
		private static final StringBuilder BUFFER = new StringBuilder();

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			BUFFER.append(data);
			if (last) {
				try {
					receive(BUFFER.toString());
				} catch (RuntimeException ignored) {
					// a malformed message changes nothing
				}
				BUFFER.setLength(0);
			}
			ws.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
			lost(ws);
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			lost(ws);
		}
	}
}
