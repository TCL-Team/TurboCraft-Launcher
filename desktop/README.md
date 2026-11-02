# TurboCraft Launcher — Desktop (Windows)

PC edition ka naya native launcher. Ye Android app ka port nahi hai: yahan wahi
idea hai — **apna Kotlin core + TCL jaisa UI** — Compose Desktop par.

```
desktop/                     ← alag standalone Gradle build (Android module ko chhoota nahi)
  src/main/kotlin/com/tcl/desktop/
    Main.kt                  window / entry point
    core/AppPaths.kt         %APPDATA%\TurboCraftLauncher (game, libraries, assets, java, logs)
    core/Settings.kt         settings.json (memory, java path, window size, jvm args, theme)
    core/Net.kt              JDK HttpClient: get / post / download + sha1 + unzip
    core/Manifest.kt         Mojang version manifest, rules engine, natives classifier
    core/Installer.kt        vanilla install: client jar → libraries → natives → assets
    core/JavaManager.kt      Java 8/17/21 detect + Temurin auto-download
    core/Auth.kt             Microsoft device-code login, offline accounts
    core/GameLauncher.kt     arguments builder, ProcessBuilder, log tail
    core/VersionResolver.kt  inheritsFrom merge (loader profiles ke liye)
    core/LoaderInstaller.kt  Fabric loader install (meta.fabricmc.net)
    core/AppState.kt         poora launcher state (Compose observable)
    ui/                      TCL look: sidebar + glass cards (home/versions/accounts/settings/logs)
```

## Build (developer machine)

```bash
cd desktop
gradle compileKotlin          # sirf compile check
gradle createDistributable    # app image (self-contained JRE)
gradle packageMsi             # MSI installer (Windows + WiX)
```

JDK 21 chahiye — `jvmToolchain(21)` + foojay resolver khud bhi laa sakta hai.
Gradle 9.5.x use karo (repo me wrapper nahi hai, kyunki build alag hai).

## CI

`.github/workflows/desktop-build.yml`

| Job | Kya karta hai |
| --- | --- |
| Compile | Ubuntu par `compileKotlin` + `jar`, errors annotations me |
| Package | Windows par app image + portable zip + MSI (artifacts) |
| Release | `workflow_dispatch` + `release_tag` → GitHub release me exe/MSI upload |

## Abhi kya kaam karta hai (MVP)

- Vanilla Minecraft (release / snapshot / old) install — assets tak, sha1 verify ke saath
- Fabric loader — version row par `+ Fabric` dabao (baaki loaders agle step)
- Launch: classpath, rules, placeholders, `--width/--height`, log tail
- Microsoft account (device code — browser me code daalo) + offline accounts
- Java auto: system JDK dhoondta hai, warna Temurin 8/17/21 download karta hai
- Settings: RAM, custom Java path, window size, extra JVM args, theme

## Agla kaam (parity ke liye)

Quilt / NeoForge / Forge loader install, mods + shader manager, modpack import
(.mrpack / CurseForge zip), multiple instances, Discord RPC, mobile ↔ PC sync,
launcher auto-update.
