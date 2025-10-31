# GateKeeper

Access control for Necesse multiplayer servers: whitelist players by SteamID or by name, manage the list via server commands, and enforce at connection time.

## Features
- Per‑world whitelist stored next to the save (portable backups).
- Auth‑only access (SteamID). Names can be used to resolve to a SteamID if the player is online or has previously played on the world.
- `/whitelist` server command for enable/disable/list/add/remove.
- Immediate enforcement on connect; non‑whitelisted users are kicked with a clear message.
- Admin notification in chat on denied connect attempts with cooldown to reduce spam.

## Install & Build
- Build mod JAR:
  ```bash
  ./gradlew buildModJar
  ```
- The JAR outputs to `build/jar/` and is used by `runClient`/`runServer` tasks.

### Workshop Preview Image
- The repository includes a vector preview at `resources/preview.svg` (1024x1024 design).
- Export a PNG named `preview.png` into `src/main/resources/` (Steam Workshop commonly accepts 512–1024 square):
  - With Inkscape:
    ```bash
    inkscape resources/preview.svg --export-type=png --export-filename=src/main/resources/preview.png --export-width=1024 --export-height=1024
    ```
  - With rsvg-convert (librsvg):
    ```bash
    rsvg-convert -w 1024 -h 1024 resources/preview.svg > src/main/resources/preview.png
    ```
- You can tweak the title/subtitle text directly in the SVG.

## Server Commands
- `/whitelist enable` — turn whitelist on.
- `/whitelist disable` — turn whitelist off (allow all).
- `/whitelist status` — show enabled state and counts.
- `/whitelist reload` — reload config from disk; if parse fails, keeps current settings and renames the broken file.
- `/whitelist list` — list whitelisted SteamIDs (shows last‑known names when available).
- `/whitelist lockdown [on|off|status]` — emergency mode; only whitelisted players can join; suppresses notifications.
- `/whitelist online` — list current connected players with SteamIDs.
- `/whitelist recent` — show last denied attempts (index, name, SteamID, age, address).
- `/whitelist recent approve <index>` — approve one of the recent denied attempts.
- `/whitelist approve-last` — approve the most recent denied attempt.
- `/whitelist add <auth|name>` — add a SteamID or name.
- `/whitelist remove <auth|name>` — remove a SteamID or name.
- Aliases: `/whitelist approve <auth|name>`, `/whitelist deny <auth|name>`.
- Permissions: ADMIN and above.

Notes:
- When adding a name, the mod tries to resolve an existing SteamID from online players or saved clients. If found, it adds the ID; otherwise it asks you to have the player connect once or provide their SteamID. Names are not stored for access.
- On denied connection, admins/owners see a chat message with the auth (SteamID) and a suggested approval command.
- Denied attempts are logged to `<world>/GateKeeper/denied_log.txt` and kept in memory for quick approval.

## Config Location (Per‑World)
- Directory world: `<worldDir>/GateKeeper/whitelist.json`
- Zip world: `<worldParent>/<worldName>.GateKeeper/whitelist.json`

Whitelist file format (JSON):
```
{
  "enabled": true,
  "lockdown": false,
  "auth": [
    76561198000000000,
    76561198000000001,
    76561198000000002
  ]
}
```
Notes:
- `auth` is an array of SteamIDs (longs). Order is not significant.

## How It Works
- The Necesse client sends an `auth` long during connect (Steam builds use SteamID). The server calls `Server.addClient(...)` and fires `ServerClientConnectedEvent`.
- GateKeeper listens to `ServerClientConnectedEvent` and immediately kicks if the connecting client is not whitelisted for the current world.
- Admins/owners online receive a one‑per‑auth cooldown chat notification with a command hint to approve.

Security/Integrity:
- On Steam, `auth` is sourced from the Steam API (SteamID). For typical dedicated setups this is the correct identifier to trust.
- Name entries are provided for convenience; prefer SteamID for durability.

## Hosting Notes (Shockbyte, etc.)
- Because the config lives alongside the world, moving or backing up the world keeps the whitelist.
- You can also edit `whitelist.json` directly from the host panel file manager.

## Development
- Run dev client:
  ```bash
  ./gradlew runDevClient
  ```

### Dev Workflow: Auto‑attach Game Sources in VS Code
- One‑shot setup:
  ```bash
  ./gradlew devSetup
  ```
- This decompiles Necesse with Vineflower into a `-sources.jar`, publishes the game + sources to `mavenLocal`, and VS Code auto‑attaches sources for navigation.
- Rebuild sources any time:
  ```bash
  ./gradlew decompileNecesseSourcesJar publishNecessePublicationToMavenLocal
  ```

Troubleshooting:
- If VS Code doesn’t jump to sources, run “Java: Clean Java Language Server Workspace” and reopen the project.
- Ensure the game path in `build.gradle` (`gameDirectory`) matches your install.
