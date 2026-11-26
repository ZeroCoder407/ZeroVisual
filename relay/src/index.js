// ZeroVisual relay: passes "this player started or stopped an emote" and "this player listens to this track" between
// players in the same room, and answers music searches (see music.js).
// A room is a hash of the server address. Nothing is stored: when the last player leaves, the room is gone.
import { handleMusic, SERVICES, TRACK_ID } from "./music.js";
import { LINK, PlayerLink } from "./link.js";
import { playerPage } from "./player.js";

export { PlayerLink };

const ID = /^[a-z0-9_]{1,32}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const ROOM = /^[0-9a-f]{32}$/;
const MAX_PLAYERS = 500;
const MAX_MESSAGES_PER_MINUTE = 60;
const MAX_POSITION_MS = 6 * 3600 * 1000;

export default {
	async fetch(request, env, ctx) {
		const url = new URL(request.url);
		if (url.pathname.startsWith("/music/")) return handleMusic(request, env, ctx);
		if (url.pathname === "/player") return playerPage();
		const link = url.pathname.match(/^\/link\/([^/]+)$/);
		if (link) {
			if (!LINK.test(link[1])) return new Response("Bad link", { status: 400 });
			if (request.headers.get("Upgrade") !== "websocket") return new Response("WebSocket only", { status: 426 });
			return env.LINKS.get(env.LINKS.idFromName(link[1])).fetch(request);
		}
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
		pair[1].serializeAttachment({ uuid, emote: null, music: null, minute: 0, count: 0 });
		// the newcomer sees who is already dancing and what the others listen to
		for (const other of sockets) {
			const info = other.deserializeAttachment();
			if (info?.emote) pair[1].send(JSON.stringify({ t: "play", uuid: info.uuid, id: info.emote }));
			if (info?.music) pair[1].send(JSON.stringify(musicMessage(info)));
		}
		return new Response(null, { status: 101, webSocket: pair[0] });
	}

	webSocketMessage(socket, text) {
		if (typeof text !== "string" || text.length > 300) return;
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
		} else if (message.t === "music") {
			// a track and where in it the player is; no track: the player stopped listening
			const pos = Number(message.pos);
			const svc = message.svc;
			if ((SERVICES.includes(svc) || svc === "system") && TRACK_ID.test(message.id ?? "") && Number.isInteger(pos) && pos >= 0 && pos <= MAX_POSITION_MS) {
				info.music = { svc, id: message.id, pos, paused: message.paused === true, at: Date.now() };
				// the system player (what plays in Windows) has nothing to look up: its names come along
				if (svc === "system") {
					info.music.title = String(message.title ?? "").slice(0, 80);
					info.music.artist = String(message.artist ?? "").slice(0, 60);
					info.music.app = String(message.app ?? "").slice(0, 30);
				}
			} else {
				info.music = null;
			}
			this.broadcast(socket, musicMessage(info));
		}
		socket.serializeAttachment(info);
	}

	webSocketClose(socket) {
		const info = socket.deserializeAttachment();
		if (info?.emote) this.broadcast(socket, { t: "stop", uuid: info.uuid });
		if (info?.music) this.broadcast(socket, { t: "music", uuid: info.uuid });
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

/** What a player listens to, with the position moved on to now while it plays. */
function musicMessage(info) {
	const m = info.music;
	if (!m) return { t: "music", uuid: info.uuid };
	const pos = m.paused ? m.pos : Math.min(MAX_POSITION_MS, m.pos + (Date.now() - m.at));
	const out = { t: "music", uuid: info.uuid, svc: m.svc, id: m.id, pos, paused: m.paused };
	if (m.svc === "system") Object.assign(out, { title: m.title, artist: m.artist, app: m.app });
	return out;
}
