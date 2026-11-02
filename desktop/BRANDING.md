# Naam / version badalna (branding)

Mobile edition me jaisa `gradle.properties` hai, waise hi PC par
**`desktop/gradle.properties`** hai. Sirf wahi file edit karo — baaki sab
build ke waqt khud ban jaata hai (`build/generated/brand/com/tcl/desktop/Brand.kt`).

```properties
launcher_app_name=TurboCraft Launcher      # UI me dikhne wala naam
launcher_name=TurboCraftLauncher           # exe / MSI / data folder (bina space)
launcher_short_name=TurboCraft             # logo ke initials (TC)
launcher_version_code=3                    # integer, har release par +1
launcher_version_name=0.3.0                # release version
url_home=https://github.com/...
launcher_vendor=TCL Team                   # Windows "Publisher"
```

Ek file badalne se ye sab badal jaata hai:

| Kahan | Kya badalta hai |
| --- | --- |
| Window title | `launcher_app_name` |
| Home screen hero + status chip | app name, `v<version>` |
| Sidebar logo initials | `launcher_short_name` |
| Data folder | `%APPDATA%\<launcher_name>\` (game, libraries, java, logs) |
| exe / MSI ka naam | `launcher_name` + `launcher_version_name` |
| MSI "Publisher" / description | `launcher_vendor` / app name |
| HTTP User-Agent | `<launcher_name>/<version> (desktop)` |
| Game ke args (`--launcherName`) | `launcher_name`, `launcher_version` |
| Settings → About | sab kuch |

⚠️ `launcher_name` mat badlo baad me — data folder ka naam wahi hota hai, purana
game data naye folder me nahi milega (rename kar dena: `%APPDATA%\PuranaNaam` →
`%APPDATA%\NayaNaam`).

`upgradeUuid` (build.gradle.kts me) **kabhi na badlo** — warna installer update ki
jagah naya install kar dega.

Release tag bhi isi version se match karo: `desktop-v0.3.0`.
