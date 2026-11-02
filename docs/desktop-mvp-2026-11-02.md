# TurboCraft Launcher — PC (Windows) MVP

**Date:** 2026-11-02 · **Branch:** `arena/e61cca19-turbocraft-launcher`

## Faisla

PC edition **scratch se** banaya, Prism/Prism-fork se nahi:
Prism ka Qt UI + 180k-line core chhodna padta, aur TCL ka jo UI user ko chahiye tha
wo Compose me dobara likhna hi padta. Isliye:

- **Core:** apna Kotlin/JVM code (`desktop/src/main/kotlin/com/tcl/desktop/core/`)
  — manifest, rules, installer, Java manager, auth, argument builder
- **UI:** Compose Multiplatform 1.12.1 + Material3, TCL Android launcher ka look
  (sidebar, glass cards, blue accent, dark/light)
- **Target:** Windows pehle (`jpackage` Exe/MSI), baaki OS baad me
- **Android build bilkul untouched** — `desktop/` ka apna `settings.gradle.kts`

## Stack

| Cheez | Version |
| --- | --- |
| Kotlin | 2.4.21 |
| Compose Multiplatform | 1.12.1 |
| Gradle | 9.5.0 |
| JDK | 21 (`jvmToolchain(21)`) |
| HTTP | JDK `HttpClient` (koi extra lib nahi) |
| JSON | Gson 2.15.0 |

## Jo ban gaya

- Installer: client jar → libraries (12 parallel) → legacy natives → asset index + objects,
  sha1 verify, `.part` files, already-installed skip
- Launcher: rules engine, placeholder resolution, modern `arguments` + legacy
  `minecraftArguments`, `-Xmx/-Xms`, extra JVM args, game log file + live tail
- Java: `JAVA_HOME`, Program Files ke saare JDKs, PATH, downloaded runtimes;
  warna Temurin (Adoptium API) auto-download + extract
- Auth: Microsoft device-code flow (browser + code, koi embedded webview nahi),
  token refresh, offline UUIDs
- UI: Home (hero card, version dropdown, Play, progress), Versions (filter +
  install/play), Accounts, Settings, Logs

## Verification

CI run **`41398822157`** — dono jobs green:

- `Compile (Kotlin / Compose Desktop)` — success
- `Package (Windows exe)` — success, artifacts: portable app image + MSI (~99 MB each)

Runtime par abhi test nahi hua (sandbox me Windows/JDK nahi hai) — pehla real
launch test user ke PC par hoga.

## Next

1. Fabric / NeoForge / Forge install (version json merge)
2. Multiple instances + mods/shader manager
3. Modpack import (.mrpack, CurseForge zip)
4. Discord RPC, mobile ↔ PC sync
5. Launcher auto-update (GitHub releases)
