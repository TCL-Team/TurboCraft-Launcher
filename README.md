# TurboCraft Launcher

<p align="center">
  <img src="res/rawdefault_background.mp4" width="700">
</p>

<p align="center">
A modern Minecraft: Java Edition Launcher for Android.
</p>

<p align="center">
Fast • Beautiful • Powerful • Open Source
</p>

---

## ✨ Overview

TurboCraft Launcher is a modern Android launcher designed for Minecraft: Java Edition.

Built with Jetpack Compose and powered by a highly customized launcher core, TurboCraft Launcher focuses on delivering a smooth, customizable and premium experience while keeping compatibility with the Java Edition ecosystem.

Whether you're playing Vanilla, Fabric, Forge or NeoForge, TurboCraft Launcher provides a clean interface with advanced customization options and performance improvements.

---

# Features

- 🎥 Animated Video Background UI
- 🧭 First-Run Interactive Guide
- 🚀 Performance Improvements
- 🎨 Modern Material Design UI
- ☕ Multiple Java Runtime Support
- 📦 Fabric / Forge / NeoForge
- 👤 Offline Accounts
- 🎭 Cape & Skin Support
- 🧍 Live 3D Player Model Preview (PetLib)
- ⚙️ Advanced Rendering Settings
- 📁 Import / Export Settings
- 🔥 Highly Customizable
- 🛠 Continuous Improvements

---

# Downloads

Download the latest release from the Releases page.

---

# Building

```bash
git clone https://github.com/TCL-Team/TurboCraft-Launcher
```

Open in Android Studio and build.

---

## Credits

TurboCraft Launcher builds upon the work of the open-source Minecraft launcher community.

For complete acknowledgements and third-party credits, see the in-app **Acknowledgements** page.

Special thanks to all developers, contributors and maintainers whose work made this project possible.

---

# License

TurboCraft Launcher is licensed under GPL-3.0.

See LICENSE for more information.


## Testing builds (pre-release)

Every build is published as a GitHub **pre-release** so an unfinished build is never handed to
normal users:

* `preview` — rolling pre-release rebuilt on every push to `main` / feature branches
  (arm64 APK, install this one to test new work);
* `vX.Y` tags — versioned pre-releases with all ABIs. In each one:

  | File | Use it for |
  | --- | --- |
  | `TurboCraft-Launcher-<version>-universal.apk` | any device, picks the right architecture itself |
  | `TurboCraft-Launcher-<version>-arm64-v8a.apk` | almost every modern phone |
  | `TurboCraft-Launcher-<version>-armeabi-v7a.apk` | old 32-bit ARM devices |
  | `TurboCraft-Launcher-<version>-x86.apk` / `-x86_64.apk` | emulators and x86 tablets / Chromebooks |
  | `SHA256SUMS.txt` | verify a download |

  Publishing all of them is automated: **Actions → Release APKs → Run workflow**, with the tag
  (for example `v1.6`) and whether it is a pre-release. The version comes from
  `TCL/gradle.properties`, so bump `launcher_version_name` / `launcher_version_code` first.

Report anything that breaks in the issue tracker, and include the copyable
**Social → Connection details** text if a chat/party problem appears.
