# Contributing to GateKeeper

This document is for contributors and mod developers. Server owners should read README.md.

## Build & Run
- Java toolchain: Java 17 (matches Necesse bundled JRE)
- Key tasks:
  - `./gradlew buildModJar` — build the mod jar into `build/jar/`
  - `./gradlew runDevClient` — run client with the mod in dev mode
  - `./gradlew runServer` — run dedicated server with the mod
  - `./gradlew test` — run unit tests (JUnit 5 + Mockito)

Notes:
- Ensure `gameDirectory` in `build.gradle` points to your Necesse install.
- The mod is server‑side only; clients are not required to have it.

## Source Navigation (VS Code)
- Run `./gradlew devSetup` to decompile Necesse into a sources JAR and publish game+sources to `mavenLocal`.
- VS Code will auto‑attach sources for navigation. If it doesn’t:
  - Run “Java: Clean Java Language Server Workspace” and reopen the project.
  - Run `./gradlew decompileNecesseSourcesJar publishNecessePublicationToMavenLocal` and reload Java projects.

## Preview Image
- Vector source: `resources/preview.svg` (1024×1024).
- Export a PNG to `src/main/resources/preview.png`:
  - Inkscape:
    ```bash
    inkscape resources/preview.svg --export-type=png --export-filename=src/main/resources/preview.png --export-width=1024 --export-height=1024
    ```
  - rsvg‑convert:
    ```bash
    rsvg-convert -w 1024 -h 1024 resources/preview.svg > src/main/resources/preview.png
    ```

## Tests
- Scope: pure logic (auth‑only decisions, persistence, rate‑limit, logging). Avoid booting full game contexts.
- Use temp directories for world paths. Avoid mutating final game fields.
- Run with `./gradlew test`.

## Coding Guidelines
- Keep changes small and focused; prefer composable methods.
- Preserve the auth‑only invariant; never grant access by name.
- Per‑world data lives under `<world>/GateKeeper/` (dir worlds) or `<parent>/<world>.GateKeeper/` (zip worlds).
- Log admin actions and denied attempts; avoid noisy spam (use cooldowns).
- Add Javadocs for public classes/methods.

See also: AGENTS.md for deeper project guidance.

## Troubleshooting
- VS Code sources not attached:
  - Clean Java LS workspace; re‑run devSetup; reload project.
- Client run issues (Steam init):
  - Gradle tasks provide `SteamAppId` via environment; no file writes are required.
- Packaging errors (module‑info):
  - The build excludes multi‑release `META‑INF/versions/**` and signature files when merging dependencies.

