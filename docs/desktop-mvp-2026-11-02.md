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
  install/play, `+ Fabric`), Accounts, Settings, Logs
- Fabric loader: `VersionResolver` child+parent json merge (`inheritsFrom`),
  `meta.fabricmc.net` se latest stable loader, profile json + libraries
- Mods screen: list, enable/disable (`.jar` ↔ `.jar.disabled`), delete, folder open
- **Branding:** `desktop/gradle.properties` — ek file se naam, version, vendor, URL;
  `generateBrand` task `Brand.kt` banata hai (window title, UI, data folder, exe/MSI,
  user-agent, game args sab wahan se). Details: `desktop/BRANDING.md`

## v0.3.1 — packaged build crash ka asli karan

Packaged exe me har network call fail hoti thi:
`Could not initialize class com.tcl.desktop.core.Net` (logs me `java/net/http/HttpClient`).

Karan: `jpackage`/`jlink` runtime image me sirf wahi JDK modules jaate hain jo maangein.
`compose.desktop` ne `java.net.http` include nahi kiya tha — source se chalane par poora
JDK hota hai isliye CI ka smoke test pass ho gaya, lekin MSI/exe me HttpClient load hi
nahi hota tha. Fix: `nativeDistributions { includeAllModules = true }` (poora runtime,
~40 MB extra) — ab koi module missing nahi ho sakti.

Isi release me UI bhi English kar diya aur Android edition jaisa look diya
(card title rails, rail nav with expanding labels, welcome card), aur **Play ab
pehle install karta hai phir launch** — user ko alag se Install dabana nahi padta.

## Portable zip / exe error (user report)

`app\<name>.cfg: No such file or directory` — zip ke **andar se** exe chalane par
aata hai (Windows zip ko temp me mount karta hai, saath ke `app/` + `runtime/`
folders wahan nahi hote). Fix: zip me top-level folder + `README-PEHLE-PADHO.txt`,
aur MSI recommend. Wo launcher ka bug nahi tha.

## Verification

CI runs (sab green):

- `41398822157` — compile + Windows package (pehla working build)
- `41400622255` — core unit tests (rules, placeholders, java mapping, args) + Xvfb smoke run
- `41401534155` — Fabric support ke saath compile + package + smoke

Released: **`desktop-v0.3.1`** → MSI (~100 MB) + portable zip (~99 MB),
self-contained JRE ke saath (user ko Java install karna nahi padta).

Runtime par abhi test nahi hua (sandbox me Windows/JDK nahi hai) — pehla real
launch test user ke PC par hoga.

## Next

1. Fabric / NeoForge / Forge install (version json merge)
2. Multiple instances + mods/shader manager
3. Modpack import (.mrpack, CurseForge zip)
4. Discord RPC, mobile ↔ PC sync
5. Launcher auto-update (GitHub releases)
