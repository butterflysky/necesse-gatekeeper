Minimal Necesse Mod Gradle Template

This template provides a minimal Gradle build for a new Necesse mod, including:
- Java 17 toolchain and sane defaults
- Build task to produce a mod JAR (`build/jar/`)
- Run tasks for client and dedicated server
- Dev-only Vineflower decompilation to generate a `-sources.jar` for IDE navigation
- Local Maven publication of `Necesse.jar` + `-sources.jar` for better IDE attachment

Quick start
- Copy this folder to a new repo (or wherever you keep your mod sources).
- Edit `settings.gradle` and `build.gradle` values under the “Change the values” section.
- Set `gameDirectory` in `build.gradle` to your Necesse install path.
- Run `./gradlew devSetup` to decompile sources and publish to `~/.m2`.
- Build your mod: `./gradlew buildModJar`.

Useful tasks
- `decompileNecesseSourcesJar` — produce `necesse-<gameVersion>-sources.jar` via Vineflower.
- `updateGameVersion` — parse `GameInfo.java` from the sources JAR and update `project.ext.gameVersion` in `build.gradle`.
- `publishNecesseToLocalMaven` — publish `Necesse.jar` and the sources JAR to `mavenLocal`.
- `devSetup` — `decompileNecesseSourcesJar` + `updateGameVersion`, then `publishNecesseToLocalMaven`.
- `runClient`, `runDevClient`, `runServer` — run the game with your mod from `build/jar/`.

Notes
- The decompiler (Vineflower) is a dev-only dependency; it’s never bundled with your mod.
- The template avoids writing into the game install. It only reads `Necesse.jar` and writes outputs to `build/`.
- The publish alias avoids the verbose Gradle-generated name and keeps your workflow ergonomic.

