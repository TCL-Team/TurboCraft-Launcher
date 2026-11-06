/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery and contributors
 * Licensed under the GNU GPL v3 or later.
 */

package com.movtery.zalithlauncher.chat

import java.util.concurrent.TimeUnit

/**
 * Social layer models: presence, parties and party chat.
 * These sit on top of the chat schema (Supabase, RLS protected) and are shared by the
 * mobile social hub UI.
 */

/** Someone in the friends list, enriched with what they are playing. */
data class SocialFriendEntry(
    val friend: ChatFriend,
    val pinned: Boolean = false
) {
    val userId: String get() = friend.userId
    val username: String get() = friend.username
    val displayName: String get() = friend.displayName
    val conversationId: String get() = friend.conversationId

    fun presence(nowMillis: Long = System.currentTimeMillis()): SocialPresence =
        socialPresence(friend.lastActiveAtMillis, nowMillis)

    fun presenceSummary(nowMillis: Long = System.currentTimeMillis()): String {
        val presence = presence(nowMillis)
        return when (presence) {
            SocialPresence.ONLINE -> "Online · ready to play"
            SocialPresence.IDLE -> "Away"
            SocialPresence.OFFLINE -> lastSeenText(friend.lastActiveAtMillis, nowMillis)
        }
    }
}

enum class SocialPresence { ONLINE, IDLE, OFFLINE }

private const val SOCIAL_ONLINE_WINDOW_MS = 2 * 60 * 1000L
private const val SOCIAL_IDLE_WINDOW_MS = 15 * 60 * 1000L

fun socialPresence(lastActiveAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): SocialPresence {
    val lastActive = lastActiveAtMillis ?: return SocialPresence.OFFLINE
    if (lastActive <= 0L) return SocialPresence.OFFLINE
    val age = (nowMillis - lastActive).coerceAtLeast(0L)
    return when {
        age <= SOCIAL_ONLINE_WINDOW_MS -> SocialPresence.ONLINE
        age <= SOCIAL_IDLE_WINDOW_MS -> SocialPresence.IDLE
        else -> SocialPresence.OFFLINE
    }
}

fun lastSeenText(lastActiveAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String {
    if (lastActiveAtMillis == null || lastActiveAtMillis <= 0L) return "Offline"
    val diff = (nowMillis - lastActiveAtMillis).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 2L -> "Last seen just now"
        minutes < 60L -> "Last seen ${minutes}m ago"
        hours < 24L -> "Last seen ${hours}h ago"
        days == 1L -> "Last seen yesterday"
        days < 30L -> "Last seen ${days}d ago"
        else -> "Offline"
    }
}

/** One member of a party. */
data class PartyMember(
    val userId: String,
    val username: String,
    val displayName: String,
    val isHost: Boolean,
    val lastActiveAtMillis: Long? = null,
    val joinedAtMillis: Long = 0L
) {
    fun presence(nowMillis: Long = System.currentTimeMillis()): SocialPresence =
        socialPresence(lastActiveAtMillis, nowMillis)
}

/** A "play together" party: host + up to 16 friends, one shared instance and optional server. */
data class SocialParty(
    val id: String,
    val hostId: String,
    val title: String,
    val versionId: String? = null,
    val instanceName: String? = null,
    val mcVersion: String? = null,
    val loader: String? = null,
    val modpackSource: String? = null,
    val modpackId: String? = null,
    val modpackIconUrl: String? = null,
    val serverAddress: String? = null,
    val maxMembers: Int = 4,
    val isPublic: Boolean = false,
    val inviteCode: String = "",
    val createdAtMillis: Long = 0L,
    val expiresAtMillis: Long = 0L,
    val members: List<PartyMember> = emptyList()
) {
    val memberCount: Int get() = members.size
    val isFull: Boolean get() = memberCount >= maxMembers
    fun isHost(userId: String?): Boolean = userId != null && hostId == userId

    fun member(userId: String?): PartyMember? = members.firstOrNull { it.userId == userId }

    fun hostMember(): PartyMember? = members.firstOrNull { it.isHost } ?: members.firstOrNull()

    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        expiresAtMillis in 1..nowMillis

    fun remainingText(nowMillis: Long = System.currentTimeMillis()): String {
        if (expiresAtMillis <= 0L) return "Active"
        val remaining = expiresAtMillis - nowMillis
        if (remaining <= 0L) return "Ended"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
        val hours = TimeUnit.MILLISECONDS.toHours(remaining)
        return when {
            hours >= 1L -> {
                val restMinutes = minutes - hours * 60
                if (restMinutes > 0L) "${hours}h ${restMinutes}m left" else "${hours}h left"
            }
            minutes >= 1L -> "${minutes}m left"
            else -> "<1m left"
        }
    }

    /** "1.21.4 · Fabric · My Instance" style subtitle. */
    fun summaryTag(): String {
        val parts = mutableListOf<String>()
        mcVersion?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        loader?.takeIf { it.isNotBlank() && !it.equals("vanilla", ignoreCase = true) }?.let { loaderName ->
            parts.add(loaderName.replaceFirstChar { it.uppercase() })
        }
        instanceName?.takeIf { it.isNotBlank() && it != mcVersion }?.let { parts.add(it) }
        return if (parts.isEmpty()) "Minecraft session" else parts.joinToString(" · ")
    }

    fun copyWithMembers(newMembers: List<PartyMember>): SocialParty = copy(members = newMembers)
}

/** An invite someone sent you (or that you sent and is still pending). */
data class PartyInvite(
    val id: String,
    val partyId: String,
    val fromId: String,
    val fromUsername: String,
    val fromDisplayName: String,
    val partyTitle: String,
    val mcVersion: String? = null,
    val loader: String? = null,
    val instanceName: String? = null,
    val maxMembers: Int = 4,
    val memberCount: Int = 0,
    val expiresAtMillis: Long = 0L,
    val createdAtMillis: Long = 0L
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        expiresAtMillis in 1..nowMillis

    fun summaryTag(): String {
        val parts = mutableListOf<String>()
        mcVersion?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        loader?.takeIf { it.isNotBlank() && !it.equals("vanilla", ignoreCase = true) }?.let { parts.add(it) }
        instanceName?.takeIf { it.isNotBlank() && it != mcVersion }?.let { parts.add(it) }
        parts.add("$memberCount/$maxMembers")
        return parts.joinToString(" · ")
    }
}

/** A message inside a party's group chat. */
data class PartyMessage(
    val id: String,
    val partyId: String,
    val senderId: String,
    val senderName: String,
    val senderUsername: String,
    val body: String,
    val createdAtMillis: Long = 0L,
    val pending: Boolean = false
)

/** Everything the social hub needs to render the parties tab. */
data class PartyDashboard(
    val parties: List<SocialParty> = emptyList(),
    val publicParties: List<SocialParty> = emptyList(),
    val invites: List<PartyInvite> = emptyList(),
    val sentInvites: List<PartyInvite> = emptyList()
) {
    fun party(partyId: String?): SocialParty? = parties.firstOrNull { it.id == partyId }
}

/** Composer values used when creating a party. */
data class PartyDraft(
    val title: String = "",
    val versionId: String? = null,
    val instanceName: String? = null,
    val mcVersion: String? = null,
    val loader: String? = null,
    val serverAddress: String = "",
    val maxMembers: Int = 4,
    val isPublic: Boolean = false
)
