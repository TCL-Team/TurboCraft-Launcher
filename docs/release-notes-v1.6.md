# TurboCraft Launcher v1.6

**Android launcher for Minecraft Java Edition — arm64-v8a, armeabi-v7a, x86 and x86_64.**

## In-game typing works again

This is the headline fix: on Minecraft 26.3 and newer the game renders through an SDL window and
no longer reads characters from the old GLFW callback — it only accepts `SDL_EVENT_TEXT_INPUT`.
The launcher's on-screen keyboard was still sending every character down the GLFW path, and the
SDL path was gated behind a Java-side flag the game frequently never sets. The keyboard opened,
key presses registered, and the text field stayed empty: server address, chat, signs, anvil, book
titles, world names — all of it.

What changed:

- **Typed text is routed through the SDL text channel** whenever the game runs on the SDL window.
  Older render paths keep using the GLFW bridge, unchanged.
- **The launcher opens the native text-input channel before the keyboard takes over**, so nothing
  is silently dropped while the game has its own input closed (custom/modded input screens), and
  it restores the channel when the keyboard is dismissed.
- **Backspace and Enter follow the same route**, so editing behaves like a real keyboard.
- **No more IME tug-of-war.** The launcher's hidden input view and SDL's hidden input view were
  both fighting for the input method; while the launcher keyboard is open it now owns the IME and
  the game's own show/hide requests no longer steal it or slam it shut.
- **No filler spaces in your text.** The invisible 30-space padding inside the input field no
  longer leaks into the game when an IME rewrites the field.

## Social: creating a profile works

- Fixed: tapping **Create my profile** did nothing at all — no spinner, no error, no request. A
  second controller instance was created when the chat session arrived, and the shared lifecycle
  block then closed the *first* controller, cancelling its coroutine scope, which silently killed
  every later action. Each controller now owns its lifecycle and rebuilds its scope if cancelled.
- If the handle you want is taken, the app says so and offers a one-tap alternative
  (`@wolf` → `@wolf_47`).
- Reconnect, the create spinner, and the copyable **Connection details** panel are included.

## Everything since v1.3

- Party support: create, join, invite friends, party chat.
- Friends, direct messages and notifications backed by the project's own Supabase instance.
- New in-app legal documents (user agreement and privacy policy) in English, with a Hindi summary,
  replacing the inherited third-party copies.
- Launcher notice feed served from this repository; community link points at the project's
  contributors.
- Renderer config UI, MobileGlues 2.0.1 (needed for the new Minecraft shader pipeline), and a
  separate "Simple FPE" fallback renderer.
- Discord Rich Presence support when the app id is configured.

## Installing

1. Pick the APK for your device — almost every phone is **arm64-v8a**; the **universal** build
   contains all four architectures and is the safest choice if you are unsure.
2. If you tested an earlier build, uninstall it first so the old version cannot stick around.
3. Settings → About shows the version; this build reports **1.6 (versionCode 9)**.

All APKs are signed with the project's development key, so they install side by side with each
other but not with differently-signed builds.

## Known limitations

- Android 8.0 (API 26) or newer is required.
- This is a testing build: expect rough edges. Report them in the issue tracker — for chat or party
  problems include the copyable **Social → Connection details** text.
