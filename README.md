# GateKeeper

A mod to enable access control for multiplayer servers.

## Author
butterflysky

## Installation
```bash
gradle buildModJar
```

The mod will automatically copy to your Necesse mods folder.

## Development

```bash
gradle runDevClient
```

### Dev Workflow: Auto‑attach Sources in VS Code

This project includes a dev‑only flow to decompile the game jar with Vineflower, publish a local Maven artifact for the game with a matching `-sources.jar`, and have VS Code auto‑attach those sources for navigation.

Prerequisites:
- Game installed at `"/home/butterfly/.steam/steam/steamapps/common/Necesse"` (configured in `build.gradle:31`).
- VS Code with the Java extensions (Red Hat Language Support, Debugger for Java).

Key tasks:
- `decompileNecesseSourcesJar`: Build `build/necesse/necesse-<gameVersion>-sources.jar` directly via Vineflower (`-file`).
- `updateGameVersion`: Read `necesse/engine/GameInfo.java` from the sources JAR (fallback to legacy folder) and update `project.ext.gameVersion` in `build.gradle`.
- `publishNecesseLocal`: Publish the game jar and sources jar to `mavenLocal()` as `local.necesse:necesse:<gameVersion>`.
- `devSetup`: Convenience task to run the whole flow in order: decompile (JAR) → sync version → publish.

Recommended flow:
```bash
# One‑shot setup (decompile, sync version, publish with sources)
./gradlew devSetup

# Reload VS Code Java project to pick up sources
# Command Palette: "Java: Clean Java Language Server Workspace" or reload window
```

How it works:
- Build logic uses `compileOnly` on the game jar; your mod jar never includes game classes.
- If `local.necesse:necesse:<gameVersion>` exists in `~/.m2`, Gradle uses it (VS Code auto‑attaches the `-sources.jar`). Otherwise it falls back to the file path in the game directory.
- Run and packaging tasks still point to the real game jars, so shipping is unaffected.

Notes:
- Decompiled sources are generated as a JAR in `build/necesse/` and auto‑attached by VS Code. No folder import is needed.
- A legacy folder task, `decompileNecesse`, still exists if you want to browse files; you can remove it with:
  - `./gradlew cleanDecompiledFolder` (deletes `external/necesse-src`), or manually `rm -rf external/necesse-src`.
- You can re‑run `./gradlew devSetup` whenever the game updates; it will refresh sources and sync `gameVersion` in `build.gradle`.
