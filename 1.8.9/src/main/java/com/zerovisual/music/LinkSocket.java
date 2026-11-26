package com.zerovisual.music;

import com.zerovisual.emote.WsClient;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

/** The WebSocket of {@link YouTubeLink} to the relay, over the WsClient of the emotes (Java 8 has no java.net.http). */
final class LinkSocket {
	interface Handler {
		void text(String text);

		void closed();
	}

	private final WsClient ws;

	private LinkSocket(WsClient ws) {
		this.ws = ws;
	}

	static CompletableFuture<LinkSocket> open(String uri, Handler handler) {
		CompletableFuture<LinkSocket> result = new CompletableFuture<>();
		Thread thread = new Thread(() -> {
			WsClient ws;
			try {
				ws = WsClient.open(URI.create(uri));
			} catch (IOException | RuntimeException e) {
				result.completeExceptionally(e);
				return;
			}
			result.complete(new LinkSocket(ws));
			try {
				String text;
				while ((text = ws.read()) != null) {
					try {
						handler.text(text);
					} catch (RuntimeException ignored) {
						// a malformed message changes nothing
					}
				}
			} catch (IOException ignored) {
				// the connection dropped
			}
			ws.close();
			handler.closed();
		}, "ZeroVisual player link");
		thread.setDaemon(true);
		thread.start();
		return result;
	}

	void send(String text) {
		try {
			ws.sendText(text);
		} catch (IOException e) {
			ws.close();
		}
	}

	void close() {
		ws.close();
	}
}
