# ZeroVisual emote relay

Vanilla servers do not pass mod data between players, so this small Cloudflare Worker tells players with ZeroVisual
on the same server which emote the others started or stopped.

It receives the player UUID, the emote id and a hash of the server address. Nothing is stored: each room lives in
memory while somebody is in it. Players can turn sharing off with EmoteMenu → "Show to other players".

## Deploy

```bash
cd relay
npx wrangler login
npx wrangler deploy
```

The relay runs at `https://zerovisual-relay.zerovisual.workers.dev`. For a new address change `RELAY_URL` in
`EmoteSync.java` in both game versions, with `wss://` instead of `https://`.

## Try it locally

```bash
npx wrangler dev --port 8799 --local
```

Then start the game with `-Dzerovisual.relay=ws://127.0.0.1:8799`.
