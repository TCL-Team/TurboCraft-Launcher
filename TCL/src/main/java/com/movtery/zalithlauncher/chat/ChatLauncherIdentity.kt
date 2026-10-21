/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery and contributors
 * Licensed under the GNU GPL v3 or later.
 */

package com.movtery.zalithlauncher.chat

import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.isMicrosoftAccount
import java.util.UUID

/**
 * A chat identity derived from the currently selected launcher account. Microsoft profiles use
 * their Mojang UUID and are verified by the Supabase Edge Function. Offline accounts use the
 * launcher's locally generated account UUID because offline usernames are not proof of ownership.
 */
internal data class ChatLauncherIdentity(
    val localAccountId: String,
    val identityType: String,
    val identityId: String,
    val minecraftAccessToken: String?,
    val accountName: String,
    val suggestedUsername: String
) {
    companion object {
        fun from(account: Account): ChatLauncherIdentity {
            val localAccountId = normalizeLocalId(account.uniqueUUID)
            val isMicrosoft = account.isMicrosoftAccount()
            val identityId = if (isMicrosoft) {
                account.profileId.replace("-", "").lowercase().also { id ->
                    if (!MINECRAFT_PROFILE_ID.matches(id)) {
                        throw ChatApiException("The selected Microsoft account has no valid Minecraft profile. Refresh it from Account Manager and try again.")
                    }
                }
            } else {
                localAccountId
            }
            val accessToken = account.accessToken
                .takeIf { isMicrosoft && it.isNotBlank() && it != "0" }

            return ChatLauncherIdentity(
                localAccountId = localAccountId,
                identityType = if (isMicrosoft) "microsoft" else "offline",
                identityId = identityId,
                minecraftAccessToken = accessToken,
                accountName = account.username,
                suggestedUsername = suggestedUsername(account.username, localAccountId)
            )
        }

        private fun normalizeLocalId(value: String): String = runCatching {
            UUID.fromString(value).toString().lowercase()
        }.getOrElse {
            UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString().lowercase()
        }

        private fun suggestedUsername(name: String, localAccountId: String): String {
            val cleaned = name.trim().lowercase()
                .map { character ->
                    if (character in 'a'..'z' || character in '0'..'9' || character == '_' || character == '.') character
                    else '_'
                }
                .joinToString("")
                .trim('_', '.')
                .take(20)
            return cleaned.takeIf { it.length >= 3 } ?: "player_${localAccountId.take(5)}"
        }

        private val MINECRAFT_PROFILE_ID = Regex("^[0-9a-f]{32}$")
    }
}
