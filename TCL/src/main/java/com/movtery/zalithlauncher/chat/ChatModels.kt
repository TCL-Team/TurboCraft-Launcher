/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery and contributors
 * Licensed under the GNU GPL v3 or later.
 */

package com.movtery.zalithlauncher.chat

import com.google.gson.JsonObject

data class ChatAuthSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    /** Null only for a session created by the older email/password chat flow. */
    val launcherAccountId: String? = null
)

data class ChatProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String = "",
    val lastActiveAtMillis: Long? = null
)

data class ChatFriend(
    val userId: String,
    val username: String,
    val displayName: String,
    val conversationId: String,
    val lastActiveAtMillis: Long? = null
)

enum class ChatPresence {
    ONLINE,
    OFFLINE,
    INACTIVE
}

fun ChatFriend.presence(nowMillis: Long = System.currentTimeMillis()): ChatPresence =
    chatPresence(lastActiveAtMillis, nowMillis)

fun chatPresence(lastActiveAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): ChatPresence {
    val lastActive = lastActiveAtMillis ?: return ChatPresence.INACTIVE
    val age = (nowMillis - lastActive).coerceAtLeast(0L)
    return when {
        age <= ONLINE_WINDOW_MILLIS -> ChatPresence.ONLINE
        age <= INACTIVE_AFTER_MILLIS -> ChatPresence.OFFLINE
        else -> ChatPresence.INACTIVE
    }
}

private const val ONLINE_WINDOW_MILLIS = 75_000L
private const val INACTIVE_AFTER_MILLIS = 2 * 24 * 60 * 60 * 1000L

data class ChatFriendRequest(
    val id: String,
    val senderId: String,
    val username: String,
    val displayName: String,
    val createdAt: String
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val createdAt: String,
    val editedAt: String? = null
)

data class ChatNotification(
    val id: String,
    val senderId: String,
    val senderUsername: String,
    val senderDisplayName: String,
    val conversationId: String,
    val messageId: String,
    val preview: String,
    val createdAt: String,
    val senderLastActiveAtMillis: Long? = null
)

data class ChatDashboard(
    val friends: List<ChatFriend>,
    val incomingRequests: List<ChatFriendRequest>,
    val unreadNotifications: List<ChatNotification>
)

internal data class ChatRealtimeEvent(
    val table: String,
    val eventType: String,
    val record: JsonObject
)

enum class ChatPanel {
    CHATS,
    REQUESTS,
    NOTIFICATIONS,
    ADD_FRIEND
}
