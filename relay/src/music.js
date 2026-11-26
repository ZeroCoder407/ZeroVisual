// Music catalog for the ZeroVisual player: searches several free music services through their public APIs and gives
// the game one simple track list. The audio does not pass through here: the game streams it from the service directly
// (Audiomack only gets a short redirect, its stream addresses last seconds).
// YouTube is the exception: it is only found here, the game plays it with the official YouTube player in a browser tab.
// Keys (wrangler.toml vars or secrets): YOUTUBE_API_KEY, JAMENDO_CLIENT_ID, AUDIUS_API_KEY, OPENVERSE_CLIENT_ID +
// OPENVERSE_CLIENT_SECRET, AUDIOMACK_CONSUMER_KEY + AUDIOMACK_CONSUMER_SECRET. A service without its key is left out.
const APP = "ZeroVisual";
const PER_SERVICE = 15;
const MAX_RESULTS = 80;
const MIN_SECONDS = 45;
const MAX_SECONDS = 15 * 60;
const SERVICE_TIMEOUT_MS = 7000;
const SEARCH_TTL = 600;
const TRACK_TTL = 3600;
export const TRACK_ID = /^[A-Za-z0-9._:~-]{1,160}$/;

// --- the services; the order is the order of the mixed result list ---

const PROVIDERS = {
	youtube: {
		ready: (env) => env.YOUTUBE_API_KEY,
		search: youtubeSearch,
		popular: true,
		// YouTube ranks by meaning itself ("камзонер" finds KAMZ0NER): no word filter
		exact: true,
		// a search costs 100 of the 10 000 daily quota units: the answer is kept a day
		ttl: (q) => (q ? 86400 : 3600),
		track: youtubeTrack,
	},
	audius: {
		search: (env, q) => audiusList(env, q ? "/tracks/search" : "/tracks/trending", q ? { query: q, limit: String(PER_SERVICE) } : { limit: String(PER_SERVICE), time: "week" }),
		popular: true,
		track: audiusTrack,
	},
	hearthis: {
		search: hearthisSearch,
		popular: true,
		track: hearthisTrack,
	},
	archive: {
		search: archiveSearch,
		popular: false,
		track: archiveTrack,
	},
	audiomack: {
		ready: (env) => env.AUDIOMACK_CONSUMER_KEY && env.AUDIOMACK_CONSUMER_SECRET,
		search: audiomackSearch,
		popular: true,
		track: audiomackTrack,
	},
	jamendo: {
		ready: (env) => env.JAMENDO_CLIENT_ID,
		search: jamendoSearch,
		popular: true,
		track: jamendoTrack,
	},
	openverse: {
		search: openverseSearch,
		popular: false,
		track: openverseTrack,
	},
};
export const SERVICES = Object.keys(PROVIDERS);
/** This relay's own address, for the Audiomack stream redirects. */
let origin = "https://zerovisual-relay.zerovisual.workers.dev";

export async function handleMusic(request, env, ctx) {
	if (request.method !== "GET") return json({ error: "method" }, 405);
	const url = new URL(request.url);
	origin = url.origin;
	const svc = url.searchParams.get("svc") ?? "all";
	const id = url.searchParams.get("id") ?? "";
	// the country for YouTube charts and search, from the game language
	const regionParam = (url.searchParams.get("region") ?? "").toUpperCase();
	const region = /^[A-Z]{2}$/.test(regionParam) ? regionParam : "US";
	let kind;
	if (url.pathname === "/music/search") kind = "search";
	else if (url.pathname === "/music/track") kind = "track";
	else if (url.pathname === "/music/stream") kind = "stream";
	else return json({ error: "path" }, 404);
	if (kind === "search" && svc !== "all" && !PROVIDERS[svc]) return json({ error: "service" }, 400);
	if (kind !== "search" && (!PROVIDERS[svc] || !TRACK_ID.test(id))) return json({ error: "id" }, 400);
	if (kind !== "search" && !ready(env, svc)) return json({ error: "not_configured" }, 502);

	if (kind === "stream") {
		// Audiomack: a fresh, short-lived stream address on every play
		try {
			const target = await audiomackStream(env, id);
			return target ? Response.redirect(target, 302) : json({ error: "upstream" }, 502);
		} catch {
			return json({ error: "upstream" }, 502);
		}
	}

	// the same question within a few minutes is answered from the cache: the services see one request, not thousands
	const q = (url.searchParams.get("q") ?? "").trim().slice(0, 80);
	const key = new Request(`https://cache.zerovisual/v5/${kind}/${svc}/${region}/${encodeURIComponent(kind === "track" ? id : q.toLowerCase())}`);
	const cache = caches.default;
	const cached = await cache.match(key);
	if (cached) return cached;

	let tracks;
	try {
		if (kind === "track") tracks = await PROVIDERS[svc].track(env, id);
		else if (svc === "all") tracks = await searchAll(env, q, region);
		else tracks = await cachedSearch(env, svc, q, region);
	} catch (e) {
		return json({ error: e.message === "not_configured" ? "not_configured" : "upstream" }, 502);
	}
	const response = json({ tracks: tracks.filter(Boolean) }, 200, kind === "track" ? TRACK_TTL : SEARCH_TTL);
	ctx.waitUntil(cache.put(key, response.clone()));
	return response;
}

function ready(env, svc) {
	const p = PROVIDERS[svc];
	return !p.ready || !!p.ready(env);
}

/** One service's answer, cached on its own: a mixed search must not spend the YouTube quota on every new mix. */
async function cachedSearch(env, svc, q, region) {
	const p = PROVIDERS[svc];
	const key = new Request(`https://cache.zerovisual/v5/one/${svc}/${region}/${encodeURIComponent(q.toLowerCase())}`);
	const cached = await caches.default.match(key);
	if (cached) return (await cached.json()).tracks;
	const tracks = (await p.search(env, q, region)).filter(Boolean);
	const ttl = p.ttl ? p.ttl(q) : SEARCH_TTL;
	await caches.default.put(key, json({ tracks }, 200, ttl));
	return tracks;
}

/** Every ready service at once; their lists are mixed one by one, repeats of the same song are left out. */
async function searchAll(env, q, region) {
	const names = SERVICES.filter((s) => ready(env, s) && (q || PROVIDERS[s].popular));
	const results = await Promise.allSettled(names.map((s) => withTimeout(cachedSearch(env, s, q, region), SERVICE_TIMEOUT_MS)));
	// some services match loosely ("макс корж" gives every Макс): only tracks with the words of the question stay
	const words = q.toLowerCase().split(/\s+/).map(normalize).filter((w) => w.length > 1);
	const need = words.length <= 3 ? words.length : Math.ceil(words.length * 0.75);
	const matches = (t) => !words.length || words.filter((w) => normalize(t.artist + " " + t.title).includes(w)).length >= need;
	const lists = results.map((r, i) => (r.status === "fulfilled" ? (r.value ?? []).filter((t) => t && (PROVIDERS[names[i]].exact || matches(t))) : []));
	if (results.every((r) => r.status === "rejected")) throw new Error("upstream");
	const seen = new Set();
	const out = [];
	for (let i = 0; out.length < MAX_RESULTS && lists.some((l) => i < l.length); i++) {
		for (const list of lists) {
			const t = list[i];
			if (!t) continue;
			const sameSong = normalize(t.artist) + "|" + normalize(t.title);
			if (seen.has(sameSong)) continue;
			seen.add(sameSong);
			out.push(t);
		}
	}
	return out.slice(0, MAX_RESULTS);
}

function withTimeout(promise, ms) {
	return Promise.race([promise, new Promise((_, reject) => setTimeout(() => reject(new Error("timeout")), ms))]);
}

function normalize(s) {
	return String(s ?? "").toLowerCase().replace(/[^\p{L}\p{N}]+/gu, "");
}

function json(body, status = 200, ttl = 0) {
	return new Response(JSON.stringify(body), {
		status,
		headers: {
			"Content-Type": "application/json; charset=utf-8",
			"Cache-Control": ttl > 0 ? `public, max-age=${ttl}` : "no-store",
		},
	});
}

async function getJson(url, init = {}) {
	const response = await fetch(url, { ...init, headers: { Accept: "application/json", "User-Agent": "ZeroVisual relay", ...(init.headers ?? {}) } });
	if (!response.ok) throw new Error("upstream");
	return response.json();
}

/** One track as the game reads it; null when it is too short, too long (a DJ mix) or has no audio. */
function track(svc, id, label, title, artist, seconds, stream, page) {
	const duration = Math.round(Number(seconds));
	if (!id || !stream || !Number.isFinite(duration) || duration < MIN_SECONDS || duration > MAX_SECONDS) return null;
	return {
		svc,
		id: String(id),
		label,
		title: String(title ?? "").trim().slice(0, 120) || "?",
		artist: String(artist ?? "").trim().slice(0, 80),
		duration,
		stream,
		page: page || "",
	};
}

// --- YouTube: search and charts of the Data API; the audio is the official player's, in the player page ---

const YOUTUBE = "https://www.googleapis.com/youtube/v3";
/** Until when the daily quota is used up (it starts again at midnight Pacific time): YouTube is skipped till then. */
let youtubeQuotaUntil = 0;

async function youtubeGet(env, path, params) {
	if (Date.now() < youtubeQuotaUntil) return null;
	const response = await fetch(`${YOUTUBE}${path}?${new URLSearchParams({ ...params, key: env.YOUTUBE_API_KEY })}`, { headers: { Accept: "application/json" } });
	if (response.status === 403) {
		const body = await response.json().catch(() => ({}));
		if (JSON.stringify(body).includes("quota")) {
			youtubeQuotaUntil = Date.now() + 3600_000;
			return null;
		}
	}
	if (!response.ok) throw new Error("upstream");
	return response.json();
}

async function youtubeSearch(env, q, region) {
	if (!q) {
		// the music videos popular in the country: 1 quota unit
		const body = await youtubeGet(env, "/videos", { part: "snippet,contentDetails,status", chart: "mostPopular", videoCategoryId: "10", regionCode: region, maxResults: "25" });
		return (body?.items ?? []).map(youtubeTrackOf).filter(Boolean).slice(0, PER_SERVICE);
	}
	const found = await youtubeGet(env, "/search", { part: "id", type: "video", q, maxResults: "20", videoEmbeddable: "true", videoSyndicated: "true", regionCode: region });
	const ids = (found?.items ?? []).map((i) => i.id?.videoId).filter(Boolean);
	if (!ids.length) return [];
	return youtubeVideos(env, ids);
}

async function youtubeTrack(env, id) {
	if (!/^[A-Za-z0-9_-]{11}$/.test(id)) return [];
	return youtubeVideos(env, [id]);
}

/** Durations and the embedding permission of videos: 1 quota unit for up to 50. */
async function youtubeVideos(env, ids) {
	const body = await youtubeGet(env, "/videos", { part: "snippet,contentDetails,status", id: ids.join(",") });
	const byId = new Map((body?.items ?? []).map((v) => [v.id, v]));
	return ids.map((id) => youtubeTrackOf(byId.get(id))).filter(Boolean).slice(0, PER_SERVICE);
}

function youtubeTrackOf(v) {
	if (!v || v.status?.embeddable === false || v.snippet?.liveBroadcastContent === "live") return null;
	const channel = String(v.snippet?.channelTitle ?? "").replace(/ - Topic$/, "");
	return track("youtube", v.id, "YouTube", unescapeHtml(v.snippet?.title), channel, isoSeconds(v.contentDetails?.duration),
		`youtube:${v.id}`, `https://www.youtube.com/watch?v=${v.id}`);
}

/** "PT3M25S" in seconds. */
function isoSeconds(d) {
	const m = /^P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$/.exec(String(d ?? ""));
	if (!m) return 0;
	return ((Number(m[1] ?? 0) * 24 + Number(m[2] ?? 0)) * 60 + Number(m[3] ?? 0)) * 60 + Number(m[4] ?? 0);
}

function unescapeHtml(s) {
	return String(s ?? "").replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">");
}

// --- Audius: free full tracks; gated (paid or follower-only) tracks are left out ---

const AUDIUS = "https://api.audius.co/v1";

function audiusQuery(env, extra) {
	const params = new URLSearchParams({ app_name: APP, ...extra });
	if (env.AUDIUS_API_KEY) params.set("api_key", env.AUDIUS_API_KEY);
	return params;
}

async function audiusList(env, path, extra) {
	const body = await getJson(`${AUDIUS}${path}?${audiusQuery(env, extra)}`);
	return (body.data ?? []).map((t) => audiusTrackOf(env, t));
}

async function audiusTrack(env, id) {
	const body = await getJson(`${AUDIUS}/tracks/${id}?${audiusQuery(env, {})}`);
	return [audiusTrackOf(env, body.data)];
}

function audiusTrackOf(env, t) {
	if (!t || t.is_streamable === false || t.is_stream_gated || t.is_delete) return null;
	return track("audius", t.id, "Audius", t.title, t.user?.name, t.duration,
		`${AUDIUS}/tracks/${t.id}/stream?${audiusQuery(env, {})}`, t.permalink ? `https://audius.co${t.permalink}` : "https://audius.co");
}

// --- hearthis.at: DJs and producers, remixes; id = "user:track" (the permalinks) ---

const HEARTHIS = "https://api-v2.hearthis.at";

async function hearthisSearch(env, q) {
	const list = q
		? await getJson(`${HEARTHIS}/search/?${new URLSearchParams({ t: q, page: "1", count: "40" })}`)
		: await getJson(`${HEARTHIS}/feed/?type=popular&page=1&count=40`);
	return (Array.isArray(list) ? list : []).map(hearthisTrackOf).filter(Boolean).slice(0, PER_SERVICE);
}

async function hearthisTrack(env, id) {
	const [user, permalink] = id.split(":");
	if (!user || !permalink) return [];
	return [hearthisTrackOf(await getJson(`${HEARTHIS}/${encodeURIComponent(user)}/${encodeURIComponent(permalink)}/`))];
}

function hearthisTrackOf(t) {
	if (!t || !t.stream_url || !t.user?.permalink || !t.permalink) return null;
	return track("hearthis", `${t.user.permalink}:${t.permalink}`, "hearthis.at", t.title, t.user.username, t.duration, t.stream_url, t.permalink_url);
}

// --- Internet Archive: uploads of everybody; id = "item:n", the n-th MP3 of the item ---

const ARCHIVE = "https://archive.org";
const ARCHIVE_ITEMS = 6;
const ARCHIVE_PER_ITEM = 3;

async function archiveSearch(env, q) {
	const words = q.toLowerCase().split(/\s+/).filter((w) => w.length > 1);
	if (!words.length) return [];
	const term = q.replace(/[()":\\]/g, " ").trim();
	const params = new URLSearchParams({ q: `(title:(${term}) OR creator:(${term})) AND mediatype:audio`, rows: String(ARCHIVE_ITEMS), output: "json" });
	params.append("fl[]", "identifier");
	const body = await getJson(`${ARCHIVE}/advancedsearch.php?${params}`);
	const items = (body.response?.docs ?? []).map((d) => d.identifier).filter(Boolean);
	const lists = await Promise.allSettled(items.map(async (item) => {
		const files = await archiveFiles(item);
		// the files that match the words best, but any of an item whose name itself matched
		const scored = files.map((f, n) => ({ f, n, score: words.filter((w) => (f.title + " " + f.artist + " " + f.name).toLowerCase().includes(w)).length }));
		scored.sort((a, b) => b.score - a.score);
		return scored.slice(0, ARCHIVE_PER_ITEM).map((s) => archiveTrackOf(item, s.f, s.n));
	}));
	return lists.flatMap((r) => (r.status === "fulfilled" ? r.value : [])).filter(Boolean).slice(0, PER_SERVICE);
}

async function archiveTrack(env, id) {
	const cut = id.lastIndexOf(":");
	const item = id.slice(0, cut), n = Number(id.slice(cut + 1));
	if (cut <= 0 || !Number.isInteger(n)) return [];
	const files = await archiveFiles(item);
	return files[n] ? [archiveTrackOf(item, files[n], n)] : [];
}

/** The MP3 files of an item in a fixed order (the n of the ids), one per song. */
async function archiveFiles(item) {
	const body = await getJson(`${ARCHIVE}/metadata/${encodeURIComponent(item)}`);
	const files = (body.files ?? []).filter((f) => /\.mp3$/i.test(f.name ?? "") && /mp3/i.test(f.format ?? ""));
	// an original MP3 and its derived copies are one song: keep one per base name
	const byBase = new Map();
	for (const f of files) {
		const base = f.name.replace(/(_64kb|_vbr)?\.mp3$/i, "");
		const current = byBase.get(base);
		if (!current || (f.source === "original" && current.source !== "original")) byBase.set(base, f);
	}
	const creator = Array.isArray(body.metadata?.creator) ? body.metadata.creator[0] : body.metadata?.creator;
	return [...byBase.values()].sort((a, b) => a.name.localeCompare(b.name)).map((f) => ({
		name: f.name,
		title: f.title || f.name.replace(/\.mp3$/i, "").replace(/_/g, " "),
		artist: f.artist || f.creator || creator || "",
		seconds: archiveLength(f.length),
	}));
}

function archiveLength(length) {
	if (!length) return 0;
	if (String(length).includes(":")) return String(length).split(":").reduce((s, part) => s * 60 + Number(part), 0);
	return Number(length);
}

/** Uploads often carry the site they came from: "Song [mp3uk.net]". */
function archiveClean(s) {
	return String(s ?? "").replace(/\s*[\[(][^\])]*\.(net|ru|com|org|me|cc|biz|info|su|fm|io)[^\])]*[\])]/gi, "").replace(/\s+/g, " ").trim();
}

function archiveTrackOf(item, f, n) {
	const path = f.name.split("/").map(encodeURIComponent).join("/");
	let title = archiveClean(f.title), artist = archiveClean(f.artist);
	// "Artist - Song" in the title, with no artist of its own
	const dash = title.indexOf(" - ");
	if (!artist && dash > 0) {
		artist = title.slice(0, dash);
		title = title.slice(dash + 3);
	}
	return track("archive", `${item}:${n}`, "Internet Archive", title, artist, f.seconds,
		`${ARCHIVE}/download/${encodeURIComponent(item)}/${path}`, `${ARCHIVE}/details/${encodeURIComponent(item)}`);
}

// --- Audiomack: OAuth 1.0a with the app key (no user); stream addresses live seconds, so the game asks /music/stream ---

const AUDIOMACK = "https://api.audiomack.com/v1";

async function audiomackSearch(env, q) {
	const body = q
		? await audiomackGet(env, "/search", { q, show: "songs", limit: String(PER_SERVICE) })
		: await audiomackGet(env, "/music/trending", { limit: String(PER_SERVICE) });
	const list = Array.isArray(body.results) ? body.results : Array.isArray(body.results?.songs) ? body.results.songs : [];
	return list.map((t) => audiomackTrackOf(t));
}

async function audiomackTrack(env, id) {
	const body = await audiomackGet(env, `/music/${id}`, {});
	return [audiomackTrackOf(body.results ?? body)];
}

function audiomackTrackOf(t) {
	if (!t || !t.id || (t.type && t.type !== "song")) return null;
	const page = t.uploader?.url_slug && t.url_slug ? `https://audiomack.com/${t.uploader.url_slug}/song/${t.url_slug}` : "https://audiomack.com";
	return track("audiomack", t.id, "Audiomack", t.title, t.artist, t.duration,
		`${origin}/music/stream?svc=audiomack&id=${t.id}`, page);
}

async function audiomackStream(env, id) {
	const body = await audiomackRequest(env, "POST", `/music/${id}/play`, {});
	return typeof body.results === "string" ? body.results : body.results?.url ?? body.signedUrl ?? null;
}

function audiomackGet(env, path, params) {
	return audiomackRequest(env, "GET", path, params);
}

async function audiomackRequest(env, method, path, params) {
	const url = `${AUDIOMACK}${path}`;
	const oauth = {
		oauth_consumer_key: env.AUDIOMACK_CONSUMER_KEY,
		oauth_nonce: crypto.randomUUID().replace(/-/g, ""),
		oauth_signature_method: "HMAC-SHA1",
		oauth_timestamp: String(Math.floor(Date.now() / 1000)),
		oauth_version: "1.0",
	};
	const all = { ...params, ...oauth };
	const pct = (s) => encodeURIComponent(s).replace(/[!'()*]/g, (c) => "%" + c.charCodeAt(0).toString(16).toUpperCase());
	const paramString = Object.keys(all).sort().map((k) => `${pct(k)}=${pct(all[k])}`).join("&");
	const base = `${method}&${pct(url)}&${pct(paramString)}`;
	const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(`${pct(env.AUDIOMACK_CONSUMER_SECRET)}&`), { name: "HMAC", hash: "SHA-1" }, false, ["sign"]);
	const signature = btoa(String.fromCharCode(...new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(base)))));
	const query = new URLSearchParams({ ...params, ...oauth, oauth_signature: signature });
	return getJson(`${url}?${query}`, { method });
}

// --- Jamendo: free Creative Commons music; needs a client_id from devportal.jamendo.com ---

const JAMENDO = "https://api.jamendo.com/v3.0";

function jamendoQuery(env, extra) {
	return new URLSearchParams({ client_id: env.JAMENDO_CLIENT_ID, format: "json", audioformat: "mp32", imagesize: "100", ...extra });
}

async function jamendoSearch(env, q) {
	const extra = q ? { search: q, limit: String(PER_SERVICE), boost: "popularity_month" } : { order: "popularity_week", limit: String(PER_SERVICE) };
	const body = await getJson(`${JAMENDO}/tracks/?${jamendoQuery(env, extra)}`);
	return (body.results ?? []).map(jamendoTrackOf);
}

async function jamendoTrack(env, id) {
	if (!/^\d+$/.test(id)) return [];
	const body = await getJson(`${JAMENDO}/tracks/?${jamendoQuery(env, { id })}`);
	return (body.results ?? []).map(jamendoTrackOf).slice(0, 1);
}

function jamendoTrackOf(t) {
	if (!t) return null;
	return track("jamendo", t.id, "Jamendo", t.name, t.artist_name, t.duration, t.audio, t.shareurl);
}

// --- Openverse: openly licensed audio of many sites (Wikimedia, Freesound, ...); music in MP3 only ---

const OPENVERSE = "https://api.openverse.org/v1";
let openverseToken = null;

async function openverseHeaders(env) {
	if (!env.OPENVERSE_CLIENT_ID || !env.OPENVERSE_CLIENT_SECRET) return {};
	if (!openverseToken || openverseToken.until < Date.now()) {
		const response = await fetch(`${OPENVERSE}/auth_tokens/token/`, {
			method: "POST",
			headers: { "Content-Type": "application/x-www-form-urlencoded" },
			body: new URLSearchParams({ grant_type: "client_credentials", client_id: env.OPENVERSE_CLIENT_ID, client_secret: env.OPENVERSE_CLIENT_SECRET }),
		});
		if (!response.ok) return {};
		const body = await response.json();
		openverseToken = { value: body.access_token, until: Date.now() + (Number(body.expires_in) || 3600) * 1000 - 60_000 };
	}
	return { Authorization: `Bearer ${openverseToken.value}` };
}

async function openverseSearch(env, q) {
	const params = new URLSearchParams({ q, category: "music", page_size: "20", mature: "false" });
	const body = await getJson(`${OPENVERSE}/audio/?${params}`, { headers: await openverseHeaders(env) });
	return (body.results ?? []).map(openverseTrackOf).filter(Boolean).slice(0, PER_SERVICE);
}

async function openverseTrack(env, id) {
	return [openverseTrackOf(await getJson(`${OPENVERSE}/audio/${encodeURIComponent(id)}/`, { headers: await openverseHeaders(env) }))];
}

function openverseTrackOf(t) {
	// Jamendo is searched directly already; ccMixter refuses players other than its own site (403)
	if (!t || !/mp3/i.test(t.filetype ?? "") || !t.url || t.source === "jamendo" || t.source === "ccmixter") return null;
	const source = t.source_name ?? t.source ?? "";
	return track("openverse", t.id, source ? `Openverse · ${source}` : "Openverse", t.title, t.creator, (Number(t.duration) || 0) / 1000, t.url, t.foreign_landing_url);
}
