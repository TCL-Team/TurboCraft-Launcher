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
