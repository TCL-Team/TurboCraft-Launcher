/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery and contributors
 * Licensed under the GNU GPL v3 or later.
 */

package com.movtery.zalithlauncher.chat

import kotlinx.coroutines.CancellationException
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

internal data class PartyUiState(
    val busy: Boolean = false,
    val sending: Boolean = false,
    val parties: List<SocialParty> = emptyList(),
    val publicParties: List<SocialParty> = emptyList(),
    val invites: List<PartyInvite> = emptyList(),
    val sentInvites: List<PartyInvite> = emptyList(),
    val activePartyId: String? = null,
    val messages: List<PartyMessage> = emptyList(),
    val loadingMessages: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val lastRefreshMillis: Long = 0L
) {
    val activeParty: SocialParty?
        get() = activePartyId?.let { id -> (parties + publicParties).firstOrNull { it.id == id } }

    val inviteCount: Int get() = invites.size

    val incomingPartyId: String? get() = parties.firstOrNull()?.id

    fun isHost(userId: String?): Boolean = activeParty?.isHost(userId) ?: false
}

/**
 * Owns the parties side of the social hub: live party list, party chat, invites and
 * host controls. The DM side stays in [ChatController]; both share the same
 * Supabase session that the chat identity handshake produced.
 */
internal class SocialPartiesController(
    private val session: ChatAuthSession,
    private val repository: SupabaseChatRepository = SupabaseChatRepository()
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PartyUiState())
    val state = _state.asStateFlow()

    private var dashboardJob: Job? = null
    private var messageJob: Job? = null
    private var heartbeatJob: Job? = null
    private var started = false

    @Volatile private var screenVisible = false

    val selfUserId: String get() = session.userId

    fun start() {
        if (started) return
        started = true
        refresh()
        scope.launch {
            while (true) {
                delay(PRESENCE_HEARTBEAT_MS)
                runCatching { repository.touchChatPresence(session) }
            }
        }
    }

    fun setScreenVisible(visible: Boolean) {
        val wasVisible = screenVisible
        screenVisible = visible
        if (visible && !wasVisible) {
            startVisiblePolling()
            refresh()
        }
        if (!visible) {
            dashboardJob?.cancel()
            dashboardJob = null
        }
    }

    fun refresh() {
        if (!started) return
        if (dashboardJob?.isActive == true) return
        dashboardJob = scope.launch {
            try {
                val dashboard = repository.loadPartyDashboard(session)
                _state.update { current ->
                    val activeId = current.activePartyId
                        ?.takeIf { id -> dashboard.parties.any { it.id == id } }
                        ?: dashboard.parties.firstOrNull()?.id
                    current.copy(
                        busy = false,
                        parties = dashboard.parties,
                        publicParties = dashboard.publicParties,
                        invites = dashboard.invites,
                        sentInvites = dashboard.sentInvites,
                        activePartyId = activeId,
                        error = null,
                        lastRefreshMillis = System.currentTimeMillis()
                    )
                }
                val active = _state.value.activePartyId
                if (active != null && !screenVisible) loadMessages(active, silent = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(busy = false, error = error.message ?: "Could not load parties.") }
            }
        }
    }

    fun openParty(partyId: String) {
        _state.update { it.copy(activePartyId = partyId, messages = emptyList()) }
        loadMessages(partyId)
    }

    fun closeParty() {
        messageJob?.cancel()
        messageJob = null
        _state.update { it.copy(activePartyId = null, messages = emptyList()) }
    }

    fun loadMessages(partyId: String = _state.value.activePartyId.orEmpty(), silent: Boolean = false) {
        if (partyId.isBlank()) return
        if (!silent) _state.update { it.copy(loadingMessages = true) }
        scope.launch {
            try {
                val messages = repository.loadPartyMessages(session, partyId)
                _state.update { current ->
                    if (current.activePartyId != partyId) current
                    else current.copy(messages = messages, loadingMessages = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loadingMessages = false,
                        error = if (silent) it.error else error.message ?: "Could not load party chat."
                    )
                }
            }
        }
    }

    fun createParty(draft: PartyDraft, onCreated: (SocialParty) -> Unit = {}) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, notice = null) }
        scope.launch {
            try {
                val partyId = repository.createParty(session, draft)
                val dashboard = repository.loadPartyDashboard(session)
                val created = dashboard.parties.firstOrNull { it.id == partyId }
                _state.update { current ->
                    current.copy(
                        busy = false,
                        parties = dashboard.parties,
                        publicParties = dashboard.publicParties,
                        invites = dashboard.invites,
                        sentInvites = dashboard.sentInvites,
                        activePartyId = partyId,
                        messages = emptyList(),
                        notice = "Party created. Share the code with your friends."
                    )
                }
                if (created != null) onCreated(created)
                startVisiblePolling()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(busy = false, error = error.message ?: "Could not create the party.") }
            }
        }
    }

    fun joinByCode(code: String, onJoined: (String) -> Unit = {}) {
        if (code.isBlank()) return
        _state.update { it.copy(busy = true, error = null, notice = null) }
        scope.launch {
            try {
                val partyId = repository.joinPartyByCode(session, code)
                _state.update { it.copy(busy = false, activePartyId = partyId, notice = "You joined the party.") }
                refresh()
                loadMessages(partyId)
                onJoined(partyId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(busy = false, error = error.message ?: "Could not join that party.") }
            }
        }
    }

    fun joinParty(partyId: String) {
        _state.update { it.copy(busy = true, error = null, notice = null) }
        scope.launch {
            try {
                repository.joinParty(session, partyId)
                _state.update { it.copy(busy = false, activePartyId = partyId, notice = "You joined the party.") }
                refresh()
                loadMessages(partyId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(busy = false, error = error.message ?: "Could not join that party.") }
            }
        }
    }

    fun leaveParty(partyId: String = _state.value.activePartyId.orEmpty()) {
        if (partyId.isBlank()) return
        scope.launch {
            try {
                repository.leaveParty(session, partyId)
                _state.update {
                    it.copy(
                        activePartyId = null,
                        messages = emptyList(),
                        notice = "You left the party."
                    )
                }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "Could not leave the party.") }
            }
        }
    }

    fun endParty(partyId: String = _state.value.activePartyId.orEmpty()) {
        if (partyId.isBlank()) return
        scope.launch {
            try {
                repository.endParty(session, partyId)
                _state.update {
                    it.copy(activePartyId = null, messages = emptyList(), notice = "Party ended.")
                }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "Could not end the party.") }
            }
        }
    }

    fun extendParty(partyId: String = _state.value.activePartyId.orEmpty()) {
        if (partyId.isBlank()) return
        scope.launch {
            try {
                repository.extendParty(session, partyId)
                _state.update { it.copy(notice = "Party extended by 1 hour.") }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "Could not extend the party.") }
            }
        }
    }

    fun invite(username: String) {
        val partyId = _state.value.activePartyId ?: return
        if (username.isBlank()) return
        scope.launch {
            try {
                repository.inviteToParty(session, partyId, username)
                _state.update { it.copy(notice = "@${username.removePrefix("@")} invited to the party.") }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "Could not send the invite.") }
            }
        }
    }

    fun respondToInvite(invite: PartyInvite, accept: Boolean) {
        scope.launch {
            try {
                repository.respondToPartyInvite(session, invite.id, accept)
                if (accept) {
                    _state.update { it.copy(activePartyId = invite.partyId) }
                    loadMessages(invite.partyId)
                }
                _state.update {
                    it.copy(notice = if (accept) "You joined ${invite.partyTitle}." else "Invite declined.")
                }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "Could not answer the invite.") }
            }
        }
    }

    fun kickMember(userId: String) {
        val partyId = _state.value.activePartyId ?: return
        scope.launch {
            try {
                repository.kickPartyMember(session, partyId, userId)
                _state.update { it.copy(notice = "Member removed.") }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "Could not remove the member.") }
            }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        val partyId = _state.value.activePartyId ?: return
        if (trimmed.isEmpty() || _state.value.sending) return
        val optimistic = PartyMessage(
            id = "pending-${System.currentTimeMillis()}",
            partyId = partyId,
            senderId = session.userId,
            senderName = "You",
            senderUsername = "",
            body = trimmed,
            createdAtMillis = System.currentTimeMillis(),
            pending = true
        )
        _state.update { it.copy(sending = true, messages = it.messages + optimistic) }
        scope.launch {
            try {
                val saved = repository.sendPartyMessage(session, partyId, trimmed)
                _state.update { current ->
                    current.copy(
                        sending = false,
                        messages = current.messages.map { if (it.id == optimistic.id) saved else it }
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { current ->
                    current.copy(
                        sending = false,
                        messages = current.messages.filterNot { it.id == optimistic.id },
                        error = error.message ?: "Message was not sent."
                    )
                }
            }
        }
    }

    fun deleteMessage(message: PartyMessage) {
        if (message.pending) return
        scope.launch {
            try {
                repository.deletePartyMessage(session, message.id)
                _state.update { current -> current.copy(messages = current.messages.filterNot { it.id == message.id }) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "Could not delete the message.") }
            }
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(error = null, notice = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun startVisiblePolling() {
        if (messageJob?.isActive == true) return
        messageJob = scope.launch {
            var lastDashboardPoll = System.currentTimeMillis()
            while (screenVisible) {
                delay(PARTY_POLL_MS)
                if (!screenVisible) break
                val current = _state.value
                val activeId = current.activePartyId
                if (current.busy || current.sending) continue
                if (activeId != null) {
                    runCatching { repository.loadPartyMessages(session, activeId) }
                        .onSuccess { latest ->
                            _state.update { state ->
                                if (state.activePartyId == activeId && !state.sending) state.copy(messages = latest)
                                else state
                            }
                        }
                }
                val now = System.currentTimeMillis()
                if (now - lastDashboardPoll >= PARTY_DASHBOARD_POLL_MS) {
                    lastDashboardPoll = now
                    runCatching { repository.loadPartyDashboard(session) }.onSuccess { dashboard ->
                        _state.update { state ->
                            state.copy(
                                parties = dashboard.parties,
                                publicParties = dashboard.publicParties,
                                invites = dashboard.invites,
                                sentInvites = dashboard.sentInvites,
                                lastRefreshMillis = now
                            )
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        screenVisible = false
        dashboardJob?.cancel()
        messageJob?.cancel()
        heartbeatJob?.cancel()
        scope.cancel()
    }

    private companion object {
        const val PARTY_POLL_MS = 5_000L
        const val PARTY_DASHBOARD_POLL_MS = 15_000L
        const val PRESENCE_HEARTBEAT_MS = 60_000L
    }
}
