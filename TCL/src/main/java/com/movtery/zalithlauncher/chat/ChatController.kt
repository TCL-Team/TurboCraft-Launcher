/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery and contributors
 * Licensed under the GNU GPL v3 or later.
 */

package com.movtery.zalithlauncher.chat

import android.content.Context
import android.util.Base64
import com.google.gson.JsonObject
import com.movtery.zalithlauncher.game.account.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import java.security.SecureRandom

internal data class ChatUiState(
    val busy: Boolean = false,
    val sendingMessage: Boolean = false,
    val session: ChatAuthSession? = null,
    val profile: ChatProfile? = null,
    val needsProfile: Boolean = false,
    val suggestedUsername: String = "",
    val panel: ChatPanel = ChatPanel.CHATS,
    val friends: List<ChatFriend> = emptyList(),
    val incomingRequests: List<ChatFriendRequest> = emptyList(),
    val unreadNotifications: List<ChatNotification> = emptyList(),
    val selectedFriendId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val mutatingMessageId: String? = null,
    val error: String? = null,
    val notice: String? = null,
    /** Shown once after creating/replacing a recovery code; never persisted on the device. */
    val recoveryCode: String? = null
)

/** Coordinates the selected launcher's chat identity, friend actions, messages and Realtime. */
internal class ChatController(
    context: Context,
    private val launcherAccount: Account?
) : Closeable {
    private val applicationContext = context.applicationContext
    private val repository = SupabaseChatRepository()
    private val sessionStore = ChatSessionStore(applicationContext, launcherAccount?.uniqueUUID)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()

    private var started = false
    @Volatile private var screenVisible = false
    private var realtime: Closeable? = null
    private var refreshJob: Job? = null
    private var livePollJob: Job? = null
    private var sessionRefreshJob: Job? = null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            val account = launcherAccount
            if (account == null) {
                _state.update { it.copy(busy = false) }
                return@launch
            }

            var activeSession = _state.value.session
            var activeProfile = _state.value.profile
            try {
                val identity = ChatLauncherIdentity.from(account)
                val saved = sessionStore.load()
                val session = if (saved == null) {
                    repository.createLauncherSession(identity)
                } else {
                    try {
                        repository.ensureFreshSession(saved)
                    } catch (refreshError: Exception) {
                        if (saved.launcherAccountId == identity.localAccountId) {
                            repository.createLauncherSession(identity)
                        } else {
                            throw refreshError
                        }
                    }
                }
                activeSession = session
                sessionStore.save(session)
                runCatching { repository.touchChatPresence(session) }
                val profile = repository.getOwnProfile(session) ?: activeProfile
                activeProfile = profile
                if (profile == null) {
                    _state.update {
                        it.copy(
                            busy = false,
                            session = session,
                            needsProfile = true,
                            suggestedUsername = identity.suggestedUsername,
                            error = null
                        )
                    }
                    scheduleSessionRefresh(session)
                } else {
                    _state.update { it.copy(suggestedUsername = identity.suggestedUsername) }
                    loadDashboard(session, profile, preferredFriendId = null)
                }
            } catch (error: Exception) {
                _state.update { current ->
                    current.copy(
                        busy = false,
                        session = activeSession ?: current.session,
                        profile = activeProfile ?: current.profile,
                        needsProfile = if (activeProfile != null) false else current.needsProfile,
                        error = error.message ?: "Could not connect this launcher account to chat. Try again."
                    )
                }
            }
        }
    }

    fun retry() {
        if (_state.value.busy) return
        started = false
        start()
    }

    fun setScreenVisible(visible: Boolean) {
        val wasVisible = screenVisible
        screenVisible = visible
        if (visible) {
            if (!wasVisible) {
                val current = _state.value
                if (current.panel == ChatPanel.CHATS) {
                    current.friends.firstOrNull { it.userId == current.selectedFriendId }?.let(::markConversationRead)
                }
            }
            if (launcherAccount != null) startVisiblePolling()
        } else {
            livePollJob?.cancel()
            livePollJob = null
        }
    }

    fun completeProfile(username: String) {
        val normalizedUsername = normalizeUsername(username)
        if (!USERNAME_PATTERN.matches(normalizedUsername)) {
            _state.update {
                it.copy(error = "Username must be 3-20 characters: letters, numbers, dot or underscore.", notice = null)
            }
            return
        }
        performBusyAction {
            val session = currentSession()
            val displayName = launcherAccount?.username?.takeIf(String::isNotBlank) ?: normalizedUsername
            val profile = repository.createProfile(session, normalizedUsername, displayName)
            loadDashboard(session, profile, preferredFriendId = null)
            val recoveryCode = generateRecoveryCode()
            try {
                repository.setRecoveryCode(session, recoveryCode)
                _state.update {
                    it.copy(
                        recoveryCode = recoveryCode,
                        notice = "Save this recovery code somewhere safe. It is needed to restore your chat ID after reinstall."
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        error = error.message ?: "Your profile is ready, but a recovery code could not be saved. Create one from Edit Profile.",
                        notice = null
                    )
                }
            }
        }
    }

    fun recoverProfile(username: String, recoveryCode: String) {
        val normalizedUsername = normalizeUsername(username)
        val normalizedCode = recoveryCode.trim()
        if (!USERNAME_PATTERN.matches(normalizedUsername)) {
            _state.update { it.copy(error = "Enter your 3-20 character chat username.", notice = null) }
            return
        }
        if (normalizedCode.length !in 32..64 || !normalizedCode.matches(RECOVERY_CODE_PATTERN)) {
            _state.update { it.copy(error = "Enter the recovery code saved for this chat profile.", notice = null) }
            return
        }
        val account = launcherAccount
        if (account == null) {
            _state.update { it.copy(error = "Select a launcher account before restoring chat.", notice = null) }
            return
        }
        performBusyAction {
            val identity = ChatLauncherIdentity.from(account)
            val session = repository.recoverLauncherSession(identity, normalizedUsername, normalizedCode)
            sessionStore.save(session)
            val profile = repository.getOwnProfile(session)
                ?: throw ChatApiException("That recovery code did not restore a chat profile.")
            _state.update { it.copy(session = session, suggestedUsername = identity.suggestedUsername) }
            loadDashboard(session, profile, preferredFriendId = null)
            _state.update { it.copy(notice = "Welcome back, @${profile.username}. Your chat profile and friends are restored.") }
        }
    }

    fun updateProfile(username: String, displayName: String, bio: String) {
        val normalizedUsername = normalizeUsername(username)
        val normalizedDisplayName = displayName.trim()
        val normalizedBio = bio.trim()
        if (!USERNAME_PATTERN.matches(normalizedUsername)) {
            _state.update { it.copy(error = "Username must be 3-20 characters: letters, numbers, dot or underscore.", notice = null) }
            return
        }
        if (normalizedDisplayName.isBlank() || normalizedDisplayName.length > MAX_DISPLAY_NAME_LENGTH) {
            _state.update { it.copy(error = "Display name must contain 1-32 characters.", notice = null) }
            return
        }
        if (normalizedBio.length > MAX_BIO_LENGTH) {
            _state.update { it.copy(error = "Bio can be up to 160 characters.", notice = null) }
            return
        }
        performBusyAction {
            val session = currentSession()
            val profile = repository.updateProfile(session, normalizedUsername, normalizedDisplayName, normalizedBio)
            loadDashboard(session, profile, preferredFriendId = _state.value.selectedFriendId)
            _state.update { it.copy(notice = "Chat profile updated.", error = null) }
        }
    }

    fun createRecoveryCode() {
        performBusyAction {
            val session = currentSession()
            val recoveryCode = generateRecoveryCode()
            repository.setRecoveryCode(session, recoveryCode)
            _state.update {
                it.copy(
                    recoveryCode = recoveryCode,
                    notice = "Save this recovery code somewhere safe. Creating a new one replaces the previous code."
                )
            }
        }
    }

    fun dismissRecoveryCode() {
        _state.update { it.copy(recoveryCode = null) }
    }

    fun selectPanel(panel: ChatPanel) {
        _state.update { it.copy(panel = panel, error = null, notice = null) }
        if (panel == ChatPanel.CHATS) {
            val current = _state.value
            current.friends.firstOrNull { it.userId == current.selectedFriendId }?.let(::markConversationRead)
        }
    }

    fun selectFriend(friend: ChatFriend) {
        val current = _state.value
        if (current.selectedFriendId == friend.userId) {
            _state.update { it.copy(panel = ChatPanel.CHATS, error = null) }
            markConversationRead(friend)
            return
        }
        realtime?.close()
        realtime = null
        _state.update {
            it.copy(
                panel = ChatPanel.CHATS,
                selectedFriendId = friend.userId,
                messages = emptyList(),
                error = null
            )
        }
        scope.launch {
            try {
                val session = currentSession()
                markConversationRead(session, friend.conversationId)
                val messages = repository.loadMessages(session, friend.conversationId)
                val stillSelected = _state.value.selectedFriendId == friend.userId
                if (stillSelected) {
                    _state.update { it.copy(messages = messages) }
                    installRealtime(session, friend.conversationId)
                }
            } catch (error: Exception) {
                setError(error)
            }
        }
    }

    fun openNotification(notification: ChatNotification) {
        val friend = _state.value.friends.firstOrNull { it.userId == notification.senderId }
        if (friend == null) {
            refresh()
            return
        }
        selectFriend(friend)
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (trimmed.length > MAX_MESSAGE_LENGTH) {
            _state.update { it.copy(error = "Messages can be up to $MAX_MESSAGE_LENGTH characters.") }
            return
        }
        val friend = selectedFriend() ?: return
        if (_state.value.sendingMessage) return
        scope.launch {
            _state.update { it.copy(sendingMessage = true, error = null) }
            try {
                val session = currentSession()
                val message = repository.sendMessage(session, friend.conversationId, trimmed)
                _state.update { current ->
                    if (current.selectedFriendId != friend.userId) current
                    else current.copy(messages = current.messages.upsertMessage(message))
                }
            } catch (error: Exception) {
                setError(error)
            } finally {
                _state.update { it.copy(sendingMessage = false) }
            }
        }
    }

    fun editMessage(message: ChatMessage, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(error = "A message cannot be empty.", notice = null) }
            return
        }
        if (trimmed.length > MAX_MESSAGE_LENGTH) {
            _state.update { it.copy(error = "Messages can be up to $MAX_MESSAGE_LENGTH characters.", notice = null) }
            return
        }
        if (message.senderId != _state.value.session?.userId || _state.value.mutatingMessageId != null) return

        scope.launch {
            _state.update { it.copy(mutatingMessageId = message.id, error = null, notice = null) }
            try {
                val session = currentSession()
                val updated = repository.editMessage(session, message.id, message.conversationId, trimmed)
                _state.update { current ->
                    current.copy(messages = current.messages.map { if (it.id == updated.id) updated else it })
                }
            } catch (error: Exception) {
                setError(error)
            } finally {
                _state.update { current ->
                    if (current.mutatingMessageId == message.id) current.copy(mutatingMessageId = null) else current
                }
            }
        }
    }

    fun deleteMessage(message: ChatMessage) {
        if (message.senderId != _state.value.session?.userId || _state.value.mutatingMessageId != null) return
        scope.launch {
            _state.update { it.copy(mutatingMessageId = message.id, error = null, notice = null) }
            try {
                val session = currentSession()
                repository.deleteMessage(session, message.id, message.conversationId)
                _state.update { current -> current.copy(messages = current.messages.filterNot { it.id == message.id }) }
            } catch (error: Exception) {
                setError(error)
            } finally {
                _state.update { current ->
                    if (current.mutatingMessageId == message.id) current.copy(mutatingMessageId = null) else current
                }
            }
        }
    }

    fun addFriend(username: String) {
        val normalizedUsername = normalizeUsername(username)
        if (!USERNAME_PATTERN.matches(normalizedUsername)) {
            _state.update {
                it.copy(error = "Enter a username with 3-20 letters, numbers, dots or underscores.", notice = null)
            }
            return
        }
        performBusyAction {
            val session = currentSession()
            repository.sendFriendRequest(session, normalizedUsername)
            _state.update { it.copy(busy = false, notice = "Friend request sent to @$normalizedUsername.", error = null) }
        }
    }

    fun respondToRequest(request: ChatFriendRequest, accept: Boolean) {
        val preferredId = if (accept) request.senderId else _state.value.selectedFriendId
        performBusyAction {
            val session = currentSession()
            repository.respondToFriendRequest(session, request.id, accept)
            val profile = _state.value.profile ?: repository.getOwnProfile(session)
                ?: throw ChatApiException("Your chat profile could not be loaded.")
            loadDashboard(session, profile, preferredId)
            _state.update {
                it.copy(notice = if (accept) "You and @${request.username} are now friends." else "Friend request declined.")
            }
        }
    }

    fun refresh() {
        val current = _state.value
        val session = current.session ?: return
        val profile = current.profile ?: return
        scope.launch {
            try {
                loadDashboard(currentSession(), profile, current.selectedFriendId)
            } catch (error: Exception) {
                setError(error)
            }
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(error = null, notice = null) }
    }

    private fun performBusyAction(action: suspend () -> Unit) {
        scope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            try {
                action()
            } catch (error: Exception) {
                setError(error)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun loadDashboard(
        session: ChatAuthSession,
        profile: ChatProfile,
        preferredFriendId: String?,
        reinstallRealtime: Boolean = true,
        scheduleTokenRefresh: Boolean = true
    ) {
        val dashboard = repository.loadDashboard(session)
        val selected = dashboard.friends.firstOrNull { it.userId == preferredFriendId }
            ?: dashboard.friends.firstOrNull()
        val messages = selected?.let { repository.loadMessages(session, it.conversationId) }.orEmpty()
        _state.update {
            it.copy(
                busy = false,
                session = session,
                profile = profile,
                needsProfile = false,
                friends = dashboard.friends,
                incomingRequests = dashboard.incomingRequests,
                unreadNotifications = dashboard.unreadNotifications,
                selectedFriendId = selected?.userId,
                messages = messages,
                error = null
            )
        }
        if (scheduleTokenRefresh) scheduleSessionRefresh(session)
        if (reinstallRealtime) installRealtime(session, selected?.conversationId)
        if (screenVisible && _state.value.panel == ChatPanel.CHATS && selected != null) {
            markConversationRead(session, selected.conversationId)
        }
    }

    private fun startVisiblePolling() {
        if (livePollJob?.isActive == true) return
        livePollJob = scope.launch {
            var lastDashboardPoll = System.currentTimeMillis()
            while (screenVisible) {
                delay(LIVE_MESSAGES_POLL_MS)
                if (!screenVisible) break
                val current = _state.value
                val profile = current.profile ?: continue
                if (current.session == null || current.busy || current.sendingMessage || current.mutatingMessageId != null) continue
                if (refreshJob?.isActive == true) continue

                try {
                    val session = currentSession()
                    val selectedFriend = current.friends.firstOrNull { it.userId == current.selectedFriendId }
                    if (current.panel == ChatPanel.CHATS && selectedFriend != null) {
                        val latestMessages = repository.loadMessages(session, selectedFriend.conversationId)
                        _state.update { state ->
                            if (state.panel == ChatPanel.CHATS && state.selectedFriendId == selectedFriend.userId && state.messages != latestMessages) {
                                state.copy(messages = latestMessages)
                            } else state
                        }
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastDashboardPoll >= LIVE_DASHBOARD_POLL_MS) {
                        lastDashboardPoll = now
                        val latest = _state.value
                        if (latest.profile != null && !latest.busy) {
                            loadDashboard(
                                session = session,
                                profile = latest.profile ?: profile,
                                preferredFriendId = latest.selectedFriendId,
                                reinstallRealtime = false,
                                scheduleTokenRefresh = false
                            )
                        }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep the last good conversation on transient REST/Realtime outages; the next poll retries.
                }
            }
        }
    }

    private fun installRealtime(session: ChatAuthSession, conversationId: String?) {
        realtime?.close()
        realtime = runCatching {
            repository.subscribe(session, conversationId) { change ->
                if (change.table == "chat_notifications" && change.eventType.equals("INSERT", ignoreCase = true)) {
                    handleIncomingNotification(session, change.record)
                }
                scheduleRealtimeRefresh()
            }
        }.onFailure {
            // A Realtime handshake failure must not invalidate the saved account session or disable REST actions.
            _state.update { state ->
                state.copy(
                    error = null,
                    notice = "Live updates are reconnecting. Chat still works; use Refresh to reload."
                )
            }
        }.getOrNull()
    }

    private fun handleIncomingNotification(session: ChatAuthSession, record: JsonObject) {
        val recipientId = record.stringOrEmpty("recipient_id")
        if (recipientId != session.userId) return
        val senderId = record.stringOrEmpty("sender_id")
        val conversationId = record.stringOrEmpty("conversation_id")
        if (senderId.isBlank() || conversationId.isBlank() || senderId == session.userId) return
        val preview = record.stringOrEmpty("message_preview")

        scope.launch {
            val current = _state.value
            val alreadyViewingConversation = screenVisible &&
                current.panel == ChatPanel.CHATS &&
                current.selectedFriendId == senderId
            if (alreadyViewingConversation) {
                runCatching { repository.markConversationNotificationsRead(session, conversationId) }
                _state.update { state ->
                    state.copy(unreadNotifications = state.unreadNotifications.filterNot { it.conversationId == conversationId })
                }
            } else {
                val friend = current.friends.firstOrNull { it.userId == senderId }
                ChatNotificationHelper.showIncomingMessage(
                    context = applicationContext,
                    conversationId = conversationId,
                    senderName = friend?.displayName ?: "New chat message",
                    preview = preview
                )
            }
        }
    }

    private fun scheduleRealtimeRefresh() {
        scope.launch {
            if (refreshJob?.isActive == true) return@launch
            refreshJob = launch {
                delay(250)
                val current = _state.value
                val profile = current.profile ?: return@launch
                runCatching {
                    loadDashboard(currentSession(), profile, current.selectedFriendId, reinstallRealtime = false)
                }.onFailure(::setError)
            }
        }
    }

    private fun markConversationRead(friend: ChatFriend) {
        scope.launch {
            runCatching {
                val session = currentSession()
                markConversationRead(session, friend.conversationId)
            }.onFailure(::setError)
        }
    }

    private fun markConversationRead(session: ChatAuthSession, conversationId: String) {
        scope.launch {
            runCatching { repository.markConversationNotificationsRead(session, conversationId) }
                .onFailure(::setError)
            _state.update { current ->
                current.copy(unreadNotifications = current.unreadNotifications.filterNot { it.conversationId == conversationId })
            }
        }
    }

    private fun scheduleSessionRefresh(session: ChatAuthSession) {
        sessionRefreshJob?.cancel()
        val delayMillis = (session.expiresAtMillis - System.currentTimeMillis() - SESSION_REFRESH_MARGIN_MS)
            .coerceAtLeast(1_000L)
        sessionRefreshJob = scope.launch {
            delay(delayMillis)
            try {
                val current = _state.value.session ?: return@launch
                val fresh = repository.ensureFreshSession(current)
                sessionStore.save(fresh)
                val profile = _state.value.profile
                sessionRefreshJob = null
                if (profile != null) loadDashboard(fresh, profile, _state.value.selectedFriendId)
                else scheduleSessionRefresh(fresh)
            } catch (error: Exception) {
                setError(error)
            }
        }
    }

    private suspend fun currentSession(): ChatAuthSession {
        val existing = _state.value.session ?: throw ChatApiException("Select a launcher account to use chat.")
        val fresh = try {
            repository.ensureFreshSession(existing)
        } catch (refreshError: Exception) {
            val account = launcherAccount
            val identity = account?.let { runCatching { ChatLauncherIdentity.from(it) }.getOrNull() }
            if (identity == null || existing.launcherAccountId != identity.localAccountId) {
                throw refreshError
            }
            repository.createLauncherSession(identity)
        }
        if (fresh != existing) {
            sessionStore.save(fresh)
            _state.update { it.copy(session = fresh) }
            scheduleSessionRefresh(fresh)
        }
        return fresh
    }

    private fun selectedFriend(): ChatFriend? {
        val selectedId = _state.value.selectedFriendId ?: return null
        return _state.value.friends.firstOrNull { it.userId == selectedId }
    }

    private fun setError(error: Throwable) {
        _state.update {
            it.copy(
                busy = false,
                error = error.message?.takeIf(String::isNotBlank) ?: "Chat request failed. Please try again.",
                notice = null
            )
        }
    }

    private fun normalizeUsername(username: String): String = username.trim().removePrefix("@").lowercase()

    private fun generateRecoveryCode(): String {
        val bytes = ByteArray(24)
        SECURE_RANDOM.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    override fun close() {
        realtime?.close()
        realtime = null
        livePollJob?.cancel()
        livePollJob = null
        sessionRefreshJob?.cancel()
        sessionRefreshJob = null
        scope.cancel()
    }

    private fun List<ChatMessage>.upsertMessage(message: ChatMessage): List<ChatMessage> =
        if (any { it.id == message.id }) this else (this + message).takeLast(MAX_VISIBLE_MESSAGES)

    private fun JsonObject.stringOrEmpty(name: String): String =
        get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private companion object {
        const val MAX_MESSAGE_LENGTH = 2000
        const val MAX_VISIBLE_MESSAGES = 100
        const val SESSION_REFRESH_MARGIN_MS = 60_000L
        const val LIVE_MESSAGES_POLL_MS = 2_000L
        const val LIVE_DASHBOARD_POLL_MS = 12_000L
        const val MAX_DISPLAY_NAME_LENGTH = 32
        const val MAX_BIO_LENGTH = 160
        val USERNAME_PATTERN = Regex("^[a-z0-9_.]{3,20}$")
        val RECOVERY_CODE_PATTERN = Regex("^[A-Za-z0-9_-]+$")
        val SECURE_RANDOM = SecureRandom()
    }
}
