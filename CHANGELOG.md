# Changelog

## 0.6.0

- **Minecraft 1.8.9 (Forge)**: ZeroVisual now also comes for Forge 1.8.9, with the ClickGUI, HUD editor, emotes, Play with friends, the title screen and the visual, cosmetic, HUD and performance modules. Left out, because 1.8.9 has no shields, totems or item cooldowns: ShieldColor, TotemPop, TotemCounter and Cooldowns. Hell and ArmsLegs kill effects and the dragon pet need the newer render states, so they live in the 1.21.4, 1.21.11 and 26.3 builds only.
- **MusicMenu** (M by default): a music player in the game. One search over YouTube, Audius, hearthis.at, Internet Archive, Jamendo and Openverse, or what is popular now; pause, seek, next and volume, and a NowPlaying HUD card. YouTube plays in the official YouTube player in a browser tab that the game opens and controls. **System player**: whatever plays in Windows (Yandex Music, VK or YouTube Music in a browser, Spotify, ...) shows in the menu and on the HUD and is paused, skipped and seeked from the game, with your own subscriptions and no sign-in. **Together**: players with ZeroVisual on the same server see what the others listen to and can listen along in sync. Game music is muted while a track plays (can be turned off). Only legal sources; your own files cannot be played.
- **EmoteMenu, two pages**: B opens the single dances and gestures, 2 switches to the group dances (1 goes back), with the hint under the wheel. Group dances carry a group size: Group dance is for everyone, 3o dance splits the dancers into trios. Everyone in a group dances to one beat, worked out from the roster on every client.
- **New dances**: LOL dance, Jeffrey dance and 3o dance, tracked from real videos, next to the ones from 0.5.0.
- **KillEffect: Hell and ArmsLegs** (1.21.4, 1.21.11, 26.3). Hell: burnt medieval gates rise behind the dying player, swing open, fire spills out and a dozen demon hands drag the body inside before the gates shut. ArmsLegs: magic lifts the body, the tentacles take it by the limbs, pull it apart without a drop of blood and drop the pieces, which fade away. Both draw their own copy of the dying body, so the real one is hidden while the effect runs.
- **Pet: mini dragon** (1.21.4, 1.21.11, 26.3): a scaled-down End dragon that follows the player, with its own wing beats, hovering, pitch and yaw. The experimental Ethereal and tsundere pets were removed.
- Fixed typed text being invisible in the name fields of the account, profile and waypoint screens on 1.21.11 and 26.3.
- **Halo**: a solid ring with a round cross section, lit from above, instead of a flat circle.
- Fixed the player skin breaking after an emote with Sodium installed (seen on FunTime).
- Fixed the second emote page not opening on 1.21.4 (no key handler, and the wheel played the wrong emote from page two) and on 1.8.9 (keys went to `keyPressed`, the wheel listened in `keyTyped`).
- Fixed the group dance losing the beat of the first dancer: every client now puts the newcomer into the roster before splitting it into groups, so the second player joins the first one's beat instead of starting their own.

## 0.5.0

- **Seasons**: leaves, snow, sakura petals or summer fluff fall around you, drift in the wind and settle on the ground. Auto follows the real season.
- **EmoteMenu**: the wheel is redrawn in the manner of the GTA weapon wheel: a translucent ring over the blurred game, the name and number in the middle.
- **EmoteMenu**: Group dance, tracked from a real video. Everyone who picks it dances to one beat, and players close by get a notification. It is purely visual.
- **CustomSky**: new lightness setting for the Rifts colors, from black to white.
- Fixed Nametags and other world text looking dim on 1.21.11 and 26.3: the background was drawn over the text.
- **Minecraft 1.21.11**: ZeroVisual now comes for 1.21.4, 1.21.11 and 26.3, with the same features.
- **HudStyle**: background, lightness (from black to white), opacity, outline, corners and shadow of all HUD cards in one place.
- **Potions**: effect icons.

## 0.4.0

- **EmoteMenu**: an emote wheel (B by default) with 10 emotes: wave, clap, sit, lie down, Rat dance, Rampage, Funked up, the Russian squat dance, flex and moonwalk. Rat dance, Rampage and Funked up are tracked from real dancers. Arms, legs and the body bend in the middle as one solid piece. Players with ZeroVisual on the same server see your emotes; this can be turned off in the module.
- **JumpCircles**: Glyphs (rings of glowing hieroglyphs) and Block wave (a round ridge runs across the tops of the blocks, like a wave through jelly).
- **Crosshair**: T, Square, Brackets, Chevron, Diamond and Circle + Dot, and a color for when you aim at a target.
- **TargetESP**: Crystals circling the target and a Pulse line that swings hard on every hit.
- **KillEffect**: Soul, a beam of light with sparks spiralling up.
- **Wings**: Dragon wings.
- **CustomSky**: Rifts, the ordinary sky split by glowing cracks with depth inside, in three colors of your choice.
- **Animations**: Stab, Slam, Backhand and Twirl.
- **CinematicCamera**: the smooth camera of OptiFine, with adjustable strength.
- **Pet**: Mini me swings its arms and taps its foot again on 26.3, and its idle animations wait for the end of an emote.

## 0.3.0

- **Minecraft 26.3**: ZeroVisual now comes for both 1.21.4 and 26.3, with the same features.
- **Play with friends**: friends join your singleplayer world directly, without a server, proxy or VPN. Pause menu → "Play with friends": send the invite code, paste the answer code of the friend. Each friend gets their own invite. Does not work when both routers block direct connections. Turn off a VPN while playing: the screen warns when one is on.
- **ShieldColor**: the shield turns green while it can block and red while an axe has disabled it (yours from the cooldown, other players' from the shield break sound).
- **HitboxColor**: colored hitbox of the player or mob under the crosshair (with F3+B or on its own).
- **SmoothSwap**: items glide between inventory slots. **ItemZoom**: the item under the mouse grows.
- New **Performance** section: **EntityCulling** (no distant entities), **ParticleLimit**, **CrystalOptimizer** (rendering only: no spin, beams or explosion clouds) and **LiteDrops** (dropped stacks drawn as one item).

## 0.2.1

- **Pet** (was StarPet): choose **Star** or **Mini me**, a small copy of your skin that runs after you, follows you with its eyes, hops when it arrives and, standing still, swings its arms, taps a foot and sits on the floor. Armor: none, iron, diamond or netherite.
- **ItemPhysics**: thrown items somersault in their flight direction.
- **TotemCounter**: background opacity and scale settings.
- After updating, enable Pet again (Cosmetics → Pet): the module was renamed.

## 0.2.0

- **CustomSky**: galaxy, realistic sky with sunsets, or a view of a black hole.
- **Nametags** (only where the game shows name tags, no ESP), **Minimap** (no player radar), **MotionBlur**, **ChatHeads**.
- **TotemPop**, **StarPet**, **LowHealth**, **GlintColor**.
- **KillEffect**: Black hole style. **JumpCircles**: Star and Shatter modes. **Zoom**: the mouse wheel changes the zoom.
- English and Russian interface.
- Official builds are signed; modified builds show "Unofficial build".

## 0.1.0

- First release: 49 visual, cosmetic, HUD and utility modules, ClickGUI with profiles, custom main menu, account manager, waypoints and fonts.
