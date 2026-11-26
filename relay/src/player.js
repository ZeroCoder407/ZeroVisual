// The player page: the official YouTube player (IFrame API), steered by the game through /link (see link.js).
// The game opens it as /player#t=<secret>; the secret stays in the address fragment, which is never sent to a server.
export function playerPage() {
	return new Response(PAGE, {
		headers: {
			"Content-Type": "text/html; charset=utf-8",
			"Cache-Control": "public, max-age=300",
			"Referrer-Policy": "strict-origin-when-cross-origin",
		},
	});
}

const PAGE = `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ZeroVisual Player</title>
<style>
	:root { color-scheme: dark; --accent: #8b5cf6; }
	* { box-sizing: border-box; }
	body { margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 16px;
		background: radial-gradient(circle at 50% 0%, #1c1630 0%, #0d0d12 60%); color: #ededf5;
		font: 15px/1.45 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
	.card { width: min(680px, 100%); background: #13131a; border: 1px solid #ffffff14; border-radius: 16px; padding: 20px;
		box-shadow: 0 20px 60px #0008; }
	.head { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
	.logo { width: 28px; height: 28px; border-radius: 8px; background: linear-gradient(135deg, #a78bfa, var(--accent));
		display: grid; place-items: center; font-weight: 700; color: #fff; }
	h1 { font-size: 17px; margin: 0; font-weight: 600; }
	.status { margin-left: auto; font-size: 13px; color: #9a9aad; display: flex; align-items: center; gap: 6px; }
	.dot { width: 8px; height: 8px; border-radius: 50%; background: #6f6f80; }
	.dot.on { background: #4ade80; }
	.video { position: relative; width: 100%; aspect-ratio: 16 / 9; border-radius: 12px; overflow: hidden; background: #000; }
	.video iframe, #player { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; }
	.overlay { position: absolute; inset: 0; display: none; align-items: center; justify-content: center; text-align: center;
		background: #000a; cursor: pointer; font-size: 16px; padding: 20px; }
	.overlay.show { display: flex; }
	.overlay span { background: var(--accent); padding: 10px 18px; border-radius: 999px; font-weight: 600; }
	.now { margin-top: 12px; font-weight: 600; min-height: 1.45em; }
	.hint { margin-top: 4px; font-size: 13px; color: #9a9aad; }
</style>
</head>
<body>
<div class="card">
	<div class="head">
		<div class="logo">Z</div>
		<h1>ZeroVisual · <span id="name">Player</span></h1>
		<div class="status"><div class="dot" id="dot"></div><span id="status"></span></div>
	</div>
	<div class="video">
		<div id="player"></div>
		<div class="overlay" id="overlay"><span id="tap"></span></div>
	</div>
	<div class="now" id="now"></div>
	<div class="hint" id="hint"></div>
</div>
<script>
(function () {
	var ru = (navigator.language || "").toLowerCase().indexOf("ru") === 0 || (navigator.language || "").toLowerCase().indexOf("uk") === 0;
	var T = ru ? {
		name: "Плеер", game: "Игра подключена", noGame: "Ждём игру…", noLink: "Откройте плеер из игры (меню музыки, M)",
		tap: "Нажмите, чтобы включить звук", hint: "Музыкой управляйте из игры (M). Вкладку можно свернуть, но не закрывайте её.",
		error: "Это видео нельзя воспроизвести вне YouTube, играет следующее"
	} : {
		name: "Player", game: "Game connected", noGame: "Waiting for the game…", noLink: "Open the player from the game (music menu, M)",
		tap: "Click to turn the sound on", hint: "Control the music from the game (M). You can minimize this tab, but keep it open.",
		error: "This video cannot be played outside YouTube, the next one plays"
	};
	function el(id) { return document.getElementById(id); }
	el("name").textContent = T.name;
	el("tap").textContent = T.tap;
	el("hint").textContent = T.hint;

	// the secret leaves the address at once: the YouTube player passes the page address on to YouTube
	var token = (location.hash.match(/t=([0-9a-f]{32})/) || [])[1];
	try {
		if (token) sessionStorage.setItem("zvLink", token);
		else token = sessionStorage.getItem("zvLink") || undefined;
	} catch (x) {
		// no storage: a reload then needs the link from the game again
	}
	if (location.hash) history.replaceState(null, "", location.pathname);
	var player = null, ready = false, socket = null, gameOn = false;
	var currentId = null, wantPaused = false, volume = 50, loadedAt = 0, lastError = 0;

	function setStatus() {
		el("dot").className = "dot" + (gameOn ? " on" : "");
		el("status").textContent = !token ? T.noLink : gameOn ? T.game : T.noGame;
	}
	setStatus();
	if (!token) return;

	function send(message) {
		if (socket && socket.readyState === 1) socket.send(JSON.stringify(message));
	}

	function connect() {
		var scheme = location.protocol === "https:" ? "wss://" : "ws://";
		socket = new WebSocket(scheme + location.host + "/link/" + token + "?role=page");
		socket.onmessage = function (e) {
			var m;
			try { m = JSON.parse(e.data); } catch (x) { return; }
			command(m);
		};
		socket.onclose = function () {
			gameOn = false;
			setStatus();
			setTimeout(connect, 2000);
		};
	}

	function command(m) {
		if (m.t === "peer") {
			gameOn = !!m.on;
			setStatus();
			return;
		}
		if (!ready) {
			if (m.t === "load") pending = m;
			if (m.t === "volume") volume = m.v;
			return;
		}
		if (m.t === "load") {
			currentId = m.id;
			wantPaused = !!m.paused;
			loadedAt = Date.now();
			var start = Math.max(0, (m.pos || 0) / 1000);
			if (wantPaused) player.cueVideoById({ videoId: m.id, startSeconds: start });
			else player.loadVideoById({ videoId: m.id, startSeconds: start });
		} else if (m.t === "play") {
			wantPaused = false;
			player.playVideo();
		} else if (m.t === "pause") {
			wantPaused = true;
			player.pauseVideo();
		} else if (m.t === "seek") {
			player.seekTo(Math.max(0, (m.pos || 0) / 1000), true);
		} else if (m.t === "volume") {
			volume = m.v;
			applyVolume();
		} else if (m.t === "stop") {
			currentId = null;
			player.stopVideo();
			el("now").textContent = "";
		}
		report();
	}
	var pending = null;

	function applyVolume() {
		if (!ready) return;
		if (volume <= 0) player.mute();
		else {
			player.unMute();
			player.setVolume(Math.round(volume));
		}
	}

	var NAMES = { "-1": "buffering", "0": "ended", "1": "playing", "2": "paused", "3": "buffering", "5": "paused" };
	function report() {
		if (!ready || !currentId) return;
		var code = player.getPlayerState();
		var s = NAMES[String(code)] || "buffering";
		// asked to play but the browser held it back (no click on the page yet)
		var blocked = !wantPaused && (code === -1 || code === 5) && Date.now() - loadedAt > 2500;
		if (blocked) s = "blocked";
		el("overlay").className = "overlay" + (blocked ? " show" : "");
		var data = player.getVideoData ? player.getVideoData() : null;
		if (data && data.title) el("now").textContent = data.title;
		send({ t: "state", id: currentId, s: s, pos: Math.round(player.getCurrentTime() * 1000), dur: Math.round(player.getDuration() * 1000) });
	}

	el("overlay").onclick = function () {
		if (player && ready) {
			player.unMute();
			player.playVideo();
		}
	};

	window.onYouTubeIframeAPIReady = function () {
		player = new YT.Player("player", {
			width: "640", height: "360",
			playerVars: { autoplay: 1, playsinline: 1, rel: 0, origin: location.origin },
			events: {
				onReady: function () {
					ready = true;
					applyVolume();
					if (pending) {
						var m = pending;
						pending = null;
						command(m);
					}
				},
				onStateChange: function () { report(); },
				onError: function (e) {
					// 100: removed, 101 / 150: the owner forbids embedding
					if (Date.now() - lastError < 500) return;
					lastError = Date.now();
					el("now").textContent = T.error;
					send({ t: "state", id: currentId, s: "error", err: e.data, pos: 0, dur: 0 });
				}
			}
		});
	};

	setInterval(report, 500);
	connect();
	var api = document.createElement("script");
	api.src = "https://www.youtube.com/iframe_api";
	document.head.appendChild(api);
})();
</script>
</body>
</html>`;
