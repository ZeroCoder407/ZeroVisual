// Links the game with its player page (src/player.js): both connect to /link/<secret> with their role, and whatever
// one sends reaches the other. The secret is made by the game and given to the page in the address; nothing is stored.
export const LINK = /^[0-9a-f]{32}$/;
const MAX_TEXT = 1000;

export class PlayerLink {
	constructor(state) {
		this.state = state;
	}

	async fetch(request) {
		const role = new URL(request.url).searchParams.get("role");
		if (role !== "game" && role !== "page") return new Response("Bad role", { status: 400 });
		// one connection per role: a newer one (the tab reloaded, the game reconnected) replaces the old
		for (const old of this.state.getWebSockets(role)) {
			try {
				old.close(4000, "replaced");
			} catch {
				// already gone
			}
		}
		const pair = new WebSocketPair();
		this.state.acceptWebSocket(pair[1], [role]);
		const others = this.state.getWebSockets(other(role));
		pair[1].send(JSON.stringify({ t: "peer", on: others.length > 0 }));
		this.tell(others, { t: "peer", on: true });
		return new Response(null, { status: 101, webSocket: pair[0] });
	}

	webSocketMessage(socket, text) {
		if (typeof text !== "string" || text.length > MAX_TEXT) return;
		for (const peer of this.state.getWebSockets(other(this.role(socket)))) {
			try {
				peer.send(text);
			} catch {
				// it just left
			}
		}
	}

	webSocketClose(socket) {
		const role = this.role(socket);
		// the other side learns it is alone, unless this socket was replaced by a newer one of the same role
		if (this.state.getWebSockets(role).some((s) => s !== socket)) return;
		this.tell(this.state.getWebSockets(other(role)), { t: "peer", on: false });
	}

	webSocketError(socket) {
		this.webSocketClose(socket);
	}

	role(socket) {
		return this.state.getTags(socket)[0];
	}

	tell(sockets, message) {
		const text = JSON.stringify(message);
		for (const s of sockets) {
			try {
				s.send(text);
			} catch {
				// it just left
			}
		}
	}
}

function other(role) {
	return role === "game" ? "page" : "game";
}
