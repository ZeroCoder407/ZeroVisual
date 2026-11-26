package com.zerovisual.music;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** The WebSocket of {@link YouTubeLink} to the relay (java.net.http here; 1.8.9 has its own over WsClient). */
final class LinkSocket {
	interface Handler {
		void text(String text);

		void closed();
	}

	private final WebSocket ws;
	/** WebSocket allows one send at a time: each waits for the one before. */
	private CompletableFuture<?> last = CompletableFuture.completedFuture(null);

	private LinkSocket(WebSocket ws) {
		this.ws = ws;
	}

	static CompletableFuture<LinkSocket> open(String uri, Handler handler) {
		return HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create(uri), new Listener(handler)).thenApply(LinkSocket::new);
	}

	synchronized void send(String text) {
		last = last.exceptionally(e -> null).thenCompose(x -> ws.sendText(text, true)).exceptionally(e -> null);
	}

	void close() {
		ws.abort();
	}

	private static final class Listener implements WebSocket.Listener {
		private final Handler handler;
		private final StringBuilder buffer = new StringBuilder();

		Listener(Handler handler) {
			this.handler = handler;
		}

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			buffer.append(data);
			if (last) {
				try {
					handler.text(buffer.toString());
				} catch (RuntimeException ignored) {
					// a malformed message changes nothing
				}
				buffer.setLength(0);
			}
			ws.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
			handler.closed();
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			handler.closed();
		}
	}
}
