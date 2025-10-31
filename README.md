<p align="center">
  <img src="src/main/resources/preview.png" alt="GateKeeper preview" width="256" />
</p>

<h1 align="center">GateKeeper</h1>

Server whitelist and access control for Necesse dedicated servers.

Workshop: https://steamcommunity.com/sharedfiles/filedetails/?id=3597047967

## Features
- Per‑world whitelist next to the world save (portable backups)
- Auth‑only (SteamID64) access; names never grant access
- Admin/owner quality of life:
  - Admins/owners bypass whitelist and are auto‑added on first join
  - One‑line join reminder: “Whitelist is ENABLED|DISABLED. Use /whitelist help”
- Clear server commands to manage the list
- Immediate enforcement on connect + friendly kick reason
- Denied‑attempt notifications with cool‑down; audit logs on disk

## Quick Start (Server Owners)
1) Subscribe on Steam Workshop, start the server.
2) In‑game or server console:
   - Enable: `/whitelist enable`
   - Recommended onboarding: have a new player connect once, then `/whitelist approve-last`
   - Lockdown during incidents: `/whitelist lockdown on` (only whitelisted can join)
3) Admins/owners can always join and are auto‑added to the whitelist on first join.

## Commands (Admin)

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
- Adding by name only works if the player is online or has played on this world before; names are never stored for access.
- On denied connection, admins/owners see a chat message with the auth (SteamID) and a suggested approval command.
- Autocomplete/typeahead may not appear on clients without the mod; `/whitelist` still works because the server parses it.
- Admins/owners can always join even if not whitelisted; on first join they are auto‑added and see a reminder.

## Configuration (Per‑World)
- Directory world: `<worldDir>/GateKeeper/whitelist.json`
- Zip world: `<worldParent>/<worldName>.GateKeeper/whitelist.json`

Example `whitelist.json`:
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
Tips:
- `auth` is an array of SteamIDs (longs). Order is not significant.
- If you edit `whitelist.json` while the server is running, use `/whitelist reload`. On invalid JSON, the server keeps the current settings and backs up the broken file.

## How It Works
- On connect, the server receives the client’s SteamID64 (auth) and fires a connect event.
- GateKeeper enforces access:
  - Whitelist disabled: allow all
  - Whitelist enabled: allow whitelisted users or ADMIN/OWNER (privileged users are auto‑added)
  - Otherwise: record a denied attempt and kick with a friendly reason
- Denied attempts are written to `<world>/GateKeeper/denied_log.txt` and kept in memory for quick approval.

Security/Integrity:
- On Steam, `auth` is sourced from Steam and is the correct identifier to trust.
- Names are convenience only; prefer SteamID for durability.

## Issues & Support
- GitHub: https://github.com/butterflysky/necesse-gatekeeper
- Include server logs and your `GateKeeper/` files when reporting issues.

---

## For Mod Developers
Development, build, and source‑navigation instructions have moved to CONTRIBUTING.md.
