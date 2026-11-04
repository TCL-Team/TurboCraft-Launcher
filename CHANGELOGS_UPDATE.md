## TurboCraft Launcher V1.5 (pre-release)

### Social: profile creation actually works now

- **Root cause found.** The screen created a second controller object when the chat session
  arrived, and the shared lifecycle block then called `close()` on the *first* controller. That
  cancelled its coroutine scope, so every later action — including "Create my profile" — silently
  did nothing: no spinner, no error, no request. Each controller now has its own lifecycle, and a
  controller rebuilds its scope if it was ever cancelled.
- If the handle you type is already used by someone else, the app now says so and offers a
  one-tap alternative (`@wolf` → `@wolf_47`).
- Reconnect, the create spinner, and the copyable Connection details panel from 1.4 are included.

### Note when testing

Settings → About shows the app version; this build reports **1.5 (versionCode 8)**. If you still
see the old behaviour, the old APK is installed — uninstall it first, then install the preview APK.

## TurboCraft Launcher V1.4 (pre-release)

### Social fixes

- Fixed the "Claim your social handle" screen getting stuck: profile creation now goes through
  a server-side `ensure_chat_profile` routine that is idempotent, and the friend list loading can no
  longer throw you back to that screen.
- The chat session now takes its user id from the signed token itself, so a mismatched session can no
  longer make every profile/message write fail.
- Expired or stale social sessions heal themselves once, and there is a **Reconnect** button on the
  Social screens.
- "Create my profile" now shows a spinner while working.
- New **Connection details** panel under Social (error text, HTTP status, server response, copyable)
  so a bug report contains the exact cause instead of a screenshot of a blank screen.

### Welcome screen

- The first-run screen no longer shows ZalithLauncher's user agreement and privacy policy.
  TurboCraft Launcher now ships its own **User Agreement** and **Privacy Policy** (English, with a
  Hindi summary), and asks you to accept the new version once.
- Open-source attribution (PojavLauncher, Zalith Launcher 2, GPL v3) is kept inside the agreement, as
  the licence requires.
- Rebranded the team / links dialog.

### Releases

- All builds are published as GitHub **pre-releases** so a broken build is never offered to normal
  users. Every push now refreshes the rolling `preview` pre-release with an arm64 test APK.

## TurboCraft Launcher V1.3 Update

### Social hub (new)

- The Chat tab is now a full **Social** hub: friends, direct messages, parties and requests in one place.
- Friends are grouped **Online / Offline** with live presence dots (heartbeat every minute), "last seen" text and a one-tap message shortcut.
- **Parties**: host a party on any installed instance (version, loader and instance name are attached automatically), share a 6-character invite code, invite friends from the member list, and chat together in the party.
- Party controls: host can extend a party by an hour, remove members or end it; leaving as host transfers the host role to the earliest member. Parties expire automatically after 12 hours.
- **Discover** public parties and join them with one tap, or join a private party with its code.
- Party chat and direct messages support editing/deleting your own messages; party chat is optimistic (your message appears instantly).
- Requests tab combines incoming friend requests, party invites (accept/decline) and the add-friend box.
- Profile editing (display name, @handle, bio) and recovery-code management are available from the Social header.

## TurboCraft Launcher V1.2 Update

### Microsoft Login

- Fixed an issue in the Microsoft account sign-in flow to make launcher authentication more reliable.
- Continue using your existing Microsoft account; this fix does not require creating a separate launcher account.

### User Interface

- Refreshed the launcher interface for a cleaner, more consistent experience.
- Updated navigation and layouts across the home screen, sidebar, and settings screens.

### Community Chat

- Added an in-launcher community chat that uses the currently selected Microsoft or offline launcher account as its identity.
- Create a chat profile, find and add friends, manage friend requests, and exchange direct messages from the launcher.
- Edit your chat username, display name, and bio from your profile in Chat.
- A recovery-code flow lets returning users restore an existing Chat ID and its profile, friends, requests, and message history after reinstalling. Save the recovery code somewhere safe before reinstalling. Offline accounts may need this code if their local launcher-account data changes.

### Renderer Compatibility

- Removed MobileGlues from the launcher's built-in renderer options because it can cause shader-combination errors in Minecraft 26.3 and later.
- MobileGlues has not been discontinued, but it must now be installed and used separately as a plugin if you still need it; it is no longer available as a built-in renderer.

### Fixes
- Fixed white flash on launcher startup (splash screen background now black)
- Fixed default background video being hidden behind an opaque UI layer
- Fixed UI cards/buttons staying fully opaque instead of showing background video through them

### Performance & Optimization
- Added large heap support for smoother memory handling with higher RAM allocations
- Improved overall launcher performance and optimization for a smoother experience
- Optimized UI and background handling for better responsiveness

### Other
- Removed some broken GitHub link button in the About page
