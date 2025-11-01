<p align="center">
  <img src="src/main/resources/preview.png" alt="GateKeeper preview" width="256" />
</p>

<h1 align="center">GateKeeper</h1>

Server whitelist and access control for Necesse dedicated servers.

Workshop: https://steamcommunity.com/sharedfiles/filedetails/?id=3597047967

## Features
- Per‑world whitelist next to the world save (portable backups)
- Auth‑only (SteamID64) enforcement; names never grant access, but we cache name↔ID for ergonomics
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
   - Recommended onboarding: have a new player attempt to connect once, then `/whitelist approve-last` or `/whitelist approve <name>`
   - Lockdown during incidents: `/whitelist lockdown on` (suppresses admin notifications for denied connects and shows a “server is in lockdown” message to rejected clients; whitelist enforcement itself is unchanged)
3) Admins/owners can always join and are auto‑added to the whitelist on first join.

## Commands (Admin)

| Command | Description |
|---|---|
| `/whitelist help` | Show command help and usage. |
| `/whitelist status` | Show enabled state and counts. |
| `/whitelist enable` | Turn whitelist on. |
| `/whitelist disable` | Turn whitelist off (allow all). |
| `/whitelist reload` | Reload config from disk; on parse error, keep current settings and rename the broken file. |
| `/whitelist lockdown [on\|off\|status]` | Emergency mode: suppress admin notifications for denied connects and change the kick reason to “server is in lockdown”. Whitelist enforcement is unchanged. |
| `/whitelist list` | List whitelisted users by name (falls back to ID if unknown). |
| `/whitelist online` | List currently connected players by name with permission levels. |
| `/whitelist recent` | Show last denied attempts (index, name, age, address). |
| `/whitelist recent approve <index>` | Approve one of the recent denied attempts. |
| `/whitelist approve-last` | Approve the most recent denied attempt. |
| `/whitelist add <SteamID or player name>` | Prefer names; we resolve to SteamID and persist it. |
| `/whitelist remove <SteamID or player name>` | Prefer names; we resolve to SteamID and remove it. |

Notes:
- Access is strictly by SteamID64. Names do not grant access. We persist a cached name↔ID mapping for convenience so you can operate by name, while the underlying whitelist remains IDs.
- Adding by name works if the player is online, has played before on this world, or has attempted to connect (we cache last‑seen names on denied attempts).
- On denied connection, admins/owners see a message like: “Connection blocked for non‑whitelisted user: <name> — approve with /whitelist approve <name> or /whitelist approve‑last”.
- Autocomplete/typeahead may not appear on clients without the mod; `/whitelist` still works because the server parses it.
- Admins/owners can always join even if not whitelisted; on first join they are auto‑added and see a reminder.
 - `/whitelist recent` displays up to the last 10 attempts. Use the printed index numbers with `recent approve <index>`.

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

Additional files (ergonomics):
- `<world>/GateKeeper/name_cache.json` — cached last‑known names for SteamIDs and last‑seen name→ID mappings to support approving by name and pretty‑printing lists. This file is best‑effort and can be deleted safely; it does not affect enforcement.

## How It Works
- On connect, the server receives the client’s SteamID64 (auth) and fires a connect event.
- GateKeeper enforces access:
  - Whitelist disabled: allow all
  - Whitelist enabled: allow whitelisted users or ADMIN/OWNER (privileged users are auto‑added)
  - Otherwise: record a denied attempt and kick with a friendly reason
- Denied attempts are written to `<world>/GateKeeper/denied_log.txt` and kept in memory for quick approval. Each attempt also updates the name cache so you can approve by name.

Security/Integrity:
- On Steam, `auth` is the trusted identifier. Whitelist enforcement is ID‑based only.
- Names are cached for convenience and may collide; commands prefer names but persist IDs.

## Issues & Support
- GitHub: https://github.com/butterflysky/necesse-gatekeeper
- Include server logs and your `GateKeeper/` files when reporting issues.

---

## For Mod Developers
Development, build, and source‑navigation instructions have moved to CONTRIBUTING.md.
