# GateKeeper — Agent Guidance

This document prepares future AI agents and contributors to work on this codebase with consistent style, safety, and quality.

## Project Overview
- Purpose: Auth-only (SteamID64) whitelist and access control for Necesse dedicated servers.
- Language/Tooling: Java 17, Gradle. Jujutsu (jj) for commits.
- Scope: Server-side enforcement, admin-friendly commands, minimal friction.

## Build & Runtime
- Game path: `gameDirectory` configured in `build.gradle` (default points to Steam install).
- Java: Target Java 17 (matches Necesse bundled JRE).
- Run tasks:
  - `./gradlew buildModJar` — produces mod jar in `build/jar/`.
  - `./gradlew runServer` — launches server with mod (uses `StartDesktopServer`).
  - `./gradlew devSetup` — builds decompiled sources jar and publishes to mavenLocal for IDE navigation.
- Decompiler: Vineflower is a dev-only dependency; never bundled.
- Safety: We include guards so build tasks never overwrite game jars.

## Coding Guidelines
- Keep changes focused and minimal; prefer small, composable methods.
- Java 17 features and standard APIs allowed.
- Add Javadocs for public classes and relevant methods.
- Update README when commands or operator workflows change.
- Maintain per-world configuration and logs:
  - Directory world: `<worldDir>/GateKeeper/`.
  - Zip world: `<parent>/<worldName>.GateKeeper/`.

## Whitelist Design (Authoritative)
- Access is auth-only (SteamID64). Names are never stored for access or persisted in `whitelist.txt`.
- `whitelist.txt` format (auth-only):
  - `enabled=true|false`
  - `lockdown=true|false`
  - `auth:<id64>` (one per line)
- Default: disabled. Admin must enable via `/whitelist enable` or config.
- Features:
  - Lockdown mode: only whitelisted connects allowed; notifications suppressed.
  - Admin anti-noise: per-auth and global notification cooldowns.
  - Denied attempts: recorded in memory + append-only `denied_log.txt`.
  - Remove: kicks connected client(s) and logs to `admin_log.txt`.
- Name convenience: Allowed only to resolve to an auth using online clients or saved players. If unknown, instruct the admin to have the player connect once or provide SteamID.

## Commands (Summary)
- `/whitelist enable|disable|status` — toggle/inspect.
- `/whitelist lockdown [on|off|status]` — emergency mode.
- `/whitelist list` — lists SteamIDs (shows last-known names when available).
- `/whitelist online` — current players (SteamIDs).
- `/whitelist recent` — recent denied attempts; `recent approve <index>`; `approve-last`.
- `/whitelist add <auth|name>` — name resolves to auth if known.
- `/whitelist remove <auth|name>` — name resolves to auth; kicks connected, logs.
- `/whitelist export` — writes saved players (SteamID,name) to `known_players.txt`.

## Tests
- Unit tests use JUnit 5 + Mockito inline.
- Focus on pure logic (auth-only decisions, persistence, rate-limit, logging). Avoid final game-field mutation.
- Do not attempt to boot full game contexts. Prefer mocks and temp directories for world paths.
- Run with `./gradlew test`.
- CI: Not configured because Necesse.jar is not available in CI environments by default.

## Commits
- Use concise, descriptive commit messages (present tense), e.g.:
  - `feat(whitelist): ...`
  - `build: ...`
  - `docs: ...`
  - `test: ...`
- Group related changes; keep diffs readable.
- Sign-off/SSH agent details are environment-specific and not tracked here.

## Do / Don’t
- Do:
  - Keep auth-only invariant — do not reintroduce name-based access.
  - Update README and Javadocs when behavior changes.
  - Log admin actions and denied attempts.
  - Respect per-world path rules (dir vs zip worlds).
- Don’t:
  - Add Steam Web API calls or external dependencies for vanity resolution.
  - Overwrite game assets/jars.
  - Add heavy integration tests that require full game boot.

## Useful Paths & Classes
- Core: `src/main/java/gatekeeper/core/`
  - `WhitelistManager` — persistence, decisions, logs, rate-limit, helpers.
  - `WhitelistCommand` — server commands.
  - `gatekeeper/core/events/WhitelistConnectionListener` — connect enforcement.
  - `gatekeeper/GatekeeperMod` — mod entry.
- Tests: `src/test/java/gatekeeper/core/`
  - `WhitelistManagerTest` — pure logic tests.

## How to Extend Safely
1. Add or modify features in `WhitelistManager` first; preserve auth-only rule.
2. Wire command(s) in `WhitelistCommand`; update help and README.
3. Enforce on connect in listener when relevant (rate-limit + logs).
4. Add unit tests for pure logic. Avoid final-field hacks; test behavior at boundaries you control.
5. Build and run tests: `./gradlew test` and `./gradlew buildModJar`.

