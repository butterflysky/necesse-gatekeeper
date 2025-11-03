# Changelog

All notable changes to GateKeeper will be documented in this file.

## 1.1.1 – Fix: name cache lookups

- Fix: Ensure per-world state and `name_cache.json` are initialized before name lookups. This resolves a case where a fresh manager instance could not resolve names from the persisted cache, causing `/whitelist approve <name>` and related name-based conveniences to fail after restart.
- Tests: Added coverage implicitly through existing `nameCache_persistsAndResolvesBothWays` test, which now passes.

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
