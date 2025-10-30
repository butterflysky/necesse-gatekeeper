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

### Dev Workflow: Decompile + Auto‑attach Sources in VS Code

This project includes a dev‑only flow to decompile the game jar with Vineflower, publish a local Maven artifact for the game with a matching `-sources.jar`, and have VS Code auto‑attach those sources for navigation.

Prerequisites:
- Game installed at `"/home/butterfly/.steam/steam/steamapps/common/Necesse"` (configured in `build.gradle:31`).
- VS Code with the Java extensions (Red Hat Language Support, Debugger for Java).

Key tasks:
- `decompileNecesse`: Decompile `Necesse.jar` into `external/necesse-src` using Vineflower.
- `updateGameVersion`: Parse `external/necesse-src/necesse/engine/GameInfo.java` and update `project.ext.gameVersion` in `build.gradle` to match the game.
- `packNecesseSources`: Build `build/necesse/necesse-<gameVersion>-sources.jar` from `external/necesse-src`.
- `publishNecesseLocal`: Publish the game jar and sources jar to `mavenLocal()` as `local.necesse:necesse:<gameVersion>`.
- `devSetup`: Convenience task to run the whole flow in order: decompile → sync version → pack sources → publish.

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
- Decompiled output lives in `external/necesse-src` and is `.gitignore`d.
- You can re‑run `./gradlew devSetup` whenever the game updates; it will refresh sources and sync `gameVersion` in `build.gradle`.

