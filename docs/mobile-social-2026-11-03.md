# Social & Parties (mobile launcher)

Date: 2026-11-03 · Scope: `TCL` (Android). The PC/desktop launcher was removed from the repo in this change set.

## What players get

The **Social** tab in the launcher rail opens the social hub.

| Tab | Contents |
| --- | --- |
| Friends | Friends grouped Online / Offline with presence dots, "Invite" (when you are in a party) and "Message" shortcuts, plus the current party banner with its 6-character code. |
| Chats | Direct messages per friend. Private 1:1 thread with timestamps, edit and delete for your own messages. |
| Parties | The party you are in (members, host, remaining time, invite box, party chat, `+1h`, `End` / `Leave`), a create-party action and a **Discover parties** list of public parties. |
| Requests | Incoming party invites, incoming friend requests with Accept / Decline, and an add-friend box. |

Extra details:

* Presence heartbeat every 60 s (`chat_profiles.last_active_at`), Online ≤ 2 min, Away ≤ 15 min, otherwise "Last seen …".
* Party codes are 6 characters from an unambiguous alphabet (`ABCDEFGHJKLMNPQRSTUVWXYZ23456789`), unique per party, copy-to-clipboard.
* Party chat is optimistic: your message appears instantly and is replaced by the server row when the insert returns.
* Creating a party picks an **installed instance** (name, Minecraft version and loader are read from `VersionsManager`), optional server address, max players (2-16) and public/private.
* Host leaving a party transfers the host role to the earliest member; the last member leaving deletes the party. Parties expire after 12 h and any member can extend by `+1h`.

## Backend

Migration: [`supabase/migrations/20261103090000_chat_parties.sql`](../supabase/migrations/20261103090000_chat_parties.sql) (already applied to the TurboCraft project).

Tables (all RLS-enabled, no direct inserts/deletes except party chat messages):

* `chat_parties` — host, title, instance/version/loader snapshot, server address, capacity, visibility, invite code, expiry.
* `chat_party_members` — membership + `is_host`.
* `chat_party_invites` — pending/accepted/declined/cancelled, one pending invite per (party, player).
* `chat_party_messages` — party group chat (members only).

RPCs (`security definer`, `grant execute … to authenticated`): `create_chat_party`, `join_chat_party_by_code`, `join_chat_party`, `leave_chat_party`, `delete_chat_party`, `refresh_chat_party`, `invite_to_chat_party`, `respond_chat_party_invite`, `kick_chat_party_member`.

Rules enforced server-side:

* Only friends can be invited (no spam from strangers).
* Private parties are joinable only via invite or code.
* Capacity, expiry and host-only actions are validated in SQL, not in the app.
* Realtime publication includes party messages, invites and members.

## App code

* `chat/SocialPartyModels.kt` — presence helpers, `SocialParty`, `PartyMember`, `PartyInvite`, `PartyMessage`, `PartyDashboard`, `PartyDraft`.
* `chat/SupabaseChatRepository.kt` — party dashboard/messages/invites queries and RPC wrappers.
* `chat/SocialPartiesController.kt` — `PartyUiState` + actions, 5 s party-chat poll, 15 s dashboard poll, presence heartbeat.
* `chat/ChatController.kt` — now also sends a presence heartbeat while running.
* `ui/screens/content/SocialHubScreen.kt` — the whole hub UI (header, tabs, party panel, DM thread, party thread, dialogs).
* `ui/screens/main/MainScreen.kt` — rail entry "Social" → `NormalNavKey.Chat` → `SocialHubScreen`; the old community links dialog stays as "Links".

## Verification

Migration applied to the live TurboCraft project (`qvjanukzamquosjxhxnc`) and exercised with
impersonated `authenticated` sessions:

* `create_chat_party` → party + host membership + unique 6-char code ✅
* `join_chat_party_by_code` as a second user → membership, code lookup ✅
* RLS: outsider cannot read another party's members/messages; public parties are readable ✅
* party chat insert by a member ✅
* host leave → host role transferred to the earliest member ✅
* new host `delete_chat_party` → party removed ✅

**Bugs caught during that run (fixed in this commit):**

* `chat_party_members` select policy referenced itself → `42P17 infinite recursion`.
  Replaced with a `SECURITY DEFINER` helper `chat_is_party_member(uuid, uuid)`.
* `create_chat_party` had a plpgsql variable `party_id` shadowing the `party_id` column.

Android compile + release build (`v1.3`, all five ABIs) passed on CI.

## Known follow-ups

* Typing indicators and read receipts for parties.
* Player-count/“join server” deep link from a party (currently informational).
* Party chat attachments (screenshot/clip sharing) — needs Supabase Storage access policy.
