// ZeroVisual emote relay: passes "this player started or stopped an emote" between players in the same room.
// A room is a hash of the server address. Nothing is stored: when the last player leaves, the room is gone.
const ID = /^[a-z0-9_]{1,32}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const ROOM = /^[0-9a-f]{32}$/;
const MAX_PLAYERS = 500;
const MAX_MESSAGES_PER_MINUTE = 60;

export default {
	async fetch(request, env) {
		const url = new URL(request.url);
		const match = url.pathname.match(/^\/room\/([^/]+)$/);
		if (!match || !ROOM.test(match[1])) return new Response("ZeroVisual relay", { status: 404 });
		if (request.headers.get("Upgrade") !== "websocket") return new Response("WebSocket only", { status: 426 });
		if (!UUID.test(url.searchParams.get("uuid") ?? "")) return new Response("Bad player", { status: 400 });
		return env.ROOMS.get(env.ROOMS.idFromName(match[1])).fetch(request);
	},
};

export class EmoteRoom {
	constructor(state) {
		this.state = state;
	}

	async fetch(request) {
		const sockets = this.state.getWebSockets();
		if (sockets.length >= MAX_PLAYERS) return new Response("Room is full", { status: 503 });
		const uuid = new URL(request.url).searchParams.get("uuid");
		const pair = new WebSocketPair();
		// hibernation: an idle room costs nothing, and the player id stays with the socket
		this.state.acceptWebSocket(pair[1]);
		pair[1].serializeAttachment({ uuid, emote: null, minute: 0, count: 0 });
		// the newcomer sees who is already dancing
		for (const other of sockets) {
			const info = other.deserializeAttachment();
			if (info?.emote) pair[1].send(JSON.stringify({ t: "play", uuid: info.uuid, id: info.emote }));
		}
		return new Response(null, { status: 101, webSocket: pair[0] });
	}

	webSocketMessage(socket, text) {
		if (typeof text !== "string" || text.length > 128) return;
		const info = socket.deserializeAttachment();
		const minute = Math.floor(Date.now() / 60000);
		if (minute !== info.minute) {
			info.minute = minute;
			info.count = 0;
		}
		if (++info.count > MAX_MESSAGES_PER_MINUTE) {
			socket.serializeAttachment(info);
			return;
		}
		let message;
		try {
			message = JSON.parse(text);
		} catch {
			return;
		}
		if (message.t === "play" && ID.test(message.id)) {
			info.emote = message.id;
			this.broadcast(socket, { t: "play", uuid: info.uuid, id: message.id });
		} else if (message.t === "stop") {
			info.emote = null;
			this.broadcast(socket, { t: "stop", uuid: info.uuid });
		}
		socket.serializeAttachment(info);
	}

	webSocketClose(socket) {
		const info = socket.deserializeAttachment();
		if (info?.emote) this.broadcast(socket, { t: "stop", uuid: info.uuid });
	}

	webSocketError(socket) {
		this.webSocketClose(socket);
	}

	broadcast(from, message) {
		const text = JSON.stringify(message);
		for (const socket of this.state.getWebSockets()) {
			if (socket === from) continue;
			try {
				socket.send(text);
			} catch {
				// this player just left
			}
		}
	}
}
