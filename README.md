<p>
  <img src="src/main/resources/preview.png" alt="GateKeeper preview" width="256" />
</p>

<h1>GateKeeper</h1>

Access control for Necesse multiplayer servers: whitelist players by SteamID or by name, manage the list via server commands, and enforce at connection time.

## Features
- Per‑world whitelist stored next to the save (portable backups).
- Auth‑only access (SteamID). Names can be used to resolve to a SteamID if the player is online or has previously played on the world.
- `/whitelist` server command for enable/disable/list/add/remove.
- Immediate enforcement on connect; non‑whitelisted users are kicked with a clear message.
- Admin notification in chat on denied connect attempts with cooldown to reduce spam.
 - Admins/owners bypass whitelist and are auto‑added on first join; a brief reminder is shown on login.

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

| Command | Description |
|---|---|
| `/whitelist help` | Show command help and usage. |
| `/whitelist status` | Show enabled state and counts. |
| `/whitelist enable` | Turn whitelist on. |
| `/whitelist disable` | Turn whitelist off (allow all). |
| `/whitelist reload` | Reload config from disk; on parse error, keep current settings and rename the broken file. |
| `/whitelist lockdown [on|off|status]` | Emergency mode; only whitelisted may join; suppress notifications. |
| `/whitelist list` | List whitelisted SteamIDs (shows last‑known names when available). |
| `/whitelist online` | List currently connected players with SteamIDs. |
| `/whitelist recent` | Show last denied attempts (index, name, SteamID, age, address). |
| `/whitelist recent approve <index>` | Approve one of the recent denied attempts. |
| `/whitelist approve-last` | Approve the most recent denied attempt. |
| `/whitelist add <auth|name>` | Add a SteamID or resolve a known name to SteamID and add. |
| `/whitelist remove <auth|name>` | Remove a SteamID or resolve a known name and remove. |

Aliases: `/whitelist approve <auth|name>`, `/whitelist deny <auth|name>` — Permissions: ADMIN and above.

Notes:
- When adding a name, the mod tries to resolve an existing SteamID from online players or saved clients. If found, it adds the ID; otherwise it asks you to have the player connect once or provide their SteamID. Names are not stored for access.
- On denied connection, admins/owners see a chat message with the auth (SteamID) and a suggested approval command.
- Denied attempts are logged to `<world>/GateKeeper/denied_log.txt` and kept in memory for quick approval.
- Autocomplete/typeahead for `/whitelist` may not appear on clients that don’t have the mod installed, but the command still works because it’s parsed on the server. Use `/whitelist help` to see usage.
 - Admins and owners can always join even if not whitelisted; on first join they are auto‑added to the whitelist, and receive a reminder: “Whitelist is ENABLED|DISABLED. Use /whitelist help”.

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
- If you edit `whitelist.json` manually while the server is running, use `/whitelist reload` to apply changes. If the file is invalid JSON, GateKeeper keeps the current settings and renames the broken file for review.

## How It Works
- The Necesse client sends an `auth` long during connect (Steam builds use SteamID). The server calls `Server.addClient(...)` and fires `ServerClientConnectedEvent`.
- GateKeeper listens to `ServerClientConnectedEvent` and enforces access:
  - If whitelist is disabled: allow all.
  - If enabled: allow if SteamID is whitelisted, or if the player is ADMIN/OWNER (admins/owners are also auto‑added).
  - Otherwise: record a denied attempt and disconnect with a friendly reason.
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

### Javadocs
- Generate API docs from source Javadocs:
  ```bash
  ./gradlew javadoc
  ```
- Output: `build/docs/javadoc/index.html`

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
