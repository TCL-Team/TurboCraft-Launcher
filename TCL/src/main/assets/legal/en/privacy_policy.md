# TurboCraft Launcher Privacy Policy

**App:** TurboCraft Launcher (also shown as **TCL**)
**Developer:** TCL Team (**we**, **us**, **our**)
**Version:** 2.0
**Effective and last updated:** November 4, 2026
**Contact:** https://github.com/TCL-Team/TurboCraft-Launcher/issues

This policy explains what TurboCraft Launcher stores on your device and what it sends to other
services when you use it. It applies to the official builds of the app distributed from our GitHub
releases page. It does not cover modified builds or services run by other people.

## 1. The short version

- The launcher has **no advertising SDK, no analytics SDK, and no tracking**.
- Everything needed to launch the game (accounts, versions, mods, settings, logs, saves) stays on
  your device, except where a service below must receive it to do its job.
- You do not need an account with us to play single-player. A **launcher account** (Microsoft or
  offline) is required for the built-in Social features, and only for those.
- We never sell your data, and we never use your game files to build profiles.

## 2. Information stored on your device

Depending on which features you use, the app stores the following locally in its private storage:

- **Launcher accounts.** For a Microsoft account we keep the Minecraft profile name, UUID, and the
  authentication tokens issued by Microsoft/Xbox Live so you do not have to sign in every time. For
  an offline account we keep the name and a locally generated identifier. Account data is stored in
  the app's private area and is never uploaded to us.
- **Social session.** If you use Social, the chat session token is encrypted with a key held in the
  Android Keystore (AES-GCM) before it is written to disk. You can remove it at any time with
  **Social → Reconnect** or by clearing app data.
- **Game data and settings.** Installed versions, mods, modpacks, resource packs, shaders, worlds,
  screenshots, control layouts, launcher preferences, Java runtime files, caches, and logs.
- **Social profile content.** Your handle, display name, bio, friends, direct messages, party
  details and party messages are stored on our Social backend (section 4), not on your device only.

You can delete local data by removing individual files in the launcher's file manager, or by
clearing the app's storage in Android settings / uninstalling the app. Uninstalling does **not**
delete your Social profile — use the contact address above if you want it removed.

## 3. Microsoft, Xbox Live and Minecraft

Signing in with a Microsoft account uses Microsoft's **Live Connect device-code flow** (the same flow
the launcher has always used for Microsoft accounts). The app:

1. asks Microsoft for a short code and a verification link, which you open in your browser;
2. polls Microsoft until you approve the sign-in;
3. exchanges the Microsoft ticket for an Xbox Live token, then an XSTS token, then a Minecraft
   services token and your Minecraft profile.

Microsoft, Xbox Live and Mojang/Microsoft handle that data under their own privacy statements. We
never see your Microsoft password. The tokens obtained in step 3 are stored on your device only, and
are sent only to Microsoft/Xbox/Minecraft endpoints and, for the Microsoft identity check described
in section 4, to our Social backend as described there.

## 4. Social features (friends, chat, parties)

Social is optional. It is powered by a **Supabase** project operated by us (database, realtime and
edge functions, hosted by Supabase). When you use Social:

- **Identity.** Your launcher account is mapped to a Social identity. For an offline account that
  identity is a locally generated UUID; for a Microsoft account it is your Minecraft UUID. To prove
  the Microsoft identity, the app sends your Minecraft access token to our backend once, and our
  backend asks Microsoft's `api.minecraftservices.com` whether the token belongs to that UUID. The
  token is not stored by our backend.
- **Profile.** Your handle, display name, bio, and a "last active" timestamp (refreshed roughly every
  minute while the app is open) are stored so friends can see whether you are online.
- **Social content.** Friend requests, friendships, direct messages, party metadata (title, chosen
  instance name, Minecraft version, loader, optional server address, party code, expiry), party
  memberships and party chat messages are stored on the backend so they can be delivered to the
  people you are talking to.
- **Visibility.** Direct messages and party chats are readable only by their participants; access is
  enforced by row-level security in the database. Public parties are discoverable by other signed-in
  users. Your IP address is visible to Supabase as the hosting provider, and we use a one-way hash of
  it for abuse rate-limiting on session creation.
- **No third-party sharing.** Social data is not shared with advertisers, data brokers or any other
  third party.

You can stop using Social at any time; it never affects single-player launching.

## 5. Third-party services the app contacts

The launcher talks directly to these services when the related feature is used. Each one receives at
least your IP address and basic request information, and applies its own privacy policy:

| Service | When | Purpose |
| --- | --- | --- |
| Microsoft / Xbox Live / Minecraft services | Signing in, launching | Authentication, ownership check, profile |
| Mojang / Minecraft download servers (and optional mirrors) | Installing versions, assets, libraries | Game files |
| Modrinth, CurseForge, FTB and similar platforms | Browsing and downloading mods/modpacks | Content metadata and files |
| Fabric, Quilt, Forge, NeoForge, LiteLoader metadata servers | Installing loaders | Loader metadata and libraries |
| Adoptium / Mojang Java runtimes | Installing Java | Runtime downloads |
| GitHub (this repository and its releases) | Update checks, downloads | Launcher updates |
| Terracotta / multiplayer relay services | Online multiplayer | Session relay |
| Discord | Only if you enable Rich Presence | Shows your game activity on Discord |
| Supabase | Social features | Database, chat, parties |

Some download sources can be changed in Settings to a mirror or to an HTTP address; those operators
then handle your request under their own terms.

## 6. Logs

The launcher writes logs (game output, download errors, crash information) to its private folder so
you can debug problems. Logs are never uploaded automatically. If you choose to share a log — for
example in a GitHub issue — it may contain your account name, file paths, server addresses or your
public IP address, so review it before posting.

## 7. Permissions

- **Internet / network state** — downloads, authentication, Social.
- **Storage access** — importing/exporting mods, worlds and settings, and choosing a game folder.
- **Notifications** — optional Social message alerts and download progress.

## 8. Children

The launcher is not directed at children under 13. Microsoft, Xbox Live and Minecraft have their own
age requirements. Social features let players exchange messages; if a minor uses the app, a parent or
guardian should supervise that use.

## 9. Your choices and rights

- Delete local data: remove files in the launcher, or clear app data.
- Reset Social: **Social → Reconnect** (new session), or ask us to delete your Social profile.
- Stop update checks: the launcher checks GitHub releases when you open Settings → About.
- Depending on where you live (for example under GDPR or India's DPDP Act) you may have the right to
  access, correct, or delete your Social data, or to object to processing. Contact us at the address
  above and we will respond within a reasonable period.

## 10. Security

Traffic to our Social backend and to Microsoft uses HTTPS. The Social session token on your device is
encrypted with an Android Keystore key. No system is perfectly secure: if you believe your account
has been compromised, remove the account in the launcher, change your Microsoft password, and
contact us.

## 11. Changes

If this policy changes materially we update the version above. The app re-displays the documents at
startup and asks you to accept them again; if you decline, the app will not continue. Continued use
after an update means you accept the then-current policy.

## 12. Open-source components

TurboCraft Launcher is built on open-source software, including PojavLauncher, Zalith Launcher 2,
LWJGL, the Android Open Source Project and many libraries. Those projects' licences (for example the
GNU GPL v3) apply to their code. Their authors are not responsible for this app, and this policy does
not describe their own websites or services.

## 13. हिंदी सारांश (Hindi summary)

TurboCraft Launcher आपके फ़ोन में कोई विज्ञापन या ट्रैकिंग नहीं जोड़ता। Microsoft लॉगिन के टोकन और
गेम/मॉड/सेटिंग्स की जानकारी आपके डिवाइस पर ही रहती है। Social (दोस्त, चैट, पार्टी) वैकल्पिक है और
उसका डेटा हमारे Supabase सर्वर पर सुरक्षित तरीके से रखा जाता है — उसे केवल आप और आपके दोस्त देख सकते
हैं। हम आपका डेटा किसी को बेचते नहीं। आप कभी भी Social बंद कर सकते हैं या अपनी प्रोफ़ाइल हटाने के लिए
ऊपर दिए GitHub लिंक पर संपर्क कर सकते हैं। पूरी जानकारी अंग्रेज़ी में ऊपर दी गई है।
