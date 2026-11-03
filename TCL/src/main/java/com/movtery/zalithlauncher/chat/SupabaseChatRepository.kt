/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery and contributors
 * Licensed under the GNU GPL v3 or later.
 */

package com.movtery.zalithlauncher.chat

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.Closeable
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

internal class SupabaseChatRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    suspend fun createLauncherSession(identity: ChatLauncherIdentity): ChatAuthSession {
        val body = JsonObject().apply {
            addProperty("identity_type", identity.identityType)
            addProperty("identity_id", identity.identityId)
            identity.minecraftAccessToken?.let { addProperty("minecraft_access_token", it) }
        }
        val request = Request.Builder()
            .url(ChatBackendConfig.CHAT_ACCOUNT_SESSION_FUNCTION)
            .withPublicKey()
            .header("Authorization", "Bearer ${ChatBackendConfig.PUBLISHABLE_KEY}")
            .post(body.toString().jsonBody())
            .build()
        return sessionFrom(parseObject(execute(request)))
            .copy(launcherAccountId = identity.localAccountId)
    }

    suspend fun recoverLauncherSession(
        identity: ChatLauncherIdentity,
        username: String,
        recoveryCode: String
    ): ChatAuthSession {
        val body = JsonObject().apply {
            addProperty("action", "recover")
            addProperty("username", username)
            addProperty("recovery_code", recoveryCode)
            addProperty("identity_type", identity.identityType)
            addProperty("identity_id", identity.identityId)
            identity.minecraftAccessToken?.let { addProperty("minecraft_access_token", it) }
        }
        val request = Request.Builder()
            .url(ChatBackendConfig.CHAT_ACCOUNT_RECOVERY_FUNCTION)
            .withPublicKey()
            .header("Authorization", "Bearer ${ChatBackendConfig.PUBLISHABLE_KEY}")
            .post(body.toString().jsonBody())
            .build()
        return sessionFrom(parseObject(execute(request)))
            .copy(launcherAccountId = identity.localAccountId)
    }

    suspend fun setRecoveryCode(session: ChatAuthSession, recoveryCode: String) {
        val body = JsonObject().apply {
            addProperty("action", "set")
            addProperty("recovery_code", recoveryCode)
        }
        val request = Request.Builder()
            .url(ChatBackendConfig.CHAT_ACCOUNT_RECOVERY_FUNCTION)
            .withPublicKey()
            .header("Authorization", "Bearer ${session.accessToken}")
            .post(body.toString().jsonBody())
            .build()
        execute(request)
    }

    suspend fun ensureFreshSession(session: ChatAuthSession): ChatAuthSession {
        if (session.expiresAtMillis > System.currentTimeMillis() + SESSION_REFRESH_MARGIN_MS) return session
        if (session.refreshToken.isBlank()) throw ChatApiException("Your chat session expired. Reopen Chat with the selected launcher account to reconnect.")

        val body = JsonObject().apply { addProperty("refresh_token", session.refreshToken) }
        val response = execute(
            Request.Builder()
                .url("${authBase()}/token?grant_type=refresh_token")
                .withPublicKey()
                .post(body.toString().jsonBody())
                .build()
        )
        return sessionFrom(parseObject(response), fallbackUserId = session.userId)
            .copy(launcherAccountId = session.launcherAccountId)
    }

    suspend fun getOwnProfile(session: ChatAuthSession): ChatProfile? {
        val url = restUrl("chat_profiles")
            .addQueryParameter("select", "id,username,display_name,bio,last_active_at")
            .addQueryParameter("id", "eq.${session.userId}")
            .addQueryParameter("limit", "1")
            .build()
        return parseArray(execute(authorizedRequest(url.toString(), session).build()))
            .firstOrNull()?.asJsonObject?.toProfile()
    }

    suspend fun createProfile(session: ChatAuthSession, username: String, displayName: String): ChatProfile {
        val body = JsonObject().apply {
            addProperty("id", session.userId)
            addProperty("username", username)
            addProperty("display_name", displayName.ifBlank { username })
            addProperty("bio", "")
        }
        val url = restUrl("chat_profiles")
            .addQueryParameter("select", "id,username,display_name,bio,last_active_at")
            .build()
        val request = authorizedRequest(url.toString(), session)
            .post(body.toString().jsonBody())
            .header("Prefer", "return=representation")
            .build()
        return parseArray(execute(request)).firstOrNull()?.asJsonObject?.toProfile()
            ?: throw ChatApiException("Could not create your chat profile.")
    }

    suspend fun updateProfile(
        session: ChatAuthSession,
        username: String,
        displayName: String,
        bio: String
    ): ChatProfile {
        val body = JsonObject().apply {
            addProperty("username", username)
            addProperty("display_name", displayName)
            addProperty("bio", bio)
        }
        val url = restUrl("chat_profiles")
            .addQueryParameter("id", "eq.${session.userId}")
            .addQueryParameter("select", "id,username,display_name,bio,last_active_at")
            .build()
        val request = authorizedRequest(url.toString(), session)
            .patch(body.toString().jsonBody())
            .header("Prefer", "return=representation")
            .build()
        return parseArray(execute(request)).firstOrNull()?.asJsonObject?.toProfile()
            ?: throw ChatApiException("Could not update your chat profile.")
    }

    suspend fun loadDashboard(session: ChatAuthSession): ChatDashboard {
        val uid = session.userId
        val friendshipUrl = restUrl("chat_friendships")
            .addQueryParameter("select", "user_a,user_b,conversation_id,created_at")
            .addQueryParameter("or", "(user_a.eq.$uid,user_b.eq.$uid)")
            .addQueryParameter("order", "created_at.desc")
            .build()
        val friendshipRows = parseArray(execute(authorizedRequest(friendshipUrl.toString(), session).build()))
        val friendTuples = friendshipRows.mapNotNull { row ->
            val item = row.asJsonObject
            val userA = item.string("user_a")
            val userB = item.string("user_b")
            val otherId = when (uid) {
                userA -> userB
                userB -> userA
                else -> ""
            }
            val conversationId = item.string("conversation_id")
            if (otherId.isBlank() || conversationId.isBlank()) null else otherId to conversationId
        }
        val friendProfiles = loadProfiles(friendTuples.map { it.first }.distinct(), session)
            .associateBy { it.id }
        val friends = friendTuples.mapNotNull { (userId, conversationId) ->
            friendProfiles[userId]?.let { profile ->
                ChatFriend(profile.id, profile.username, profile.displayName, conversationId, profile.lastActiveAtMillis)
            }
        }

        val requestUrl = restUrl("chat_friend_requests")
            .addQueryParameter("select", "id,sender_id,receiver_id,created_at")
            .addQueryParameter("receiver_id", "eq.$uid")
            .addQueryParameter("status", "eq.pending")
            .addQueryParameter("order", "created_at.desc")
            .build()
        val requestRows = parseArray(execute(authorizedRequest(requestUrl.toString(), session).build()))
        val requestProfiles = loadProfiles(
            requestRows.map { it.asJsonObject.string("sender_id") }.distinct(),
            session
        ).associateBy { it.id }
        val incoming = requestRows.mapNotNull { row ->
            val item = row.asJsonObject
            val senderId = item.string("sender_id")
            val profile = requestProfiles[senderId] ?: return@mapNotNull null
            ChatFriendRequest(
                id = item.string("id"),
                senderId = senderId,
                username = profile.username,
                displayName = profile.displayName,
                createdAt = item.string("created_at")
            )
        }

        val unreadNotifications = loadUnreadNotifications(session)
        return ChatDashboard(friends, incoming, unreadNotifications)
    }

    suspend fun loadMessages(session: ChatAuthSession, conversationId: String): List<ChatMessage> {
        val url = restUrl("chat_messages")
            .addQueryParameter("select", "id,conversation_id,sender_id,body,created_at,edited_at")
            .addQueryParameter("conversation_id", "eq.$conversationId")
            .addQueryParameter("order", "created_at.asc")
            .addQueryParameter("limit", "100")
            .build()
        return parseArray(execute(authorizedRequest(url.toString(), session).build())).map { row ->
            row.asJsonObject.toMessage()
        }
    }

    suspend fun sendMessage(session: ChatAuthSession, conversationId: String, bodyText: String): ChatMessage {
        val body = JsonObject().apply {
            addProperty("conversation_id", conversationId)
            addProperty("sender_id", session.userId)
            addProperty("body", bodyText.trim())
        }
        val url = restUrl("chat_messages")
            .addQueryParameter("select", "id,conversation_id,sender_id,body,created_at,edited_at")
            .build()
        val request = authorizedRequest(url.toString(), session)
            .post(body.toString().jsonBody())
            .header("Prefer", "return=representation")
            .build()
        return parseArray(execute(request)).firstOrNull()?.asJsonObject?.toMessage()
            ?: throw ChatApiException("Message was not saved.")
    }

    suspend fun editMessage(
        session: ChatAuthSession,
        messageId: String,
        conversationId: String,
        bodyText: String
    ): ChatMessage {
        val body = JsonObject().apply { addProperty("body", bodyText.trim()) }
        val url = restUrl("chat_messages")
            .addQueryParameter("select", "id,conversation_id,sender_id,body,created_at,edited_at")
            .addQueryParameter("id", "eq.$messageId")
            .addQueryParameter("conversation_id", "eq.$conversationId")
            .addQueryParameter("sender_id", "eq.${session.userId}")
            .build()
        val request = authorizedRequest(url.toString(), session)
            .patch(body.toString().jsonBody())
            .header("Prefer", "return=representation")
            .build()
        return parseArray(execute(request)).firstOrNull()?.asJsonObject?.toMessage()
            ?: throw ChatApiException("Message could not be edited. It may have been deleted.")
    }

    suspend fun deleteMessage(session: ChatAuthSession, messageId: String, conversationId: String) {
        val url = restUrl("chat_messages")
            .addQueryParameter("select", "id")
            .addQueryParameter("id", "eq.$messageId")
            .addQueryParameter("conversation_id", "eq.$conversationId")
            .addQueryParameter("sender_id", "eq.${session.userId}")
            .build()
        val request = authorizedRequest(url.toString(), session)
            .delete()
            .header("Prefer", "return=representation")
            .build()
        if (parseArray(execute(request)).size() == 0) {
            throw ChatApiException("Message could not be deleted. It may have already been removed.")
        }
    }

    suspend fun loadUnreadNotifications(session: ChatAuthSession): List<ChatNotification> {
        val url = restUrl("chat_notifications")
            .addQueryParameter("select", "id,sender_id,conversation_id,message_id,message_preview,created_at")
            .addQueryParameter("recipient_id", "eq.${session.userId}")
            .addQueryParameter("read_at", "is.null")
            .addQueryParameter("order", "created_at.desc")
            .addQueryParameter("limit", "1000")
            .build()
        val rows = parseArray(execute(authorizedRequest(url.toString(), session).build()))
        val profiles = loadProfiles(rows.map { it.asJsonObject.string("sender_id") }.distinct(), session)
            .associateBy { it.id }
        return rows.mapNotNull { row ->
            val item = row.asJsonObject
            val senderId = item.string("sender_id")
            val profile = profiles[senderId] ?: return@mapNotNull null
            ChatNotification(
                id = item.string("id"),
                senderId = senderId,
                senderUsername = profile.username,
                senderDisplayName = profile.displayName,
                conversationId = item.string("conversation_id"),
                messageId = item.string("message_id"),
                preview = item.string("message_preview"),
                createdAt = item.string("created_at"),
                senderLastActiveAtMillis = profile.lastActiveAtMillis
            )
        }
    }

    suspend fun unreadSenderCount(session: ChatAuthSession): Int {
        val url = restUrl("chat_notifications")
            .addQueryParameter("select", "sender_id")
            .addQueryParameter("recipient_id", "eq.${session.userId}")
            .addQueryParameter("read_at", "is.null")
            .addQueryParameter("limit", "1000")
            .build()
        return parseArray(execute(authorizedRequest(url.toString(), session).build()))
            .mapNotNull { it.asJsonObject.string("sender_id").takeIf(String::isNotBlank) }
            .distinct()
            .size
    }

    suspend fun touchChatPresence(session: ChatAuthSession) {
        val request = authorizedRequest(
            "${ChatBackendConfig.PROJECT_URL}/rest/v1/rpc/touch_chat_presence",
            session
        ).post(JsonObject().toString().jsonBody()).build()
        execute(request)
    }

    suspend fun sendFriendRequest(session: ChatAuthSession, username: String) {
        val body = JsonObject().apply { addProperty("target_username", username) }
        val request = authorizedRequest("${ChatBackendConfig.PROJECT_URL}/rest/v1/rpc/send_chat_friend_request", session)
            .post(body.toString().jsonBody())
            .build()
        execute(request)
    }

    suspend fun respondToFriendRequest(session: ChatAuthSession, requestId: String, accept: Boolean) {
        val body = JsonObject().apply {
            addProperty("request_id", requestId)
            addProperty("accept", accept)
        }
        val request = authorizedRequest("${ChatBackendConfig.PROJECT_URL}/rest/v1/rpc/respond_chat_friend_request", session)
            .post(body.toString().jsonBody())
            .build()
        execute(request)
    }

    suspend fun markConversationNotificationsRead(session: ChatAuthSession, conversationId: String) {
        val body = JsonObject().apply { addProperty("p_conversation_id", conversationId) }
        val request = authorizedRequest(
            "${ChatBackendConfig.PROJECT_URL}/rest/v1/rpc/mark_chat_conversation_notifications_read",
            session
        ).post(body.toString().jsonBody()).build()
        execute(request)
    }

    // ------------------------------------------------------------------ parties

    suspend fun loadPartyDashboard(session: ChatAuthSession): PartyDashboard {
        val uid = session.userId
        val membershipUrl = restUrl("chat_party_members")
            .addQueryParameter("select", "party_id")
            .addQueryParameter("user_id", "eq.$uid")
            .build()
        val partyIds = parseArray(execute(authorizedRequest(membershipUrl.toString(), session).build()))
            .mapNotNull { it.asJsonObject.string("party_id").takeIf(String::isNotBlank) }
            .distinct()

        val parties = loadParties(session, partyIds)
        val publicParties = loadPublicParties(session).filter { party -> parties.none { it.id == party.id } }
        val invites = loadPartyInvites(session, incoming = true)
        val sentInvites = loadPartyInvites(session, incoming = false)
        return PartyDashboard(parties, publicParties, invites, sentInvites)
    }

    private suspend fun loadParties(session: ChatAuthSession, partyIds: List<String>): List<SocialParty> {
        if (partyIds.isEmpty()) return emptyList()
        val url = restUrl("chat_parties")
            .addQueryParameter("select", PARTY_SELECT)
            .addQueryParameter("id", "in.(${partyIds.joinToString(",")})")
            .addQueryParameter("order", "created_at.desc")
            .build()
        return parseArray(execute(authorizedRequest(url.toString(), session).build()))
            .mapNotNull { it.asJsonObject.toParty() }
            .filterNot { it.isExpired() }
    }

    private suspend fun loadPublicParties(session: ChatAuthSession): List<SocialParty> {
        val url = restUrl("chat_parties")
            .addQueryParameter("select", PARTY_SELECT)
            .addQueryParameter("is_public", "eq.true")
            .addQueryParameter("expires_at", "gt.${Instant.now()}")
            .addQueryParameter("order", "created_at.desc")
            .addQueryParameter("limit", "30")
            .build()
        return parseArray(execute(authorizedRequest(url.toString(), session).build()))
            .mapNotNull { it.asJsonObject.toParty() }
    }

    private suspend fun loadPartyInvites(session: ChatAuthSession, incoming: Boolean): List<PartyInvite> {
        val column = if (incoming) "to_id" else "from_id"
        val url = restUrl("chat_party_invites")
            .addQueryParameter("select", "id,party_id,from_id,to_id,status,created_at")
            .addQueryParameter(column, "eq.${session.userId}")
            .addQueryParameter("status", "eq.pending")
            .addQueryParameter("order", "created_at.desc")
            .build()
        val rows = parseArray(execute(authorizedRequest(url.toString(), session).build()))
            .map { it.asJsonObject }
        if (rows.isEmpty()) return emptyList()

        val partyIds = rows.mapNotNull { it.string("party_id").takeIf(String::isNotBlank) }.distinct()
        val parties = runCatching { loadParties(session, partyIds) }.getOrDefault(emptyList()).associateBy { it.id }
        val profileIds = rows.mapNotNull { it.string("from_id").takeIf(String::isNotBlank) }.distinct()
        val profiles = runCatching { loadProfiles(profileIds, session) }.getOrDefault(emptyList()).associateBy { it.id }

        return rows.mapNotNull { row ->
            val partyId = row.string("party_id")
            val party = parties[partyId] ?: return@mapNotNull null
            val fromProfile = profiles[row.string("from_id")]
            PartyInvite(
                id = row.string("id"),
                partyId = partyId,
                fromId = row.string("from_id"),
                fromUsername = fromProfile?.username.orEmpty(),
                fromDisplayName = fromProfile?.displayName ?: fromProfile?.username ?: "A friend",
                partyTitle = party.title,
                mcVersion = party.mcVersion,
                loader = party.loader,
                instanceName = party.instanceName,
                maxMembers = party.maxMembers,
                memberCount = party.memberCount,
                expiresAtMillis = party.expiresAtMillis,
                createdAtMillis = row.instantMillis("created_at") ?: 0L
            )
        }
    }

    suspend fun loadPartyMessages(session: ChatAuthSession, partyId: String): List<PartyMessage> {
        val url = restUrl("chat_party_messages")
            .addQueryParameter("select", PARTY_MESSAGE_SELECT)
            .addQueryParameter("party_id", "eq.$partyId")
            .addQueryParameter("order", "created_at.asc")
            .addQueryParameter("limit", "200")
            .build()
        return parseArray(execute(authorizedRequest(url.toString(), session).build()))
            .mapNotNull { it.asJsonObject.toPartyMessage() }
    }

    suspend fun sendPartyMessage(session: ChatAuthSession, partyId: String, bodyText: String): PartyMessage {
        val body = JsonObject().apply {
            addProperty("party_id", partyId)
            addProperty("sender_id", session.userId)
            addProperty("body", bodyText.trim())
        }
        val url = restUrl("chat_party_messages")
            .addQueryParameter("select", PARTY_MESSAGE_SELECT)
            .build()
        val request = authorizedRequest(url.toString(), session)
            .post(body.toString().jsonBody())
            .header("Prefer", "return=representation")
            .build()
        return parseArray(execute(request)).firstOrNull()?.asJsonObject?.toPartyMessage()
            ?: throw ChatApiException("Message was not saved.")
    }

    suspend fun deletePartyMessage(session: ChatAuthSession, messageId: String) {
        val url = restUrl("chat_party_messages")
            .addQueryParameter("id", "eq.$messageId")
            .build()
        execute(authorizedRequest(url.toString(), session).delete().build())
    }

    suspend fun createParty(session: ChatAuthSession, draft: PartyDraft): String {
        val body = JsonObject().apply {
            addProperty("p_title", draft.title.trim())
            draft.versionId?.let { addProperty("p_version_id", it) }
            draft.instanceName?.let { addProperty("p_instance_name", it) }
            draft.mcVersion?.let { addProperty("p_mc_version", it) }
            draft.loader?.let { addProperty("p_loader", it) }
            draft.serverAddress?.takeIf { it.isNotBlank() }?.let { addProperty("p_server_address", it.trim()) }
            addProperty("p_max_members", draft.maxMembers)
            addProperty("p_is_public", draft.isPublic)
        }
        val response = partyRpc(session, "create_chat_party", body)
        return runCatching { JsonParser.parseString(response).asString }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: throw ChatApiException("Party was not created.")
    }

    suspend fun joinPartyByCode(session: ChatAuthSession, code: String): String {
        val body = JsonObject().apply { addProperty("p_code", code.trim().uppercase()) }
        val response = partyRpc(session, "join_chat_party_by_code", body)
        return runCatching { JsonParser.parseString(response).asString }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: throw ChatApiException("That party code is not valid anymore.")
    }

    suspend fun joinParty(session: ChatAuthSession, partyId: String) {
        partyRpc(session, "join_chat_party", JsonObject().apply { addProperty("p_party_id", partyId) })
    }

    suspend fun leaveParty(session: ChatAuthSession, partyId: String) {
        partyRpc(session, "leave_chat_party", JsonObject().apply { addProperty("p_party_id", partyId) })
    }

    suspend fun endParty(session: ChatAuthSession, partyId: String) {
        partyRpc(session, "delete_chat_party", JsonObject().apply { addProperty("p_party_id", partyId) })
    }

    suspend fun extendParty(session: ChatAuthSession, partyId: String) {
        partyRpc(session, "refresh_chat_party", JsonObject().apply { addProperty("p_party_id", partyId) })
    }

    suspend fun inviteToParty(session: ChatAuthSession, partyId: String, username: String) {
        partyRpc(
            session,
            "invite_to_chat_party",
            JsonObject().apply {
                addProperty("p_party_id", partyId)
                addProperty("p_username", username.removePrefix("@").trim().lowercase())
            }
        )
    }

    suspend fun respondToPartyInvite(session: ChatAuthSession, inviteId: String, accept: Boolean) {
        partyRpc(
            session,
            "respond_chat_party_invite",
            JsonObject().apply {
                addProperty("p_invite_id", inviteId)
                addProperty("p_accept", accept)
            }
        )
    }

    suspend fun kickPartyMember(session: ChatAuthSession, partyId: String, userId: String) {
        partyRpc(
            session,
            "kick_chat_party_member",
            JsonObject().apply {
                addProperty("p_party_id", partyId)
                addProperty("p_user_id", userId)
            }
        )
    }

    private suspend fun partyRpc(session: ChatAuthSession, name: String, payload: JsonObject): String =
        execute(
            authorizedRequest("${ChatBackendConfig.PROJECT_URL}/rest/v1/rpc/$name", session)
                .post(payload.toString().jsonBody())
                .build()
        )

    private fun JsonObject.toParty(): SocialParty? {
        val id = string("id").takeIf { it.isNotBlank() } ?: return null
        val members = get("chat_party_members")
            ?.takeUnless { it.isJsonNull }
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.mapNotNull { element ->
                val row = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
                val userId = row.string("user_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val profile = row.objectOrNull("chat_profiles")
                PartyMember(
                    userId = userId,
                    username = profile?.string("username") ?: userId.take(8),
                    displayName = profile?.string("display_name")?.takeIf { it.isNotBlank() }
                        ?: profile?.string("username") ?: "Player",
                    isHost = row.get("is_host")?.takeUnless { it.isJsonNull }?.asBoolean ?: false,
                    lastActiveAtMillis = profile?.instantMillis("last_active_at"),
                    joinedAtMillis = row.instantMillis("joined_at") ?: 0L
                )
            }
            .orEmpty()
            .sortedBy { if (it.isHost) 0L else it.joinedAtMillis.coerceAtLeast(1L) }

        return SocialParty(
            id = id,
            hostId = string("host_id"),
            title = string("title", "Party"),
            versionId = string("version_id").takeIf { it.isNotBlank() },
            instanceName = string("instance_name").takeIf { it.isNotBlank() },
            mcVersion = string("mc_version").takeIf { it.isNotBlank() },
            loader = string("loader").takeIf { it.isNotBlank() },
            modpackSource = string("modpack_source").takeIf { it.isNotBlank() },
            modpackId = string("modpack_id").takeIf { it.isNotBlank() },
            modpackIconUrl = string("modpack_icon_url").takeIf { it.isNotBlank() },
            serverAddress = string("server_address").takeIf { it.isNotBlank() },
            maxMembers = long("max_members", 4L).toInt(),
            isPublic = get("is_public")?.takeUnless { it.isJsonNull }?.asBoolean ?: false,
            inviteCode = string("invite_code"),
            createdAtMillis = instantMillis("created_at") ?: 0L,
            expiresAtMillis = instantMillis("expires_at") ?: 0L,
            members = members
        )
    }

    private fun JsonObject.toPartyMessage(): PartyMessage? {
        val id = string("id").takeIf { it.isNotBlank() } ?: return null
        val profile = objectOrNull("chat_profiles")
        return PartyMessage(
            id = id,
            partyId = string("party_id"),
            senderId = string("sender_id"),
            senderName = profile?.string("display_name")?.takeIf { it.isNotBlank() }
                ?: profile?.string("username") ?: "Player",
            senderUsername = profile?.string("username").orEmpty(),
            body = string("body"),
            createdAtMillis = instantMillis("created_at") ?: System.currentTimeMillis()
        )
    }

    fun subscribe(
        session: ChatAuthSession,
        conversationId: String?,
        onDatabaseChange: (ChatRealtimeEvent) -> Unit
    ): Closeable = RealtimeSubscription(session, conversationId, onDatabaseChange)

    private suspend fun loadProfiles(ids: List<String>, session: ChatAuthSession): List<ChatProfile> {
        if (ids.isEmpty()) return emptyList()
        val url = restUrl("chat_profiles")
            .addQueryParameter("select", "id,username,display_name,bio,last_active_at")
            .addQueryParameter("id", "in.(${ids.joinToString(",")})")
            .build()
        return parseArray(execute(authorizedRequest(url.toString(), session).build()))
            .map { it.asJsonObject.toProfile() }
    }

    private fun authorizedRequest(url: String, session: ChatAuthSession): Request.Builder =
        Request.Builder()
            .url(url)
            .header("apikey", ChatBackendConfig.PUBLISHABLE_KEY)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")

    private fun restUrl(path: String) = "${ChatBackendConfig.PROJECT_URL}/rest/v1/$path".toHttpUrl().newBuilder()

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        val call = client.newCall(request)
        call.timeout().timeout(25, TimeUnit.SECONDS)
        call.execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ChatApiException(response.errorDescription(body))
            body
        }
    }

    private fun Request.Builder.withPublicKey(): Request.Builder =
        header("apikey", ChatBackendConfig.PUBLISHABLE_KEY)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")

    private fun String.jsonBody() = toRequestBody(JSON_MEDIA_TYPE)

    private fun authBase() = "${ChatBackendConfig.PROJECT_URL}/auth/v1"

    private fun sessionFrom(root: JsonObject, fallbackUserId: String? = null): ChatAuthSession {
        val userId = root.objectOrNull("user")?.string("id") ?: fallbackUserId
            ?: throw ChatApiException("Supabase did not return a chat user ID.")
        val accessToken = root.string("access_token")
        if (accessToken.isBlank()) throw ChatApiException("Supabase did not return an access token.")
        return ChatAuthSession(
            userId = userId,
            accessToken = accessToken,
            refreshToken = root.string("refresh_token"),
            expiresAtMillis = System.currentTimeMillis() + root.long("expires_in", 3600) * 1000L
        )
    }

    private fun parseObject(body: String): JsonObject = try {
        JsonParser.parseString(body).asJsonObject
    } catch (_: Exception) {
        throw ChatApiException("Supabase returned an invalid response.")
    }

    private fun parseArray(body: String): JsonArray = try {
        JsonParser.parseString(body).asJsonArray
    } catch (_: Exception) {
        throw ChatApiException("Supabase returned an invalid response.")
    }

    private fun JsonObject.toProfile() = ChatProfile(
        id = string("id"),
        username = string("username"),
        displayName = string("display_name", string("username")),
        bio = string("bio"),
        lastActiveAtMillis = instantMillis("last_active_at")
    )

    private fun JsonObject.toMessage() = ChatMessage(
        id = string("id"),
        conversationId = string("conversation_id"),
        senderId = string("sender_id"),
        body = string("body"),
        createdAt = string("created_at"),
        editedAt = get("edited_at")?.takeUnless { it.isJsonNull }?.asString
    )

    private fun JsonObject.instantMillis(name: String): Long? =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.let { timestamp ->
            runCatching { OffsetDateTime.parse(timestamp).toInstant().toEpochMilli() }.getOrNull()
        }

    private fun JsonObject.string(name: String, fallback: String = ""): String =
        get(name)?.takeUnless { it.isJsonNull }?.asString ?: fallback

    private fun JsonObject.long(name: String, fallback: Long): Long =
        get(name)?.takeUnless { it.isJsonNull }?.asLong ?: fallback

    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        get(name)?.takeUnless { it.isJsonNull }?.asJsonObject

    private fun Response.errorDescription(body: String): String {
        if (body.contains("chat_profiles_username_unique", ignoreCase = true) ||
            body.contains("duplicate key value", ignoreCase = true)
        ) {
            return "That chat username is already taken. Try another one."
        }
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
        return root?.string("msg")?.takeIf { it.isNotBlank() }
            ?: root?.string("message")?.takeIf { it.isNotBlank() }
            ?: root?.string("error_description")?.takeIf { it.isNotBlank() }
            ?: root?.string("details")?.takeIf { it.isNotBlank() }
            ?: root?.string("error")?.takeIf { it.isNotBlank() }
            ?: "Chat request failed (HTTP $code)."
    }

    private inner class RealtimeSubscription(
        private val session: ChatAuthSession,
        private val conversationId: String?,
        private val onDatabaseChange: (ChatRealtimeEvent) -> Unit
    ) : Closeable {
        private val closed = AtomicBoolean(false)
        private val ref = AtomicInteger(1)
        private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "TCL-chat-realtime").apply { isDaemon = true }
        }
        @Volatile private var socket: WebSocket? = null
        @Volatile private var heartbeat: ScheduledFuture<*>? = null
        @Volatile private var reconnect: ScheduledFuture<*>? = null
        @Volatile private var reconnectDelayMs = 1_000L

        init {
            connect()
        }

        private fun connect() {
            if (closed.get()) return
            // OkHttp HttpUrl accepts only http/https. newWebSocket upgrades this HTTPS request to WSS.
            val endpoint = "${ChatBackendConfig.PROJECT_URL}/realtime/v1/websocket"
                .toHttpUrl().newBuilder()
                .addQueryParameter("apikey", ChatBackendConfig.PUBLISHABLE_KEY)
                .addQueryParameter("vsn", "1.0.0")
                .build()
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()
            socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectDelayMs = 1_000L
                    joinChannel(webSocket)
                    heartbeat?.cancel(false)
                    heartbeat = scheduler.scheduleAtFixedRate({
                        if (!closed.get()) {
                            val heartbeatRef = ref.incrementAndGet().toString()
                            webSocket.send(
                                JsonObject().apply {
                                    addProperty("topic", "phoenix")
                                    addProperty("event", "heartbeat")
                                    add("payload", JsonObject())
                                    addProperty("ref", heartbeatRef)
                                }.toString()
                            )
                        }
                    }, 25, 25, TimeUnit.SECONDS)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val event = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                        ?: return
                    val payload = event.objectOrNull("payload") ?: return
                    if (event.string("event") == "phx_reply") {
                        val status = payload.string("status")
                        if (status.isNotBlank() && status != "ok") {
                            webSocket.close(1011, "realtime channel join failed")
                        }
                        return
                    }
                    if (event.string("event") != "postgres_changes") return
                    val data = payload.objectOrNull("data") ?: return
                    val table = data.string("table")
                    val record = data.objectOrNull("record") ?: data.objectOrNull("old_record") ?: return
                    if (table.isBlank()) return
                    runCatching {
                        onDatabaseChange(
                            ChatRealtimeEvent(
                                table = table,
                                eventType = data.string("type"),
                                record = record
                            )
                        )
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    scheduleReconnect()
                }
            })
        }

        private fun joinChannel(webSocket: WebSocket) {
            val changes = JsonArray()
            conversationId?.takeIf { it.isNotBlank() }?.let { id ->
                changes.add(changeFilter("INSERT", "chat_messages", "conversation_id=eq.$id"))
                changes.add(changeFilter("UPDATE", "chat_messages", "conversation_id=eq.$id"))
            }
            changes.add(changeFilter("INSERT", "chat_friend_requests", "receiver_id=eq.${session.userId}"))
            changes.add(changeFilter("UPDATE", "chat_friend_requests", "receiver_id=eq.${session.userId}"))
            changes.add(changeFilter("UPDATE", "chat_friend_requests", "sender_id=eq.${session.userId}"))
            changes.add(changeFilter("INSERT", "chat_notifications", "recipient_id=eq.${session.userId}"))

            val joinRef = ref.incrementAndGet().toString()
            val frame = JsonObject().apply {
                addProperty("topic", "realtime:chat-${session.userId}")
                addProperty("event", "phx_join")
                add("payload", JsonObject().apply {
                    add("config", JsonObject().apply {
                        add("broadcast", JsonObject().apply {
                            addProperty("ack", false)
                            addProperty("self", false)
                        })
                        add("presence", JsonObject().apply { addProperty("key", "") })
                        add("postgres_changes", changes)
                        addProperty("private", false)
                    })
                    addProperty("access_token", session.accessToken)
                })
                addProperty("ref", joinRef)
                addProperty("join_ref", joinRef)
            }
            webSocket.send(frame.toString())
        }

        private fun changeFilter(event: String, table: String, filter: String) = JsonObject().apply {
            addProperty("event", event)
            addProperty("schema", "public")
            addProperty("table", table)
            addProperty("filter", filter)
        }

        private fun scheduleReconnect() {
            if (closed.get()) return
            heartbeat?.cancel(false)
            val delay = reconnectDelayMs
            reconnectDelayMs = min(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS)
            reconnect?.cancel(false)
            reconnect = scheduler.schedule({ connect() }, delay, TimeUnit.MILLISECONDS)
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            heartbeat?.cancel(true)
            reconnect?.cancel(true)
            socket?.close(1000, "chat screen closed")
            scheduler.shutdownNow()
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val SESSION_REFRESH_MARGIN_MS = 60_000L
        const val MAX_RECONNECT_DELAY_MS = 30_000L
        const val PARTY_SELECT =
            "id,host_id,title,version_id,instance_name,mc_version,loader,modpack_source,modpack_id," +
                "modpack_icon_url,server_address,max_members,is_public,invite_code,created_at,expires_at," +
                "chat_party_members(user_id,is_host,joined_at,chat_profiles(username,display_name,last_active_at))"
        const val PARTY_MESSAGE_SELECT =
            "id,party_id,sender_id,body,created_at,chat_profiles(username,display_name)"
    }
}

internal class ChatApiException(message: String) : IOException(message)
