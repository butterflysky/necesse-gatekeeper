# Changelog

All notable changes to GateKeeper will be documented in this file.

## Unreleased

- Build: Added `templates/minimal-mod/` — a minimal Gradle template for new Necesse mods, including Vineflower decompilation, local Maven publishing, and run tasks.
- Build: Added friendly alias task `publishNecesseToLocalMaven` (avoids the verbose Gradle-generated `publish...Publication...` name). `devSetup` now finalizes with this alias.

## 1.1.0 – Name-first approvals and UX

- Feature: Approve by player name. The server now caches a bidirectional mapping between last-seen names and SteamIDs to allow commands like `/whitelist approve <name>` and to pretty-print lists.
- Persistence: Added `<world>/GateKeeper/name_cache.json` (best-effort cache; safe to delete). Whitelist enforcement remains SteamID-only in `whitelist.json`.
- Admin notices: On denied join attempts, message now reads: `Connection blocked for non-whitelisted user: <name> — approve with /whitelist approve <name> or /whitelist approve-last`.
- Commands:
  - `/whitelist list` shows names first, falls back to ID if unknown.
  - `/whitelist recent` shows index, name, age, and address (IDs hidden). `recent approve <index>` still works against the printed index.
  - `/whitelist approve-last` and `/whitelist add/remove` use names in output; IDs shown only as fallback.
  - `/whitelist online` prints names and permission levels (IDs hidden).
- Docs: README updated to reflect name-first ergonomics while preserving SteamID-only enforcement.

Notes:
- Security/integrity is unchanged: only SteamIDs in `whitelist.json` grant access. Names may collide; the cache is purely for convenience.
