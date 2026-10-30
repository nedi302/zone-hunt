# Zone Hunt

GPS hide-and-seek for Android: a real map, a shrinking zone, a ping system and catch codes. Alongside Classic, there are **Zombie Apocalypse**, **Assassin** and **Ice Tag**. The app is called **Zone Hunt** internationally; English is the default language, and German, Spanish and French are also included.

## Languages

- English (default and fallback)
- German
- Spanish
- French

The language follows the device's system setting. You can also change it directly in the app using the gear icon on the home screen: system language, English, German, Spanish or French. The selection is saved; English is the base and fallback language. All visible text, dialogs, notifications and date formats are localized.

## Installation

The ready-to-install APK is available as:

```
ZoneHunt-1.4.0.apk
```

1. Transfer the APK to your Android phone (cable, cloud, messenger, etc.).
2. Tap the file. On first install, you must allow installation from unknown sources for the app you use to open the file.
3. Requirements: Android 8.0 or newer, GPS, mobile data or Wi-Fi.

The APK is release-signed and can be installed directly. When updating from an older version of the same app, the same signature must be used.

## How to play

1. **Host:** tap "Create hunt", enter a name and configure the rules. Your GPS position at the start becomes the zone.
2. **Players:** tap "Join with code" and enter the 4-digit code shared by the host.
3. In the lobby, the host sees all players as selectable chips and can select one or more seekers or have them chosen at random. The random selection can be rerolled as often as needed until the round starts. A round needs at least 2 players, and at least one hider must remain. In Zombie and Ice modes, the host does not choose a seeker; instead, the app draws exactly one starting zombie or one ice hunter. In Assassin mode, all players are assassins with equal standing. Using "Change settings", you can adjust the zone, pace, pings, hide time, team visibility, game mode and the optional shop even after the lobby has been created; the join code stays the same.
4. The host starts the round. Everyone lands on the map and receives a notification.
5. **Hiders** get the hide time set by the host (25 seconds by default) to hide. During this phase, the zone does not shrink, there are no pings, and catching is disabled. **Seekers** wait for the start signal. A full-screen 3-second countdown leads into the start of the round.
6. Then the hunt begins: on every ping, all players' positions are frozen on the map and remain visible at that exact spot until the next ping. Ping markers show the first two letters of the player's name and pulse when a ping arrives.
7. The zone shrinks and moves at the same time. The map always shows the current zone (green, red when you are outside) and the next zone (dashed orange). Every new zone lies entirely within the previous one, and therefore also within the starting zone. Anyone outside the current zone for too long is automatically eliminated. A notification appears when the zone shrinks.
8. Each seeker taps "Catch", selects the hider and enters their 4-digit catch code. The code is visible only on the hider's phone ("Show").
9. The round ends when all hiders are out, all seekers are out, or time runs out — with a notification for everyone.
10. On the results screen, a **replay** can be opened: map, colored routes for all players, play/pause, timeline and 1×/2×/4× speed. Every round is saved locally automatically. The results screen also shows the distance covered during the round and estimated calories; the home screen shows total distance and total calories across all rounds.

### Game modes

The mode is selected under **Game mode** when creating the lobby and applies to the entire round. Catch codes, the ping system, the zone, chat and replays work in all modes; roles, win conditions and some settings differ.

#### Classic

One or more seekers hunt the hiders. A seeker enters the hider's 4-digit catch code to eliminate them. The hiders win if at least one survives until time runs out.

#### Zombie Apocalypse

- The seekers are zombies from the start. Exactly one player is randomly chosen as the starting zombie; everyone else starts as a survivor.
- A zombie still catches a survivor using their 4-digit catch code. The caught player becomes **infected** and joins the horde from that moment on. There is no silent death: once infected, that player hunts the remaining survivors.
- **Infection protection:** A newly turned zombie cannot catch anyone for the configured duration. This prevents an immediate chain reaction at the catch location.
- **Horde sense:** At fixed intervals, zombies receive the live positions of all survivors who are still active, for 4 seconds. The sense is a local effect and is not transmitted as a permanent reveal.
- The round ends when all survivors are infected (**ZOMBIES** win) or when time runs out and at least one survivor remains (**SURVIVORS** win).

#### Assassin

- Every player is an assassin with exactly one secret target. Targets are formed as a closed ring from the player list and the game ID. Everyone is both hunter and hunted at the same time.
- The target panel shows the name of your target. The distance comes from the last ping or from an active target radar, not from the live position.
- **Eliminate:** You must get closer to your target than the configured kill radius and confirm "Eliminate". No catch code is required; the victim receives a message that they have been eliminated.
- After a successful elimination, the assassin inherits the victim's target. This keeps the ring closed until only one person remains.
- The host is authoritative for target assignment. If a player leaves the round or is eliminated (by the zone or by another assassin), the host repairs the ring and distributes the new targets to all devices.
- **Target-only pings:** By default, a ping shows only your current target. This option can be disabled when creating the lobby; all positions are then visible again.
- The round ends as soon as only one assassin remains (**ASSASSIN:<player ID>** wins), or when time runs out. If time runs out, the assassin with the most eliminations wins; ties are decided by the remaining player list.

#### Ice Tag

- Exactly one player is randomly chosen as the **ice hunter**; everyone else is a hider.
- The ice hunter freezes a hider using their 4-digit catch code. The frozen player is not eliminated immediately but remains on the map as a **frozen** marker.
- **Thaw:** An active, non-frozen teammate must stay within the configured radius and remain nearby for the hold time. The frozen player is then active again. The thawing player sees progress; the frozen player receives a notification.
- **Freezing:** If no one reaches them in time, the frozen player is eliminated after the configured melt time.
- The round ends when all hiders are frozen or eliminated (**ICE HUNTER** wins) or when time runs out and at least one hider is active (**HIDERS** win). If the ice hunter is eliminated, the hiders win immediately.

### Chat, overview & notifications

- **Chat:** Use the speech bubble icon in the game view. Messages go either to **Everyone** or only to your **team** (all hiders or all seekers). Team messages are filtered client-side by role. Unread messages appear as a red counter on the chat icon.
- **Player overview:** Use "Overview" in the bottom bar or the menu. It shows all eliminated/found players separately (with reason) and all players still in the game (with role and last ping). Catch codes are never shown there.
- **Notifications:** Local Android notifications appear at round start, when the hunt begins after the hide phase, when the zone shrinks, and at round end. This requires notification permission on Android 13+; the app requests it on first launch. No push servers or Firebase are required.

### Replays

- Recordings are stored exclusively in the app's private storage on the device (`files/replays`).
- Use "Replays" on the home screen to open all rounds and permanently delete them with the trash can.
- Up to 50 replays are kept per device; after that, the oldest recording is removed automatically.
- A replay contains the waypoints of all players, roles, round duration, the zone including its moving history, and the result — but no catch codes.
- During playback, GPS outliers are removed from the routes and the paths are lightly smoothed so that no zigzag lines run across the map.

### Shop (optional)

The shop is enabled in the round settings. Each player earns **1 coin per 5 seconds of survival**; after being eliminated, no more coins are earned. Prices scale automatically with the configured round duration (base: 30 minutes), so purchases remain possible in both short and long rounds. Each mode has its own three items; purchased effects apply only to your own role and are stored per player.

**Classic**

| Item | Role | Price at 30 min | Effect |
| --- | --- | --- | --- |
| Fake marker | Hider | 90 coins | Places a decoy with your own name at a freely chosen spot on the map. All seekers see it as a normal ping marker for 45 s — there is nobody there. The buyer also sees their own decoy on the map, visually distinguished by color. |
| Ping camouflage | Hider | 180 coins | Your marker is missing from the next ping and reappears on the ping after that. |
| Seeker radar | Seeker | 150 coins | Shows the live positions of all hiders for 10 s, even between pings. |

**Zombie Apocalypse**

| Item | Role | Price at 30 min | Effect |
| --- | --- | --- | --- |
| Survivor decoy | Survivor | 90 coins | Places a false trail with your own name to lure the horde away. |
| Horde camouflage | Survivor | 180 coins | Your marker is missing from the next zombie ping. |
| Horde sense | Zombie | 150 coins | Shows all survivors live for 10 s. |

**Assassin**

| Item | Role | Price at 30 min | Effect |
| --- | --- | --- | --- |
| False trail | Assassin | 90 coins | Places a fake name on the map to mislead pursuers. |
| Shadow | Assassin | 180 coins | Your marker is missing from the next ping. |
| Target radar | Assassin | 150 coins | Shows your secret target live for 10 s. |

**Ice Tag**

| Item | Role | Price at 30 min | Effect |
| --- | --- | --- | --- |
| Warm decoy | Hider | 90 coins | Places a false trail with your own name to distract the ice hunter. |
| Hot tea | Hider | 150 coins | Instantly thaws the frozen buyer. |
| Thermal vision | Ice hunter | 150 coins | Shows all hiders live for 10 s. |

Prices in the tables are for a 30-minute round and are adjusted proportionally to the round duration (for 5 minutes, for example, 15/30/25 coins). Decoys are placed by tapping the map. Purchases and effects are transmitted to all players as an MQTT `SHOP` event. Coins, prices and active effects are shown in the game below the player bar. Buyers always see their own decoy; other players see decoys only if their role is meant to see them. Radar, camouflage and sense effects apply exclusively to the buyer.

### Host settings

| Setting | Meaning |
| --- | --- |
| Game mode | Classic, Zombie Apocalypse, Assassin or Ice Tag |
| Zone size | Starting radius in meters |
| Shrink speed | Meters per minute |
| Ping interval | Time between position pings |
| Round duration | Maximum length of the hunt phase |
| Grace period | How long you may stay outside the zone |
| Hide duration | Head start for all hiders, adjustable from 5–120 seconds |
| Team live | Hiders see hiders live, seekers see seekers live; found players disappear immediately |
| Shop | Enables coins and the three shop items for the selected mode |
| Infection protection (Zombie) | 5–60 s during which a newly turned zombie cannot catch anyone |
| Horde sense interval (Zombie) | 15–120 s between the zombies' short live-vision windows |
| Kill radius (Assassin) | 10–60 m within which the secret target can be eliminated |
| Target-only pings (Assassin) | Ping shows only your target; can be disabled to show all positions |
| Thaw radius (Ice) | 5–40 m around a frozen player |
| Thaw time (Ice) | 3–15 s an active teammate must stay in range |
| Melt time (Ice) | 20–120 s until freezing to death without help |

### Zone progression

- The zone shrinks in fixed 5-minute steps. The center can move at each step and may also change direction.
- The center moves no farther in a step than the radius lost in that step. As a result, the next zone always lies entirely within the current zone; all zones remain within the starting zone.
- The progression is calculated from the game ID. All devices and the replay therefore show the same current and next zone without transmitting ongoing zone updates.
- In the HUD, "NOW" shows the current zone and "NEXT" shows the next radius and countdown. When the final step is reached, it becomes "END ZONE".
- With a static zone (shrink speed 0 m/min), the center remains fixed.

## Technical details

- Native Android app (Java), package `com.zonehunt.app`, display name **Zone Hunt**
- Languages: English (base), German, Spanish, French
- Map: osmdroid / OpenStreetMap
- Multiplayer without a dedicated server via MQTT/TLS: `ssl://broker.emqx.io:8883`
- Topic root: `zonenjagd/v1/`
- The zone is calculated on each device from the start time, center, host settings and game ID. The moving progression is deterministic; no ongoing zone updates are transmitted.
- Catch-code protection: hiders only send `SHA-256(code|playerId)` via heartbeat. The seeker checks the entered code locally against this hash.
- Replay: every heartbeat with coordinates is stored locally as a waypoint. At the end of the round, the recording is saved as a JSON file and played back in the replay view.
- Notifications: local Android notifications via `NotificationHelper`; no FCM, no additional server. `POST_NOTIFICATIONS` is required on Android 13+.
- Chat: MQTT `CHAT` event on the game topic. Team messages are filtered client-side by role; the broker could technically read them.
- Shop: MQTT `SHOP` event with item, effect ID and hunting timestamp. Each client applies the effect locally; coins and purchases are tracked locally.
- Multiple seekers: the selected seekers are stored as a comma-separated list in the lobby's `seekerId` field. At least one hider always remains.
- Modes: `mode` in the lobby settings is `CLASSIC`, `ZOMBIE`, `ASSASSIN` or `ICE`. Zombie infections run via `CATCH`/`OUT` with state fields in the heartbeat; Assassin uses `KILL` and host-authoritative `TARGETS`; Ice uses `CATCH`, `THAW` and the melt check via heartbeat states. Replays store the mode and the winner.

### Important note about the broker

`broker.emqx.io` is a public test broker. For permanent or commercial use, it should be replaced with your own authenticated MQTT endpoint. The broker may fail, be throttled, or be read by third parties. Lobby data is not encrypted (only transport via TLS).

## Building from source

Prerequisites:

- JDK 17
- Android SDK with Platform 35 and Build-Tools 35
- Gradle 8.9 (or Android Studio)

Create `local.properties` with the SDK path:

```properties
sdk.dir=C\:\\Path\\to\\Android-SDK
```

Then, in the project folder:

```powershell
gradle assembleRelease
```

The APK is created in `app/build/outputs/apk/release/`: `app-release.apk` with `key.properties`, or `app-release-unsigned.apk` without it.

To create a signed release build, add a local `key.properties` in the project folder:

```properties
storeFile=../signing/zonenjagd-release.jks
storePassword=<password>
keyAlias=<alias>
keyPassword=<password>
```

`key.properties`, the `signing/` folder and keystore files are listed in `.gitignore` and must not be published. If `key.properties` is missing, Gradle creates an unsigned release APK; this is the appropriate case for F-Droid, which signs the APK with its own key.

## Project structure

```
app/src/main/java/com/zonehunt/app/
├── MainActivity.java          Home / name / settings
├── SettingsActivity.java      Language & app settings
├── CreateGameActivity.java    Create rules & lobby
├── JoinGameActivity.java      Enter code
├── LobbyActivity.java         Players, seeker selection, start
├── GameActivity.java          Map, zone, ping, catch dialog, chat, overview, results
├── ReplayListActivity.java    View/delete saved replays
├── ReplayActivity.java        Map, routes and playback
├── model/                     Players, settings, lobby, runtime, zone progression, shop, replay
├── net/GameClient.java        MQTT, lobby, heartbeat, ping, catch logic, shop, recording
├── util/                      Geo, hash, replay storage, UI helpers
└── view/                      Zone overlay, pins, radar, replay markers
```

## Known limitations

- No internet means no connection; the public broker is a single point of failure.
- Location tracking only runs while the app is in the foreground.
- Notifications only appear if the app has permission and notifications are allowed system-wide.
- 4-digit lobby codes are not globally unique; if a collision occurs, simply create a new lobby.
- There are no accounts or moderation features; the MQTT broker should be run privately for serious rounds.
- A replay is saved at the regular end of a round (or when leaving the round). If the app is force-closed during a round, the recording is lost.

## License

This project is licensed under the **GNU General Public License Version 3 (GPL-3.0)**.

Copyright (C) 2026 Michael/nedi

The full license text is in the [`LICENSE`](LICENSE) file and is also available at <https://www.gnu.org/licenses/gpl-3.0.txt>. The GPL-3.0 permits use, modification and redistribution of the software, but requires that derivative works are also licensed under the GPL-3.0 and that the source code remains available when redistributed. There is no warranty.

### Dependencies and assets

- AndroidX AppCompat, Activity and Core, Material Components, and osmdroid: Apache-2.0, compatible with GPL-3.0.
- Eclipse Paho MQTT Client: Eclipse Distribution License 1.0 (BSD-3-Clause), compatible with GPL-3.0.
- The vector icons in `app/src/main/res/drawable` are based on Material Design path data (Apache-2.0).
- Map data and tiles from OpenStreetMap are available under the ODbL; the required attribution is included in the app.
