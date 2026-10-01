# ULTRAS_Discord_logs_v1

Professional, modular Discord logging for Minecraft. **Webhook-first** (no bot needed), bot support is optional.

```
Minecraft Event → Listener → LogService (validate · dedupe · Log ID) → pipeline thread (render embed)
  → DiscordLogQueue (one lane per destination: bounded, retry, backoff, 429 handling)
  → WebhookConnection | BotConnection → Discord channel          (+ async local log file)
```

## 1. Requirements
| | |
|---|---|
| Minecraft | **26.2** (Paper) |
| Paper API | `io.papermc.paper:paper-api:26.2.build.+` (Paper moved to the 26.x version scheme; 26.2 is the current stable line) |
| Java | **25** (required by Paper 26.x) |
| Gradle | 9.1+ (needed for Java 25) |
| Optional plugins | Vault, LuckPerms, Floodgate/Geyser, EssentialsX, SuperVanish/PremiumVanish, AdvancedBan |

Nothing else is required. No bot, no JDA, no external library is shaded (the plugin uses the JDK `HttpClient` and Gson that ships with Paper).

## 2. Installation
1. Build (section 27) or take the jar from `build/libs/`.
2. Put it in `plugins/`, start the server once.
3. Edit `plugins/ULTRAS_Discord_logs_v1/discord.yml` and paste at least the `default` webhook URL.
4. `/uc_discord_logs reload`, then open `/uc_discord_logs` and left-click any log to send a test.

With only `default` configured, every log goes there (`discord.fallback-to-default-when-unconfigured: true`). Add more webhooks later to split logs by channel.

## 3. Discord Webhook setup (recommended)
Discord Server → Channel → **Edit Channel** → **Integrations** → **Webhooks** → **Create Webhook** → **Copy Webhook URL**.

```yaml
# discord.yml
discord:
  connections:
    join:
      type: WEBHOOK
      webhook-url: "PASTE_WEBHOOK_URL_HERE"
      username: "ULTRAS | Join"
```
**The webhook URL is a secret.** Anyone with it can post in your channel. Never share it. The plugin never prints it (console, local logs, exceptions, GUI, chat, Discord).

Per connection you can set `username`, `avatar-url`, `thread-id` (post into an existing thread), `thread-name` (forum channels) and `allowed-mentions` (all `false` by default). Discord rejects webhook usernames containing "discord" or "clyde"; such a username is ignored and reported on reload.

**One webhook → many logs, one log → its own webhook** are both supported: set `connection:` per log in `logs.yml`. Connections that point to the same Discord destination share one queue and rate limiter automatically.

## 4. Optional Bot setup
A bot is only needed if you want the bot identity or future bot-only features.
1. Discord Developer Portal → **New Application** → **Bot** → **Reset Token** / copy token.
2. No privileged intents are needed (the plugin only sends messages through the REST API).
3. Invite the bot with *View Channel*, *Send Messages*, *Embed Links*.
4. Copy the channel ID (Developer Mode → right click channel → Copy ID).
```yaml
staff:
  type: BOT
  token: "BOT_TOKEN"
  channel-id: "123456789012345678"
```
A connection is **either** WEBHOOK (`webhook-url`) **or** BOT (`token` + `channel-id`). A failing bot never affects webhooks. If no BOT connection exists, nothing bot-related ever runs. Because messages are sent through REST (no gateway session), the bot appears *offline* in the member list; this is expected.

## 5. Files
`config.yml` (settings, queue, rotation, sounds) · `discord.yml` (connections) · `logs.yml` (enable/route every log + per-log settings) · `messages.yml` (all Discord embed templates) · `messages_en.yml` / `messages_ar.yml` (Minecraft + GUI texts, Discord title/field-name translations).

GUI changes (enable/disable a log, toggles) are saved to `data/gui-overrides.yml`, so your commented YAML files are never rewritten.

## 6. Logs
| Group | Logs |
|---|---|
| Players | `join` `quit` `first-join` `player-stats` |
| Combat | `player-kill` `death` |
| Gameplay | `creative-item` `gamemode` `teleport` `advancement` `vanish` `sign-change` `book-edit` `economy-transaction` + heavy (off by default): `item-drop` `item-pickup` `container-open` `block-break` `block-place` |
| Punishments | `punishments` (ban, unban, tempban, IP ban/unban, kick, mute, unmute, tempmute, warn, jail) `freeze` |
| Chat | `chat` `commands` |
| Security | `ip-mismatch` `op-change` `whitelist-change` `permission-change` |
| Statistics | `server-top` `server-stats` `server-start` `server-stop` `server-reload` `world-load` `world-unload` `gamerule` + heavy: `weather` `time-change` |

**Truthfulness rules**
* *Command attempted ≠ success.* Vanilla `ban/pardon/ban-ip/pardon-ip/op/deop/whitelist` are verified by comparing server state before/after. Plugin punishments are logged only from the plugin's own confirmed events (AdvancedBan, EssentialsX mute/jail) or from `UltrasPunishmentEvent`. Unverified punishments are dropped (`require-confirmation: true`).
* Command log result is `Failure` only when provable (cancelled / no permission), otherwise `Unknown`.
* Missing data is `Unknown`; a field whose data is entirely unavailable is simply omitted.
* IP mismatch is titled *Potential Account/IP Mismatch*, uses neutral wording and only fires for an IP the account never used before (`only-new-ips`).

## 7. GUI & commands
`/uc_discord_logs` opens the menu (📋 Logs, 🚫 Punishments, 🎮 Gameplay, 👤 Players, ⚔ Combat, 💬 Chat, 🛡 Security, 📊 Statistics, ⚙ Settings, 🌐 Language).
In a log list: **left-click** = test log, **right-click** = enable/disable (admin). *Delete Logs* → confirmation screen → deletes only the local files of that group (moved to `backups/` unless `storage.backup-before-delete: false`).
Other forms: `/uc_discord_logs reload`, `/uc_discord_logs <log-name>` (tab-completes dynamically from the registry).
Test flow: log exists → permission → enabled → connection exists → connection valid → cooldown (10 s) → build data → queue → result.
Language is per player (GUI → Language); `plugin.language` in `config.yml` is only the default (also used for Discord titles).

## 8. Permissions
`ultras.discordlogs.use` · `.reload` · `.test` · `.delete` · `.admin` (includes all; also required for toggles). Default: op.

## 9. Placeholders
`%player% %uuid% %ip% %rank% %world% %x% %y% %z% %gamemode% %executor% %target% %command% %reason% %duration% %time% %date% %server% %version% %is_op% %is_vanish% %log_id%` plus `%location% %prefix% %platform% %ping% %avatar%` and log specific ones (for example `%item% %amount% %enchantments% %victim_player% %killer_player% %weapon% %old_gamemode% %new_gamemode% %teleport_type% %top_kills% %tps%`; see the `sample` data in `BuiltinLogs` or the templates in `messages.yml`). Two-player logs use `victim_` / `killer_` prefixes.

## 10. Hooks (all optional)
Vault / LuckPerms (rank, prefix, permission changes), Floodgate / Geyser (Java vs Bedrock), SuperVanish / PremiumVanish / EssentialsX (vanish events + state), AdvancedBan and EssentialsX (mute, jail, balance). Others (LiteBans, shop and crate plugins, freeze plugins, CoreProtect, WorldGuard) integrate through the API events below.

## 11. Developer API
```java
UltrasApi api = UltrasApi.get();
api.registerCustomLog(plugin, new CustomLogDefinition("crate-reward")
        .connection("gameplay").title("🎁 Crate Reward").field("Player", "%player%").field("Reward", "%reward%"));
api.log("crate-reward", Map.of("player", "Steve", "reward", "Diamond x3"));
// or from anywhere (also async): Bukkit.getPluginManager().callEvent(new UltrasCustomLogEvent("crate-reward", data));
// punishment plugins: new UltrasPunishmentEvent("BAN", target, uuid, executor, reason, durationMs, "MyPlugin", false)
// freeze plugins:     new UltrasFreezeEvent(player, executor, reason, command, true)
```
Services: `LogRegistry`, `LogService`, `DiscordConnectionManager`, `StorageManager` are exposed through `UltrasApi`.

## 12. Storage
Local logs: `plugins/ULTRAS_Discord_logs_v1/logs/*.yml` (`players`, `punishments`, `deaths`, `commands`, `gamemode`, `teleport`, `chat`, `security`, `statistics`, `server`, `gameplay`, `custom`) with Log ID, player, UUID, type, IP (if allowed), date, time, world, coordinates, executor, target, data. Rotation: `DAILY`/`MONTHLY`/size, gzip, archive folder, max files. Player data: `data/players.yml` (name, IP history, first/last join, language, statistics cache). Everything goes through the `StorageBackend` interface, so SQLite/MySQL/PostgreSQL backends can be added without touching the log engine. Log IDs (`ULTRAS-20260930-000001`) are thread-safe and restart-safe.

## 13. Performance
Main thread only collects a snapshot. Rendering, queueing, HTTP and file I/O run on background threads. Queues are bounded (oldest dropped on overflow, with a rate-limited warning). Discord 429 / `Retry-After` / `X-RateLimit-*` are honoured; failures retry with exponential backoff. Server top uses a cached, round-robin statistics capture (at most 3 online players per second); it never reads offline player files. Heavy logs are off by default.

## 14. Security
Secrets are redacted from every log line and exception text. IPs are never shown in Minecraft chat. `security.log-ip-addresses: false` disables reading, storing and sending IPs. Chat/command text is sanitised (`@everyone`, `<@…>`) and webhook `allowed_mentions` default to none. Every GUI click re-checks permissions.

## 15. Troubleshooting
* Connection shows `INVALID`: placeholder URL still present, wrong format, or Discord returned 401/404 (webhook deleted).
* Nothing arrives: run `/uc_discord_logs join`; check the log is enabled and the connection status (Settings → Connections).
* `Unknown` values: the data is not exposed by Minecraft or no hook plugin is installed (for example Vanish needs a vanish plugin).
* Enable `settings.debug` for stack frames (never exception messages with secrets).

## 16. Known limits (Minecraft API)
* Client version is not exposed by Paper: it is not logged unless you add ViaVersion data through the API.
* Minecraft cannot tell whether a creative click came from the creative menu or from moving an owned item: the *gain check* only logs real inventory gains.
* Plugin-initiated teleports/gamemode changes cannot be attributed to a plugin (`Plugin (not identifiable)`).
* Vanilla has no "block placed" statistic; it is derived from `USE_ITEM` on block items.
* Difficulty changes have no Bukkit event, so they are not logged.
* Economy transactions need EssentialsX events (Vault has none). Shop/crate rewards need the API.
* LiteBans exposes only an abstract listener class; use `UltrasPunishmentEvent`.

## 27. Build
```bash
gradle wrapper --gradle-version 9.1.0   # once
./gradlew build                          # jar: build/libs/ULTRAS_Discord_logs_v1-1.0.0.jar
./gradlew test
```
