/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery and contributors
 * Licensed under the GNU GPL v3 or later.
 */

package com.movtery.zalithlauncher.chat

import android.content.Context
import com.movtery.zalithlauncher.game.account.Account
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * Foreground-only sync for the Chat tab badge and the selected launcher's last-active heartbeat.
 * It deliberately does not provision a chat account: users first create/select their chat profile
 * by opening Chat, and this controller only uses an already-saved session.
 */
internal class ChatForegroundController(
    context: Context,
    private val launcherAccount: Account?
) : Closeable {
    private val repository = SupabaseChatRepository()
    private val sessionStore = ChatSessionStore(context.applicationContext, launcherAccount?.uniqueUUID)
    private val _unreadPeopleCount = kotlinx.coroutines.flow.MutableStateFlow(0)
    val unreadPeopleCount = _unreadPeopleCount.asStateFlow()

    @Volatile private var closed = false
    private var cachedSession: ChatAuthSession? = null
    private var lastPresenceUpdateMillis = 0L

    suspend fun runForegroundLoop() {
        while (!closed && currentCoroutineContext().isActive) {
            try {
                syncOnce()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the last count visible and retry on the next foreground tick.
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun syncOnce() {
        val account = launcherAccount ?: run {
            _unreadPeopleCount.value = 0
            return
        }
        val saved = cachedSession ?: withContext(Dispatchers.IO) { sessionStore.load() }?.also { cachedSession = it } ?: return
        val session = try {
            repository.ensureFreshSession(saved)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (refreshError: Exception) {
            val identity = ChatLauncherIdentity.from(account)
            if (saved.launcherAccountId == identity.localAccountId) {
                repository.createLauncherSession(identity)
            } else {
                throw refreshError
            }
        }
        if (session != saved) withContext(Dispatchers.IO) { sessionStore.save(session) }
        cachedSession = session

        val now = System.currentTimeMillis()
        if (now - lastPresenceUpdateMillis >= PRESENCE_INTERVAL_MS) {
            lastPresenceUpdateMillis = now
            try {
                repository.touchChatPresence(session)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The unread badge can still sync if the optional presence RPC is temporarily unavailable.
            }
        }
        _unreadPeopleCount.value = repository.unreadSenderCount(session)
    }

    override fun close() {
        closed = true
    }

    private companion object {
        const val POLL_INTERVAL_MS = 4_000L
        const val PRESENCE_INTERVAL_MS = 25_000L
    }
}
